package com.billcapture.app

/**
 * Extracts the A–H bill fields from raw OCR text:
 *   A company name, B company/registration no, C address, D contact (phone or
 *   email), E bill date, F bill no, G category + description, H total amount.
 *
 * Receipts are noisy, so every extractor is best-effort; blank means "not
 * recognised" and the user fixes it on the review screen.
 */
object ReceiptParser {

    data class Fields(
        val companyName: String,
        val companyNo: String,
        val address: String,
        val contact: String,
        val billDate: String,
        val billNo: String,
        val category: String,
        val description: String,
        val amount: String
    )

    val CATEGORIES = listOf(
        "Food", "Groceries", "Hardware", "Utilities", "Telecom", "Fuel",
        "Pharmacy", "Office", "Electronics", "Clothing", "Transport", "Other"
    )

    // Company/business registration numbers: old Malaysian ROC "1199959-D",
    // new 12-digit SSM "202201234567", or a labelled "Reg No: ..." value.
    private val COMPANY_NO = Regex("""\(?\b(\d{6,7}-[A-Z])\b\)?|\b(\d{12})\b""")
    private val COMPANY_NO_LABELED =
        Regex("""(?i)(?:co|comp|company|reg|regn|roc|ssm|gst|sst)\.?\s*(?:no|num|id)\.?\s*[:#]?\s*([A-Z0-9][A-Z0-9\-/]{4,20})""")

    private val PHONE =
        Regex("""(?:\+?6?0\d{1,2}[-\s]\d{3,4}[-\s]?\d{3,4})|(?:\+?60\d{8,9})""")
    private val EMAIL = Regex("""[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}""")

    private val DATE =
        Regex("""\b(\d{1,2}[/\-.]\d{1,2}[/\-.]\d{2,4}|\d{4}-\d{2}-\d{2})\b""")
    private val TIME = Regex("""\b\d{1,2}:\d{2}(?::\d{2})?\s*(?:[AaPp][Mm])?""")

    // Lines that mark the end of the header block (name/reg/address/contact)
    private val HEADER_STOP = Regex(
        """(?i)^(tax\s+invoice|invoice|receipt|cash\s+bill|bill\s*(no|to)|estimate|quotation|""" +
            """current\s+invoice|r\.?\s*no|date\b|time\b|cashier|table|qty|item|description|""" +
            """pos\b|order|customer|welcome|thank|\*+|=+|-{4,})"""
    )

    fun parse(text: String): Fields {
        val header = headerLines(text)
        val companyNo = extractCompanyNo(text)
        val contact = extractContact(text)
        val companyName = extractCompanyName(header, companyNo)
        val category = detectCategory(text) ?: ""
        return Fields(
            companyName = companyName,
            companyNo = companyNo,
            address = extractAddress(header, companyName, companyNo),
            contact = contact,
            billDate = extractBillDate(text) ?: "",
            billNo = extractBillNo(text) ?: "",
            category = category,
            description = extractItems(text).joinToString(", ").take(400),
            amount = extractAmount(text) ?: ""
        )
    }

    /**
     * The header is the top of the bill: company name, registration no,
     * address and contact lines, before the document body (invoice/date/item
     * lines) starts.
     */
    private fun headerLines(text: String): List<String> {
        val lines = mutableListOf<String>()
        for (line in text.lines().map { it.trim() }) {
            if (line.isEmpty()) continue
            if (HEADER_STOP.containsMatchIn(line) || DATE.containsMatchIn(line)) break
            lines.add(line)
            if (lines.size == 6) break
        }
        return lines
    }

    /** A. The company name is the first header line, without the reg number. */
    private fun extractCompanyName(header: List<String>, companyNo: String): String {
        val first = header.firstOrNull { it.count { c -> c.isLetter() } >= 3 } ?: return ""
        return first
            .replace(companyNo, "")
            .replace(Regex("""\(\s*\)"""), "")
            .trim(' ', ',', '-', '(', ')')
    }

    /** B. Company / business registration number, e.g. "(1199959-D)". */
    fun extractCompanyNo(text: String): String {
        val head = text.lines().take(8).joinToString("\n")
        COMPANY_NO.find(head)?.let { m ->
            return m.groupValues.drop(1).firstOrNull { it.isNotEmpty() } ?: ""
        }
        return COMPANY_NO_LABELED.find(text)?.groupValues?.get(1) ?: ""
    }

    /** C. Address: the header lines that aren't the name, reg no, or contact. */
    private fun extractAddress(
        header: List<String>,
        companyName: String,
        companyNo: String
    ): String {
        return header
            .map { line ->
                line.replace(PHONE, "")
                    .replace(EMAIL, "")
                    .replace(companyNo, "")
                    .replace(Regex("""\(\s*\)"""), "")
                    .trim(' ', ',', '-', ':')
            }
            .filter { line ->
                line.count { it.isLetterOrDigit() } >= 4 &&
                    !line.contains(companyName.take(12), ignoreCase = true) &&
                    !Regex("(?i)^(tel|phone|fax|h/?p|email|e-mail|contact)").containsMatchIn(line)
            }
            .joinToString(", ")
            .take(200)
    }

    /** D. Contact: a phone number or email address anywhere near the top. */
    fun extractContact(text: String): String {
        val head = text.lines().take(10).joinToString("\n")
        val phone = PHONE.find(head)?.value ?: PHONE.find(text)?.value
        val email = EMAIL.find(text)?.value
        return listOfNotNull(phone, email).joinToString(" / ")
    }

    /** E. Bill date, with the printed time when it sits on the same line. */
    fun extractBillDate(text: String): String? {
        val match = DATE.find(text) ?: return null
        val line = text.lines().firstOrNull { it.contains(match.value) } ?: return match.value
        val time = TIME.find(line.substringAfter(match.value))?.value?.trim()
        return if (time != null) "${match.value} $time" else match.value
    }

    /**
     * F. The bill/invoice number sits after labels like "Invoice No", "R.No",
     * "Receipt #", "Doc No" — possibly on the next line. The captured token
     * must contain a digit, so label words ("No", "Date") are never mistaken
     * for the number itself.
     */
    fun extractBillNo(text: String): String? {
        val number = """((?=[A-Za-z0-9\-/]*\d)[A-Za-z0-9][A-Za-z0-9\-/]{1,24})"""
        val labeled = listOf(
            """(?:tax\s+)?invoice\s*(?:no|number|num|id)?""",
            """cash\s+bill\s*(?:no|number|num)?""",
            """bill\s*(?:no|number|num)""",
            """receipt\s*(?:no|number|num)?""",
            """r\.?\s*no""",
            """\binv\s*(?:no|num)?""",
            """(?:doc|document|ref|reference)\s*(?:no|number|num)?"""
        )
        for (label in labeled) {
            val match = Regex("""(?i)\b$label\s*[.:#\-]?\s*#?\s*$number""")
                .find(text)?.groupValues?.get(1)
            if (match != null) return match
        }
        // Fallbacks: "No. 12345" style, or a POS-prefixed token
        return Regex("""(?i)\bno\s*[.:#]\s*$number""").find(text)?.groupValues?.get(1)
            ?: Regex("""\b(POS\d{4,})\b""").find(text)?.groupValues?.get(1)
    }

    /**
     * G. Item lines on a receipt are text followed by a price at the end of
     * the line, optionally with a quantity in front ("2 x Kopi O  4.00").
     * Summary lines (total, tax, cash, change...) are excluded.
     */
    private fun extractItems(text: String): List<String> {
        val itemLine = Regex(
            """^(.*?\S)\s+(?:RM|\$|MYR)?\s*(\d[\d,]*[.,]\d{2})\s*$"""
        )
        val exclude = Regex(
            """(?i)\b(sub\s*-?total|total|tax|gst|sst|vat|cash|change|rounding|discount|""" +
                """balance|tender|visa|master|credit|debit|amount|due|paid|payment|""" +
                """service\s+charge|deposit|point|member)\b"""
        )
        val qty = Regex("""^(\d{1,3})\s*[xX*]?\s+""")
        val itemCode = Regex("""^[A-Z0-9\-#]{4,}\s+""")

        return text.lines()
            .map { it.trim() }
            .mapNotNull { line ->
                if (exclude.containsMatchIn(line)) return@mapNotNull null
                val match = itemLine.find(line) ?: return@mapNotNull null
                val (rawName, price) = match.destructured
                val quantity = qty.find(rawName)?.groupValues?.get(1)
                val cleaned = rawName
                    .replace(qty, "")
                    .replace(itemCode, "")
                    .trim(' ', '-', '.', ':')
                // A real item name has letters (Latin or CJK), not just codes
                if (cleaned.count { it.isLetter() } < 2) return@mapNotNull null
                buildString {
                    if (quantity != null && quantity != "1") append(quantity).append("x ")
                    append(cleaned).append(' ').append(price)
                }
            }
            .distinct()
            .take(10)
    }

    fun detectCategory(text: String): String? {
        val categories = listOf(
            "Food" to listOf(
                "restaurant", "cafe", "kopitiam", "bakery", "food", "menu", "dine",
                "burger", "pizza", "coffee", "tea", "rice", "noodle", "chicken", "beverage"
            ),
            "Groceries" to listOf(
                "grocer", "supermarket", "mart", "hypermarket", "provision", "fresh market"
            ),
            "Hardware" to listOf(
                "hardware", "tools", "cement", "paint", "timber", "plumbing",
                "electrical supplies", "nails", "screws", "drill"
            ),
            "Utilities" to listOf(
                "electricity", "water bill", "utility", "tenaga", "syabas", "indah water",
                "sewerage", "gas bill"
            ),
            "Telecom" to listOf(
                "telco", "mobile", "broadband", "internet", "prepaid", "postpaid",
                "unifi", "maxis", "celcom", "digi"
            ),
            "Fuel" to listOf("petrol", "diesel", "fuel", "petronas", "shell", "caltex"),
            "Pharmacy" to listOf("pharmacy", "clinic", "medical", "hospital", "guardian", "watsons"),
            "Office" to listOf("stationery", "office suppl", "printing", "photocopy", "toner"),
            "Electronics" to listOf("electronic", "computer", "laptop", "phone shop", "gadget"),
            "Clothing" to listOf("fashion", "apparel", "clothing", "boutique", "textile"),
            "Transport" to listOf("taxi", "grab", "toll", "parking", "transport", "logistics")
        )
        val lower = text.lowercase()
        return categories
            .map { (name, keywords) -> name to keywords.count { lower.contains(it) } }
            .filter { it.second > 0 }
            .maxByOrNull { it.second }
            ?.first
    }

    /** H. Total amount: prefer a line labelled total/amount due, not subtotal. */
    fun extractAmount(text: String): String? {
        val labeled = Regex(
            """(?i)(?:grand\s+total|(?<!sub)(?<!sub[ \-])total|amount\s+due|balance\s+due)\D{0,10}([0-9][0-9,]*\.?\d{0,2})"""
        ).findAll(text).lastOrNull()?.groupValues?.get(1)
        val amount = labeled ?: Regex("""\b\d{1,3}(?:,\d{3})*\.\d{2}\b""")
            .findAll(text)
            .map { it.value }
            .maxByOrNull { it.replace(",", "").toDoubleOrNull() ?: 0.0 }
        return amount?.replace(",", "")
    }
}

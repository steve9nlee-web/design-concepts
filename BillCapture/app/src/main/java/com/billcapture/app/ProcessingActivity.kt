package com.billcapture.app

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.auth.oauth2.GoogleCredentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class ProcessingActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PHOTO_PATH = "photo_path"
        private const val VISION_URL = "https://vision.googleapis.com/v1/images:annotate"
        private const val CREDENTIALS_ASSET = "google_credentials.json"
    }

    private lateinit var progressBar: ProgressBar
    private lateinit var textResults: TextView
    private lateinit var editDate: EditText
    private lateinit var editBillNo: EditText
    private lateinit var editCompany: EditText
    private lateinit var editDescription: EditText
    private lateinit var editAmount: EditText
    private lateinit var btnSave: Button

    private var photoPath: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_processing)

        progressBar = findViewById(R.id.progressBar)
        textResults = findViewById(R.id.textViewResults)
        editDate = findViewById(R.id.editDate)
        editBillNo = findViewById(R.id.editBillNo)
        editCompany = findViewById(R.id.editCompany)
        editDescription = findViewById(R.id.editDescription)
        editAmount = findViewById(R.id.editAmount)
        btnSave = findViewById(R.id.btn_save_to_excel)

        photoPath = intent.getStringExtra(EXTRA_PHOTO_PATH)

        findViewById<Button>(R.id.btn_retake_photo).setOnClickListener {
            startActivity(android.content.Intent(this, CameraActivity::class.java))
            finish()
        }
        btnSave.setOnClickListener { saveBill() }

        runOcr()
    }

    private fun runOcr() {
        val path = photoPath
        if (path == null || !File(path).exists()) {
            textResults.text = "No photo found — fill the fields manually."
            return
        }

        progressBar.visibility = View.VISIBLE
        textResults.text = getString(R.string.processing)
        btnSave.isEnabled = false

        lifecycleScope.launch {
            try {
                val text = withContext(Dispatchers.IO) { detectText(path) }
                if (text.isBlank()) {
                    textResults.text = "No text detected. Edit the fields manually or retake."
                } else {
                    fillFieldsFrom(text)
                    textResults.text = "Extracted — review and edit if needed."
                }
            } catch (e: Exception) {
                textResults.text = "OCR failed: ${e.message}\nFill the fields manually."
            } finally {
                progressBar.visibility = View.GONE
                btnSave.isEnabled = true
            }
        }
    }

    /** Calls Cloud Vision TEXT_DETECTION using the service-account JSON in assets. */
    private fun detectText(path: String): String {
        val credentials = assets.open(CREDENTIALS_ASSET).use {
            GoogleCredentials.fromStream(it)
                .createScoped(listOf("https://www.googleapis.com/auth/cloud-platform"))
        }
        credentials.refreshIfExpired()
        val token = credentials.accessToken.tokenValue

        val bitmap = BitmapUtil.loadScaled(path)
        val request = JSONObject().put(
            "requests", JSONArray().put(
                JSONObject()
                    .put("image", JSONObject().put("content", BitmapUtil.toBase64Jpeg(bitmap)))
                    .put(
                        "features",
                        JSONArray().put(JSONObject().put("type", "TEXT_DETECTION"))
                    )
            )
        )

        val conn = URL(VISION_URL).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        conn.doOutput = true
        conn.outputStream.use { it.write(request.toString().toByteArray()) }

        if (conn.responseCode !in 200..299) {
            val error = conn.errorStream?.bufferedReader()?.readText() ?: ""
            throw IllegalStateException("Vision API ${conn.responseCode}: ${error.take(200)}")
        }

        val body = conn.inputStream.bufferedReader().readText()
        val response = JSONObject(body).getJSONArray("responses").getJSONObject(0)
        return response.optJSONObject("fullTextAnnotation")?.optString("text") ?: ""
    }

    // ---- field extraction ---------------------------------------------------

    private fun fillFieldsFrom(text: String) {
        extractDate(text)?.let { editDate.setText(it) }
        extractBillNo(text)?.let { editBillNo.setText(it) }
        editCompany.setText(extractCompanyBlock(text))
        editDescription.setText(buildDetailedDescription(text))
        extractAmount(text)?.let { editAmount.setText(it) }
    }

    /**
     * The company block is the header of the bill: name, registration no,
     * address, contact details. Collect the top lines until the document
     * body starts (invoice/date/item lines), joined with commas.
     */
    private fun extractCompanyBlock(text: String): String {
        val stop = Regex(
            """(?i)^(tax\s+invoice|invoice|receipt|cash\s+bill|bill\s*(no|to)|estimate|quotation|""" +
                """date\b|time\b|cashier|table|qty|item|description|pos\b|order|customer|""" +
                """welcome|thank)"""
        )
        val dateLine = Regex("""\d{1,2}[/\-.]\d{1,2}[/\-.]\d{2,4}|\d{4}-\d{2}-\d{2}""")

        val block = mutableListOf<String>()
        for (line in text.lines().map { it.trim() }) {
            if (line.isEmpty()) continue
            if (stop.containsMatchIn(line) || dateLine.containsMatchIn(line)) break
            block.add(line)
            if (block.size == 6) break
        }
        return block.joinToString(", ")
    }

    /** "Category: item1 price, item2 price, ..." — what was bought, without company info. */
    private fun buildDetailedDescription(text: String): String {
        val category = detectCategory(text)
        val items = extractItems(text)
        return when {
            category != null && items.isNotEmpty() ->
                "$category: ${items.joinToString(", ")}"
            items.isNotEmpty() -> items.joinToString(", ")
            else -> category ?: ""
        }.take(400)
    }

    /**
     * Item lines on a receipt are text followed by a price at the end of the
     * line, optionally with a quantity in front ("2 x Kopi O  4.00").
     * Summary lines (total, tax, cash, change...) are excluded. Each item is
     * reported with its quantity and price so the Excel row keeps the detail.
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
                // A real item name has letters, not just codes/quantities
                if (cleaned.count { it.isLetter() } < 3) return@mapNotNull null
                buildString {
                    if (quantity != null && quantity != "1") append(quantity).append("x ")
                    append(cleaned).append(' ').append(price)
                }
            }
            .distinct()
            .take(10)
    }

    private fun detectCategory(text: String): String? {
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

    private fun extractDate(text: String): String? =
        Regex("""\b(\d{1,2}[/\-.]\d{1,2}[/\-.]\d{2,4}|\d{4}-\d{2}-\d{2})\b""")
            .find(text)?.value

    /**
     * The bill/invoice number sits after labels like "Invoice No", "Cash Bill
     * No", "Receipt #", "Doc No" — possibly on the next line. The captured
     * token must contain a digit, so label words ("No", "Date") are never
     * mistaken for the number itself.
     */
    private fun extractBillNo(text: String): String? {
        val number = """((?=[A-Za-z0-9\-/]*\d)[A-Za-z0-9][A-Za-z0-9\-/]{1,24})"""
        val labeled = listOf(
            """(?:tax\s+)?invoice\s*(?:no|number|num|id)?""",
            """cash\s+bill\s*(?:no|number|num)?""",
            """bill\s*(?:no|number|num)""",
            """receipt\s*(?:no|number|num)?""",
            """\binv\s*(?:no|num)?""",
            """(?:doc|document|ref|reference)\s*(?:no|number|num)?"""
        )
        for (label in labeled) {
            val match = Regex("""(?i)\b$label\s*[.:#\-]?\s*#?\s*$number""")
                .find(text)?.groupValues?.get(1)
            if (match != null) return match
        }
        // Fallback: a standalone "No. 12345" style token near the top
        return Regex("""(?i)\bno\s*[.:#]\s*$number""").find(text)?.groupValues?.get(1)
    }

    private fun extractAmount(text: String): String? {
        // Prefer an amount on a line mentioning total/amount due
        val labeled = Regex(
            """(?i)(?:grand\s+total|total|amount\s+due|balance\s+due)\D{0,10}([0-9][0-9,]*\.?\d{0,2})"""
        ).findAll(text).lastOrNull()?.groupValues?.get(1)
        val amount = labeled ?: Regex("""\b\d{1,3}(?:,\d{3})*\.\d{2}\b""")
            .findAll(text)
            .map { it.value }
            .maxByOrNull { it.replace(",", "").toDoubleOrNull() ?: 0.0 }
        return amount?.replace(",", "")
    }

    // ---- saving -------------------------------------------------------------

    private fun saveBill() {
        val entry = BillEntry(
            date = editDate.text.toString().trim(),
            billNo = editBillNo.text.toString().trim(),
            company = editCompany.text.toString().trim(),
            description = editDescription.text.toString().trim(),
            amount = editAmount.text.toString().trim()
        )
        if (entry.date.isEmpty() && entry.billNo.isEmpty() && entry.amount.isEmpty()) {
            Toast.makeText(this, "Nothing to save — fill in at least one field", Toast.LENGTH_SHORT)
                .show()
            return
        }

        btnSave.isEnabled = false
        progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            val driveError = withContext(Dispatchers.IO) {
                ExcelManager.append(this@ProcessingActivity, entry)
                val account = SessionManager.signedInAccount(this@ProcessingActivity)
                if (account != null) {
                    try {
                        DriveUploader.upload(
                            this@ProcessingActivity, account,
                            ExcelManager.masterFile(this@ProcessingActivity)
                        )
                        null
                    } catch (e: Exception) {
                        e.message ?: "Drive upload failed"
                    }
                } else null
            }

            progressBar.visibility = View.GONE
            val message = when {
                driveError != null -> "Saved locally; Drive sync failed: $driveError"
                SessionManager.isSignedIn(this@ProcessingActivity) ->
                    "Saved to Excel and synced to Google Drive"
                else -> "Saved to Excel (Downloads/${ExcelManager.FILE_NAME})"
            }
            Toast.makeText(this@ProcessingActivity, message, Toast.LENGTH_LONG).show()
            photoPath?.let { File(it).delete() }
            finish()
        }
    }
}

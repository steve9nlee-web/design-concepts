package com.subcontracker.app

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class WorkEntry(
    val name: String,
    val date: String,
    val time: String,
    val amPm: String
)

/**
 * Reads and writes SubConTracker.xlsx without any external library: an .xlsx
 * file is a zip of XML parts, and this app only needs one sheet with four
 * text columns (written as inline strings, which Excel fully supports).
 *
 * The master copy lives in app-private storage (no permissions needed); every
 * save also exports a copy to the public Downloads folder.
 */
object ExcelManager {

    const val FILE_NAME = "SubConTracker.xlsx"
    private val HEADER = listOf("Worker Name", "Date", "Time", "AM/PM")

    fun masterFile(context: Context): File = File(context.filesDir, FILE_NAME)

    /** Appends one work entry, rewrites the workbook, and exports it to Downloads. */
    fun append(context: Context, entry: WorkEntry) {
        val entries = readAll(context) + entry
        masterFile(context).outputStream().use { writeWorkbook(it, entries) }
        exportToDownloads(context, entries)
    }

    fun readAll(context: Context): List<WorkEntry> {
        val file = masterFile(context)
        if (!file.exists()) return emptyList()

        val rows = mutableListOf<List<String>>()
        ZipInputStream(file.inputStream().buffered()).use { zip ->
            var zipEntry: ZipEntry? = zip.nextEntry
            while (zipEntry != null) {
                if (zipEntry.name == "xl/worksheets/sheet1.xml") {
                    rows.addAll(parseSheet(zip))
                    break
                }
                zipEntry = zip.nextEntry
            }
        }
        return rows.drop(1) // skip header row
            .filter { it.isNotEmpty() }
            .map {
                WorkEntry(
                    name = it.getOrElse(0) { "" },
                    date = it.getOrElse(1) { "" },
                    time = it.getOrElse(2) { "" },
                    amPm = it.getOrElse(3) { "" }
                )
            }
    }

    // ---- xlsx writing -------------------------------------------------------

    private fun writeWorkbook(out: OutputStream, entries: List<WorkEntry>) {
        ZipOutputStream(out.buffered()).use { zip ->
            fun part(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }

            part(
                "[Content_Types].xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
</Types>"""
            )
            part(
                "_rels/.rels",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""
            )
            part(
                "xl/workbook.xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<sheets><sheet name="Work Log" sheetId="1" r:id="rId1"/></sheets>
</workbook>"""
            )
            part(
                "xl/_rels/workbook.xml.rels",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
</Relationships>"""
            )
            part("xl/worksheets/sheet1.xml", sheetXml(entries))
        }
    }

    private fun sheetXml(entries: List<WorkEntry>): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
        appendRow(sb, HEADER)
        entries.forEach {
            appendRow(sb, listOf(it.name, it.date, it.time, it.amPm))
        }
        sb.append("</sheetData></worksheet>")
        return sb.toString()
    }

    private fun appendRow(sb: StringBuilder, cells: List<String>) {
        sb.append("<row>")
        cells.forEach { value ->
            sb.append("""<c t="inlineStr"><is><t xml:space="preserve">""")
                .append(escapeXml(value))
                .append("</t></is></c>")
        }
        sb.append("</row>")
    }

    private fun escapeXml(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    // ---- xlsx reading -------------------------------------------------------

    private fun parseSheet(input: java.io.InputStream): List<List<String>> {
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setInput(input, "UTF-8")
        }
        val rows = mutableListOf<List<String>>()
        var currentRow: MutableList<String>? = null
        var cellValue = StringBuilder()
        var inValueTag = false

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "row" -> currentRow = mutableListOf()
                    "c" -> cellValue = StringBuilder()
                    "v", "t" -> inValueTag = true
                }
                XmlPullParser.TEXT -> if (inValueTag) cellValue.append(parser.text)
                XmlPullParser.END_TAG -> when (parser.name) {
                    "v", "t" -> inValueTag = false
                    "c" -> currentRow?.add(cellValue.toString())
                    "row" -> currentRow?.let { rows.add(it) }
                }
            }
            event = parser.next()
        }
        return rows
    }

    // ---- Downloads export ---------------------------------------------------

    private fun exportToDownloads(context: Context, entries: List<WorkEntry>) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                // Remove a previous export so we don't accumulate "(1)" copies
                resolver.delete(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    "${MediaStore.Downloads.DISPLAY_NAME} = ?",
                    arrayOf(FILE_NAME)
                )
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, FILE_NAME)
                    put(
                        MediaStore.Downloads.MIME_TYPE,
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                    )
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return
                resolver.openOutputStream(uri)?.use { writeWorkbook(it, entries) }
            } else {
                @Suppress("DEPRECATION")
                val dir = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS
                )
                dir.mkdirs()
                File(dir, FILE_NAME).outputStream().use { writeWorkbook(it, entries) }
            }
        } catch (_: Exception) {
            // Export to Downloads is best-effort; the master copy is already saved.
        }
    }
}

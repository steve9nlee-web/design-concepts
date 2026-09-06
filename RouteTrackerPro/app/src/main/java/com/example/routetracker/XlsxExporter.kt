package com.example.routetracker

import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Writes a genuine .xlsx workbook (Office Open XML) with no third-party
 * libraries: an xlsx file is just a zip of XML parts. Two sheets are
 * produced — "Trips" (one row per journey) and "Stops" (one row per
 * detected stop, linked to its trip by date/departure).
 */
object XlsxExporter {

    private val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val timeFmt = SimpleDateFormat("HH:mm", Locale.US)

    fun write(
        out: OutputStream,
        trips: List<Trip>,
        pointsFor: (Long) -> List<TrackPoint>
    ) {
        val tripRows = mutableListOf<List<Cell>>()
        val stopRows = mutableListOf<List<Cell>>()

        tripRows.add(
            listOf(
                Cell.Str("Date"), Cell.Str("Left home"), Cell.Str("Returned"),
                Cell.Str("Duration (min)"), Cell.Str("Distance (km)"), Cell.Str("Stops")
            )
        )
        stopRows.add(
            listOf(
                Cell.Str("Trip date"), Cell.Str("Trip departure"), Cell.Str("Arrived"),
                Cell.Str("Departed"), Cell.Str("Stayed (min)"), Cell.Str("Latitude"),
                Cell.Str("Longitude"), Cell.Str("Map link")
            )
        )

        // Oldest first reads naturally in a log.
        for (trip in trips.sortedBy { it.startTime }) {
            val points = pointsFor(trip.id)
            val stops = StopDetector.detect(points)
            val end = trip.endTime
            tripRows.add(
                listOf(
                    Cell.Str(dateFmt.format(Date(trip.startTime))),
                    Cell.Str(timeFmt.format(Date(trip.startTime))),
                    Cell.Str(end?.let { timeFmt.format(Date(it)) } ?: "(in progress)"),
                    end?.let { Cell.Num((it - trip.startTime) / 60000.0) } ?: Cell.Str(""),
                    Cell.Num(StopDetector.totalDistanceMeters(points) / 1000.0),
                    Cell.Num(stops.size.toDouble())
                )
            )
            for (s in stops) {
                stopRows.add(
                    listOf(
                        Cell.Str(dateFmt.format(Date(trip.startTime))),
                        Cell.Str(timeFmt.format(Date(trip.startTime))),
                        Cell.Str(timeFmt.format(Date(s.arrival))),
                        Cell.Str(timeFmt.format(Date(s.departure))),
                        Cell.Num(s.durationMs / 60000.0),
                        Cell.Num(s.lat),
                        Cell.Num(s.lon),
                        Cell.Str("https://maps.google.com/?q=%.6f,%.6f".format(Locale.US, s.lat, s.lon))
                    )
                )
            }
        }

        ZipOutputStream(out).use { zip ->
            zip.part("[Content_Types].xml", contentTypes())
            zip.part("_rels/.rels", rootRels())
            zip.part("xl/workbook.xml", workbook())
            zip.part("xl/_rels/workbook.xml.rels", workbookRels())
            zip.part("xl/styles.xml", styles())
            zip.part("xl/worksheets/sheet1.xml", sheet(tripRows))
            zip.part("xl/worksheets/sheet2.xml", sheet(stopRows))
        }
    }

    // ------------------------------------------------------------- pieces --

    sealed class Cell {
        data class Str(val v: String) : Cell()
        data class Num(val v: Double) : Cell()
    }

    private fun ZipOutputStream.part(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun esc(s: String): String = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&apos;")

    private fun colRef(index: Int): String {
        var i = index
        val sb = StringBuilder()
        while (i >= 0) {
            sb.insert(0, ('A' + i % 26))
            i = i / 26 - 1
        }
        return sb.toString()
    }

    private fun sheet(rows: List<List<Cell>>): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        val cols = rows.maxOfOrNull { it.size } ?: 1
        sb.append("""<cols><col min="1" max="$cols" width="16" customWidth="1"/></cols>""")
        sb.append("<sheetData>")
        rows.forEachIndexed { r, row ->
            sb.append("""<row r="${r + 1}">""")
            row.forEachIndexed { c, cell ->
                val ref = "${colRef(c)}${r + 1}"
                when (cell) {
                    is Cell.Str ->
                        sb.append("""<c r="$ref" t="inlineStr"><is><t xml:space="preserve">${esc(cell.v)}</t></is></c>""")
                    is Cell.Num -> {
                        // Trim floating noise: 12.30000000000001 -> 12.3
                        val num = if (cell.v % 1.0 == 0.0) cell.v.toLong().toString()
                        else "%.4f".format(Locale.US, cell.v).trimEnd('0').trimEnd('.')
                        sb.append("""<c r="$ref"><v>$num</v></c>""")
                    }
                }
            }
            sb.append("</row>")
        }
        sb.append("</sheetData></worksheet>")
        return sb.toString()
    }

    private fun contentTypes() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
<Override PartName="/xl/worksheets/sheet2.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
</Types>"""

    private fun rootRels() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""

    private fun workbook() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<sheets>
<sheet name="Trips" sheetId="1" r:id="rId1"/>
<sheet name="Stops" sheetId="2" r:id="rId2"/>
</sheets>
</workbook>"""

    private fun workbookRels() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/>
<Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""

    private fun styles() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
<fonts count="1"><font><sz val="11"/><name val="Calibri"/></font></fonts>
<fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills>
<borders count="1"><border/></borders>
<cellStyleXfs count="1"><xf/></cellStyleXfs>
<cellXfs count="1"><xf/></cellXfs>
<cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>
</styleSheet>"""
}

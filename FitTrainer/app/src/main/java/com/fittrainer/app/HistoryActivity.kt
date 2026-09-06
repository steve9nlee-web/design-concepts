package com.fittrainer.app

import android.content.ContentValues
import android.content.Intent
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.fittrainer.app.data.RecordStore
import com.fittrainer.app.data.WorkoutRecord
import com.fittrainer.app.data.XlsxWriter
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Workout records"
        render()
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val records = RecordStore.load(this).sortedByDescending { it.timestamp }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        setContentView(ScrollView(this).apply { addView(root) })

        fun margin(top: Int = 10) = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(top) }

        // Summary
        val days = records.map { it.dateString() }.distinct().size
        root.addView(TextView(this).apply {
            text = "Total workouts: ${records.size} • Active days: $days"
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
        })

        // Export buttons
        root.addView(MaterialButton(this).apply {
            text = "📥  Export to Excel (Downloads)"
            setOnClickListener { exportToDownloads(records) }
        }, margin())
        root.addView(MaterialButton(this).apply {
            text = "📤  Share Excel file"
            setOnClickListener { shareXlsx(records) }
        }, margin(6))
        root.addView(MaterialButton(this).apply {
            text = "🗑  Clear all records"
            setOnClickListener {
                AlertDialog.Builder(this@HistoryActivity)
                    .setTitle("Clear all records?")
                    .setMessage("This deletes every saved workout. Export to Excel first if you want to keep them.")
                    .setPositiveButton("Clear") { _, _ ->
                        RecordStore.clear(this@HistoryActivity)
                        render()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }, margin(6))

        if (records.isEmpty()) {
            root.addView(TextView(this).apply {
                text = "\nNo workouts logged yet.\nOpen a training area, do an exercise and tap “Save record”."
                textSize = 14f
            }, margin(16))
            return
        }

        var lastDate = ""
        for (r in records) {
            if (r.dateString() != lastDate) {
                lastDate = r.dateString()
                root.addView(TextView(this).apply {
                    text = lastDate
                    textSize = 15f
                    setTypeface(typeface, Typeface.BOLD)
                }, margin(14))
            }
            val unit = if (r.type == "TIME") "sec" else "reps"
            val card = MaterialCardView(this).apply {
                radius = dp(10).toFloat()
                cardElevation = dp(1).toFloat()
                setContentPadding(dp(14), dp(10), dp(14), dp(10))
            }
            val inner = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            inner.addView(TextView(this).apply {
                text = "${r.exercise}  (${r.area})"
                textSize = 15f
                setTypeface(typeface, Typeface.BOLD)
            })
            val noteSuffix = if (r.note.isNotEmpty()) " • ${r.note}" else ""
            inner.addView(TextView(this).apply {
                text = "${r.timeString()} • ${r.sets}×${r.amountPerSet} $unit (total ${r.totalAmount}) • ${r.ageCategory}$noteSuffix"
                textSize = 13f
            })
            card.addView(inner)
            root.addView(card, margin(6))
        }
    }

    private val headers = listOf(
        "Date", "Time", "Area", "Exercise", "Age Category",
        "Type", "Sets", "Reps/Seconds per Set", "Total", "Note"
    )

    private fun rows(records: List<WorkoutRecord>): List<List<Any>> =
        records.sortedBy { it.timestamp }.map {
            listOf(
                it.dateString(), it.timeString(), it.area, it.exercise, it.ageCategory,
                if (it.type == "TIME") "Timed (seconds)" else "Repetitions",
                it.sets, it.amountPerSet, it.totalAmount, it.note
            )
        }

    private fun fileName(): String =
        "FitTrainer_Records_${SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())}.xlsx"

    private val xlsxMime = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

    private fun exportToDownloads(records: List<WorkoutRecord>) {
        if (records.isEmpty()) {
            Toast.makeText(this, "Nothing to export yet", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val name = fileName()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.MIME_TYPE, xlsxMime)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = contentResolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI, values
                ) ?: throw IllegalStateException("Could not create file")
                contentResolver.openOutputStream(uri)!!.use {
                    XlsxWriter.write(it, headers, rows(records))
                }
            } else {
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                dir.mkdirs()
                File(dir, name).outputStream().use {
                    XlsxWriter.write(it, headers, rows(records))
                }
            }
            Toast.makeText(this, "Saved to Downloads:\n$name", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Export failed: ${e.message}\nTry the Share button instead.", Toast.LENGTH_LONG).show()
        }
    }

    private fun shareXlsx(records: List<WorkoutRecord>) {
        if (records.isEmpty()) {
            Toast.makeText(this, "Nothing to share yet", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val dir = File(cacheDir, "exports").apply { mkdirs() }
            val f = File(dir, fileName())
            f.outputStream().use { XlsxWriter.write(it, headers, rows(records)) }
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", f)
            startActivity(Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = xlsxMime
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }, "Share workout records"
            ))
        } catch (e: Exception) {
            Toast.makeText(this, "Share failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}

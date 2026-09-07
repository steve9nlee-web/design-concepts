package com.billcapture.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

class ProcessingActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PHOTO_PATH = "photo_path"
    }

    private lateinit var progressBar: ProgressBar
    private lateinit var textResults: TextView
    private lateinit var editCompanyName: EditText
    private lateinit var editCompanyNo: EditText
    private lateinit var editAddress: EditText
    private lateinit var editContact: EditText
    private lateinit var editDate: EditText
    private lateinit var editBillNo: EditText
    private lateinit var editCategory: AutoCompleteTextView
    private lateinit var editDescription: EditText
    private lateinit var editAmount: EditText
    private lateinit var textCapturedAt: TextView
    private lateinit var btnSave: Button

    private var photoPath: String? = null
    private var capturedAt: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_processing)

        progressBar = findViewById(R.id.progressBar)
        textResults = findViewById(R.id.textViewResults)
        editCompanyName = findViewById(R.id.editCompanyName)
        editCompanyNo = findViewById(R.id.editCompanyNo)
        editAddress = findViewById(R.id.editAddress)
        editContact = findViewById(R.id.editContact)
        editDate = findViewById(R.id.editDate)
        editBillNo = findViewById(R.id.editBillNo)
        editCategory = findViewById(R.id.editCategory)
        editDescription = findViewById(R.id.editDescription)
        editAmount = findViewById(R.id.editAmount)
        textCapturedAt = findViewById(R.id.textCapturedAt)
        btnSave = findViewById(R.id.btn_save_to_excel)

        editCategory.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_list_item_1, ReceiptParser.CATEGORIES)
        )

        photoPath = intent.getStringExtra(EXTRA_PHOTO_PATH)
        capturedAt = photoPath?.let { BillRepository.photoTakenAt(it) } ?: BillRepository.now()
        textCapturedAt.text = getString(R.string.photo_taken_at, capturedAt)

        findViewById<Button>(R.id.btn_retake_photo).setOnClickListener {
            startActivity(Intent(this, CameraActivity::class.java))
            finish()
        }
        findViewById<Button>(R.id.btn_manual_entry).setOnClickListener {
            val intent = Intent(this, ManualEntryActivity::class.java)
            photoPath?.let { intent.putExtra(ManualEntryActivity.EXTRA_PHOTO_PATH, it) }
            startActivity(intent)
            finish()
        }
        btnSave.setOnClickListener { saveBill() }

        runOcr()
    }

    private fun runOcr() {
        val path = photoPath
        if (path == null || !File(path).exists()) {
            textResults.text = getString(R.string.no_photo_found)
            return
        }

        progressBar.visibility = View.VISIBLE
        textResults.text = getString(R.string.processing)
        btnSave.isEnabled = false

        lifecycleScope.launch {
            try {
                val text = detectText(path)
                if (text.isBlank()) {
                    textResults.text = getString(R.string.ocr_nothing_found)
                } else {
                    fillFieldsFrom(text)
                    textResults.text = getString(R.string.ocr_done)
                }
            } catch (e: Exception) {
                textResults.text = getString(R.string.ocr_failed, e.message ?: "unknown error")
            } finally {
                progressBar.visibility = View.GONE
                btnSave.isEnabled = true
            }
        }
    }

    /**
     * On-device OCR with ML Kit. The Chinese recogniser also reads Latin
     * script, so bilingual receipts (item names in Chinese, totals in
     * English) come out in one pass — no cloud call, no API key.
     */
    private suspend fun detectText(path: String): String {
        val bitmap = withContext(Dispatchers.IO) { BitmapUtil.loadScaled(path) }
        val recognizer =
            TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        return try {
            val result = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
            reconstructLines(result)
        } finally {
            recognizer.close()
        }
    }

    /**
     * ML Kit groups text into blocks by column, so an item name and its price
     * often land in different blocks. Rebuild physical lines by sorting all
     * recognised lines top-to-bottom and merging the ones that sit at the
     * same height, left-to-right — that keeps "Chicken Rice   13.00" on one
     * line for the field extractor.
     */
    private fun reconstructLines(result: Text): String {
        data class Piece(val text: String, val left: Int, val centerY: Int, val height: Int)

        val pieces = result.textBlocks
            .flatMap { it.lines }
            .mapNotNull { line ->
                val box = line.boundingBox ?: return@mapNotNull null
                Piece(line.text, box.left, box.centerY(), box.height())
            }
            .sortedBy { it.centerY }
        if (pieces.isEmpty()) return result.text

        val rows = mutableListOf<MutableList<Piece>>()
        for (piece in pieces) {
            val row = rows.lastOrNull()
            val anchor = row?.first()
            if (anchor != null &&
                abs(anchor.centerY - piece.centerY) < maxOf(anchor.height, piece.height) * 0.6
            ) {
                row.add(piece)
            } else {
                rows.add(mutableListOf(piece))
            }
        }
        return rows.joinToString("\n") { row ->
            row.sortedBy { it.left }.joinToString("  ") { it.text }
        }
    }

    private fun fillFieldsFrom(text: String) {
        val fields = ReceiptParser.parse(text)
        editCompanyName.setText(fields.companyName)
        editCompanyNo.setText(fields.companyNo)
        editAddress.setText(fields.address)
        editContact.setText(fields.contact)
        editDate.setText(fields.billDate)
        editBillNo.setText(fields.billNo)
        editCategory.setText(fields.category, false)
        editDescription.setText(fields.description)
        editAmount.setText(fields.amount)
    }

    // ---- saving -------------------------------------------------------------

    private fun saveBill() {
        val entry = BillEntry(
            companyName = editCompanyName.text.toString().trim(),
            companyNo = editCompanyNo.text.toString().trim(),
            address = editAddress.text.toString().trim(),
            contact = editContact.text.toString().trim(),
            billDate = editDate.text.toString().trim(),
            billNo = editBillNo.text.toString().trim(),
            category = editCategory.text.toString().trim(),
            description = editDescription.text.toString().trim(),
            amount = editAmount.text.toString().trim(),
            capturedAt = capturedAt
        )
        if (entry.billDate.isEmpty() && entry.billNo.isEmpty() && entry.amount.isEmpty()) {
            Toast.makeText(this, R.string.nothing_to_save, Toast.LENGTH_SHORT).show()
            return
        }

        btnSave.isEnabled = false
        progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                BillRepository.save(this@ProcessingActivity, entry)
            }

            progressBar.visibility = View.GONE
            val message = when {
                result.driveError != null ->
                    getString(R.string.saved_drive_failed, result.driveError)
                result.savedToDrive -> getString(R.string.saved_and_synced)
                else -> getString(R.string.saved_locally, ExcelManager.FILE_NAME)
            }
            Toast.makeText(this@ProcessingActivity, message, Toast.LENGTH_LONG).show()
            photoPath?.let { File(it).delete() }
            finish()
        }
    }
}

package com.billcapture.app

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Fallback when OCR can't read a bill: the user uploads (or keeps) a photo
 * and types each field guided by the A–H section labels that mirror where
 * the information sits on a printed bill. On save, the photo is uploaded to
 * Google Drive alongside the Excel row so the original stays reviewable.
 */
class ManualEntryActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PHOTO_PATH = "photo_path"
    }

    private lateinit var imagePreview: ImageView
    private lateinit var textPhotoHint: TextView
    private lateinit var textCapturedAt: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var editCompanyName: EditText
    private lateinit var editCompanyNo: EditText
    private lateinit var editAddress: EditText
    private lateinit var editContact: EditText
    private lateinit var editDate: EditText
    private lateinit var editBillNo: EditText
    private lateinit var editCategory: AutoCompleteTextView
    private lateinit var editDescription: EditText
    private lateinit var editAmount: EditText
    private lateinit var btnSave: Button

    private var photoPath: String? = null
    private var capturedAt: String = ""

    private val pickPhotoLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) importPhoto(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manual_entry)

        imagePreview = findViewById(R.id.imagePreview)
        textPhotoHint = findViewById(R.id.textPhotoHint)
        textCapturedAt = findViewById(R.id.textCapturedAt)
        progressBar = findViewById(R.id.progressBar)
        editCompanyName = findViewById(R.id.editCompanyName)
        editCompanyNo = findViewById(R.id.editCompanyNo)
        editAddress = findViewById(R.id.editAddress)
        editContact = findViewById(R.id.editContact)
        editDate = findViewById(R.id.editDate)
        editBillNo = findViewById(R.id.editBillNo)
        editCategory = findViewById(R.id.editCategory)
        editDescription = findViewById(R.id.editDescription)
        editAmount = findViewById(R.id.editAmount)
        btnSave = findViewById(R.id.btn_save_manual)

        editCategory.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_list_item_1, ReceiptParser.CATEGORIES)
        )

        findViewById<Button>(R.id.btn_pick_photo).setOnClickListener {
            pickPhotoLauncher.launch("image/*")
        }
        findViewById<Button>(R.id.btn_back).setOnClickListener { finish() }
        btnSave.setOnClickListener { saveBill() }

        intent.getStringExtra(EXTRA_PHOTO_PATH)?.let { usePhoto(it) }
    }

    /** Copies a picked gallery photo into the cache so EXIF and upload work. */
    private fun importPhoto(uri: Uri) {
        lifecycleScope.launch {
            val copied = withContext(Dispatchers.IO) {
                try {
                    val target = File(cacheDir, "manual_${System.currentTimeMillis()}.jpg")
                    contentResolver.openInputStream(uri)?.use { input ->
                        target.outputStream().use { input.copyTo(it) }
                    } ?: return@withContext null
                    target.absolutePath
                } catch (_: Exception) {
                    null
                }
            }
            if (copied == null) {
                Toast.makeText(
                    this@ManualEntryActivity, R.string.photo_load_failed, Toast.LENGTH_LONG
                ).show()
            } else {
                usePhoto(copied)
            }
        }
    }

    private fun usePhoto(path: String) {
        photoPath = path
        capturedAt = BillRepository.photoTakenAt(path)
        textCapturedAt.text = getString(R.string.photo_taken_at, capturedAt)
        textPhotoHint.visibility = View.GONE
        try {
            imagePreview.setImageBitmap(BitmapFactory.decodeFile(path))
        } catch (_: Exception) {
            // Preview is cosmetic; the fields still work without it.
        }
    }

    private fun saveBill() {
        val photo = photoPath
        if (photo == null) {
            Toast.makeText(this, R.string.photo_required, Toast.LENGTH_LONG).show()
            return
        }
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
            capturedAt = capturedAt.ifBlank { BillRepository.now() }
        )
        val dataFields = listOf(
            entry.companyName, entry.companyNo, entry.address, entry.contact,
            entry.billDate, entry.billNo, entry.category, entry.description, entry.amount
        )
        if (dataFields.all { it.isBlank() }) {
            Toast.makeText(this, R.string.nothing_to_save, Toast.LENGTH_SHORT).show()
            return
        }

        btnSave.isEnabled = false
        progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                BillRepository.save(this@ManualEntryActivity, entry, File(photo))
            }

            progressBar.visibility = View.GONE
            btnSave.isEnabled = true
            val message = when {
                result.driveError != null ->
                    getString(R.string.saved_drive_failed, result.driveError)
                result.savedToDrive -> getString(R.string.saved_synced_with_photo)
                else -> getString(R.string.saved_locally, ExcelManager.FILE_NAME)
            }
            Toast.makeText(this@ManualEntryActivity, message, Toast.LENGTH_LONG).show()
            File(photo).delete()
            finish()
        }
    }
}

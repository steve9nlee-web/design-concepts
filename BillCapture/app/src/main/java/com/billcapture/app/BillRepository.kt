package com.billcapture.app

import android.content.Context
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Shared save path for both the OCR flow and the manual-entry flow:
 * append to the xlsx, then (when signed in) sync the xlsx — and optionally
 * the bill photo — to Google Drive. Call from a background thread.
 */
object BillRepository {

    data class SaveResult(val savedToDrive: Boolean, val driveError: String?)

    fun save(
        context: Context,
        entry: BillEntry,
        photoToUpload: File? = null
    ): SaveResult {
        ExcelManager.append(context, entry)

        val account = SessionManager.signedInAccount(context) ?: return SaveResult(false, null)
        return try {
            DriveUploader.upload(context, account, ExcelManager.masterFile(context))
            photoToUpload?.let {
                DriveUploader.uploadPhoto(context, account, it, photoName(entry))
            }
            SaveResult(true, null)
        } catch (e: Exception) {
            SaveResult(false, e.message ?: "Drive upload failed")
        }
    }

    private fun photoName(entry: BillEntry): String {
        val id = entry.billNo.ifBlank { entry.capturedAt.ifBlank { "bill" } }
        return "bill_${id.replace(Regex("""[^A-Za-z0-9\-_]"""), "_")}.jpg"
    }

    /**
     * When the photo was taken: EXIF capture time if the file has it (camera
     * shots and most gallery imports do), otherwise the file's own timestamp.
     */
    fun photoTakenAt(path: String): String {
        val display = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.US)
        try {
            val exifValue = ExifInterface(path).getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                ?: ExifInterface(path).getAttribute(ExifInterface.TAG_DATETIME)
            if (exifValue != null) {
                val exif = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)
                exif.parse(exifValue)?.let { return display.format(it) }
            }
        } catch (_: Exception) {
            // fall through to the file timestamp
        }
        val modified = File(path).lastModified()
        return display.format(if (modified > 0) Date(modified) else Date())
    }

    fun now(): String =
        SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.US).format(Date())
}

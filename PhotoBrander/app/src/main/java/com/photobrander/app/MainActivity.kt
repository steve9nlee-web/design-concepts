package com.photobrander.app

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.photobrander.app.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var settings: AppSettings
    private val n8nClient = N8nClient()

    private var selectedPhotoUri: Uri? = null
    private var cameraOutputUri: Uri? = null
    private var resultImageBytes: ByteArray? = null

    private val pickPhotoLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) onPhotoSelected(uri)
    }

    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = cameraOutputUri
        if (success && uri != null) onPhotoSelected(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        settings = AppSettings(this)

        binding.buttonPickPhoto.setOnClickListener {
            pickPhotoLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
        binding.buttonTakePhoto.setOnClickListener { launchCamera() }
        binding.buttonGenerate.setOnClickListener { generate() }
        binding.buttonSave.setOnClickListener { saveResultToGallery() }
    }

    override fun onResume() {
        super.onResume()
        if (!settings.isConfigured()) {
            binding.textStatus.text = getString(R.string.status_configure_first)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (item.itemId == R.id.action_settings) {
            startActivity(Intent(this, SettingsActivity::class.java))
            true
        } else {
            super.onOptionsItemSelected(item)
        }
    }

    private fun launchCamera() {
        val cameraDir = File(cacheDir, "camera").apply { mkdirs() }
        val photoFile = File(cameraDir, "capture_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", photoFile)
        cameraOutputUri = uri
        takePictureLauncher.launch(uri)
    }

    private fun onPhotoSelected(uri: Uri) {
        selectedPhotoUri = uri
        binding.imagePreview.setImageURI(null)
        binding.imagePreview.setImageURI(uri)
        binding.imagePreview.visibility = View.VISIBLE
        binding.textStatus.text = getString(R.string.status_photo_ready)
        resultImageBytes = null
        binding.imageResult.visibility = View.GONE
        binding.buttonSave.visibility = View.GONE
        binding.labelResult.visibility = View.GONE
    }

    private fun generate() {
        val photoUri = selectedPhotoUri
        if (photoUri == null) {
            toast(getString(R.string.error_no_photo))
            return
        }
        val description = binding.editDescription.text?.toString()?.trim().orEmpty()
        if (description.isEmpty()) {
            toast(getString(R.string.error_no_description))
            return
        }
        if (!settings.isConfigured()) {
            toast(getString(R.string.error_not_configured))
            startActivity(Intent(this, SettingsActivity::class.java))
            return
        }

        setBusy(true)
        binding.textStatus.text = getString(R.string.status_uploading)

        lifecycleScope.launch {
            try {
                val photoBytes = withContext(Dispatchers.IO) { readAndDownscale(photoUri) }
                binding.textStatus.text = getString(R.string.status_generating)
                val result = n8nClient.generateBrandedPhoto(
                    webhookUrl = settings.webhookUrl,
                    photoJpeg = photoBytes,
                    description = description,
                    companyName = settings.companyName,
                    companyDetails = settings.companyDetails,
                    logoFile = if (settings.hasLogo()) settings.logoFile else null,
                )
                val bitmap = withContext(Dispatchers.Default) {
                    BitmapFactory.decodeByteArray(result, 0, result.size)
                } ?: throw IllegalStateException(getString(R.string.error_bad_image))

                resultImageBytes = result
                binding.imageResult.setImageBitmap(bitmap)
                binding.imageResult.visibility = View.VISIBLE
                binding.labelResult.visibility = View.VISIBLE
                binding.buttonSave.visibility = View.VISIBLE
                binding.textStatus.text = getString(R.string.status_done)
            } catch (e: Exception) {
                binding.textStatus.text = getString(R.string.status_failed, e.message ?: "unknown error")
            } finally {
                setBusy(false)
            }
        }
    }

    /** Decodes the picked photo and downsizes it so uploads stay reasonably small. */
    private fun readAndDownscale(uri: Uri): ByteArray {
        val original = contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input)
        } ?: throw IllegalStateException(getString(R.string.error_read_photo))

        val maxSide = max(original.width, original.height)
        val bitmap = if (maxSide > MAX_UPLOAD_SIDE_PX) {
            val scale = MAX_UPLOAD_SIDE_PX.toFloat() / maxSide
            Bitmap.createScaledBitmap(
                original,
                (original.width * scale).toInt(),
                (original.height * scale).toInt(),
                true
            )
        } else {
            original
        }

        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        return out.toByteArray()
    }

    private fun saveResultToGallery() {
        val bytes = resultImageBytes ?: return
        val fileName = "PhotoBrander_" +
            SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".png"
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/PhotoBrander")
                }
                val uri = contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
                ) ?: throw IllegalStateException("MediaStore insert failed")
                contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
            } else {
                @Suppress("DEPRECATION")
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    "PhotoBrander"
                ).apply { mkdirs() }
                val file = File(dir, fileName)
                file.writeBytes(bytes)
                MediaStore.Images.Media.insertImage(
                    contentResolver, file.absolutePath, fileName, null
                )
            }
            toast(getString(R.string.saved_to_gallery))
        } catch (e: Exception) {
            toast(getString(R.string.error_save_failed, e.message ?: ""))
        }
    }

    private fun setBusy(busy: Boolean) {
        binding.progress.visibility = if (busy) View.VISIBLE else View.GONE
        binding.buttonGenerate.isEnabled = !busy
        binding.buttonPickPhoto.isEnabled = !busy
        binding.buttonTakePhoto.isEnabled = !busy
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    companion object {
        private const val MAX_UPLOAD_SIDE_PX = 2048
    }
}

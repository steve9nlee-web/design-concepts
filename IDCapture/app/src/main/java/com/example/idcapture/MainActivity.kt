package com.example.idcapture

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var overlayView: DocumentOverlayView
    private lateinit var captureButton: FloatingActionButton

    private var imageCapture: ImageCapture? = null

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            if (results[Manifest.permission.CAMERA] == true) {
                startCamera()
            } else {
                Toast.makeText(this, R.string.camera_permission_denied, Toast.LENGTH_LONG).show()
                finish()
            }
        }

    // System photo picker — no storage permission needed on any API level.
    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) importPhoto(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        previewView = findViewById(R.id.previewView)
        overlayView = findViewById(R.id.overlayView)
        captureButton = findViewById(R.id.captureButton)

        findViewById<MaterialButtonToggleGroup>(R.id.modeToggle)
            .addOnButtonCheckedListener { _, checkedId, isChecked ->
                if (isChecked) {
                    overlayView.mode = when (checkedId) {
                        R.id.buttonPassport -> DocumentMode.PASSPORT
                        else -> DocumentMode.IC
                    }
                }
            }

        captureButton.setOnClickListener { capturePhoto() }
        findViewById<MaterialButton>(R.id.galleryButton).setOnClickListener {
            pickImageLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
        }
        findViewById<ImageButton>(R.id.nasSettingsButton).setOnClickListener { showNasDialog() }

        if (hasCameraPermission()) {
            startCamera()
        } else {
            permissionLauncher.launch(requiredPermissions())
        }
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    private fun requiredPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            arrayOf(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            arrayOf(Manifest.permission.CAMERA)
        }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                // Documents are shot flash-off: a flash bounces straight back
                // off laminated cards and washes out the print.
                .setFlashMode(ImageCapture.FLASH_MODE_OFF)
                .build()

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture,
                )
            } catch (e: Exception) {
                Toast.makeText(this, getString(R.string.camera_start_failed, e.message), Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun capturePhoto() {
        val imageCapture = imageCapture ?: return
        captureButton.isEnabled = false

        imageCapture.takePicture(
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val rotationDegrees = image.imageInfo.rotationDegrees
                    val bitmap = image.toBitmap()
                    image.close()

                    val upright = OutputBox.rotate(bitmap, rotationDegrees)
                    val cropped = cropToFrame(upright)
                    val boxed = OutputBox.fill(cropped, overlayView.mode)
                    saveAndUpload(boxed)
                    captureButton.isEnabled = true
                }

                override fun onError(exception: ImageCaptureException) {
                    captureButton.isEnabled = true
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.capture_failed, exception.message),
                        Toast.LENGTH_LONG,
                    ).show()
                }
            },
        )
    }

    /**
     * Loads a picked gallery photo (downsampled near the box size, EXIF
     * rotation applied) and letterboxes it into the current mode's box.
     */
    private fun importPhoto(uri: Uri) {
        val mode = overlayView.mode
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }

            val opts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, mode)
            }
            val bitmap = contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: throw IllegalStateException("decode failed")

            val rotation = contentResolver.openInputStream(uri)?.use {
                ExifInterface(it).rotationDegrees
            } ?: 0

            val boxed = OutputBox.fit(OutputBox.rotate(bitmap, rotation), mode)
            saveAndUpload(boxed)
        } catch (e: Exception) {
            Toast.makeText(this, R.string.import_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun sampleSize(width: Int, height: Int, mode: DocumentMode): Int {
        var sample = 1
        while (width / (sample * 2) >= mode.boxWidth && height / (sample * 2) >= mode.boxHeight) {
            sample *= 2
        }
        return sample
    }

    /**
     * Maps the overlay's guide frame (view coordinates) onto the captured bitmap
     * and crops to it. PreviewView uses FILL_CENTER by default: the image is
     * scaled uniformly to cover the view and center-cropped, so we invert that
     * transform here.
     */
    private fun cropToFrame(bitmap: Bitmap): Bitmap {
        val frame = overlayView.frameRect
        val viewWidth = previewView.width.toFloat()
        val viewHeight = previewView.height.toFloat()
        if (frame.isEmpty || viewWidth == 0f || viewHeight == 0f) return bitmap

        val scale = max(viewWidth / bitmap.width, viewHeight / bitmap.height)
        val offsetX = (bitmap.width * scale - viewWidth) / 2f
        val offsetY = (bitmap.height * scale - viewHeight) / 2f

        val left = ((frame.left + offsetX) / scale).roundToInt().coerceIn(0, bitmap.width - 1)
        val top = ((frame.top + offsetY) / scale).roundToInt().coerceIn(0, bitmap.height - 1)
        val right = ((frame.right + offsetX) / scale).roundToInt().coerceIn(left + 1, bitmap.width)
        val bottom = ((frame.bottom + offsetY) / scale).roundToInt().coerceIn(top + 1, bitmap.height)

        return Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
    }

    /** Saves the boxed image to the gallery, then queues the NAS upload if enabled. */
    private fun saveAndUpload(bitmap: Bitmap) {
        val mode = overlayView.mode
        val name = mode.filePrefix + "_" +
            SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/IDCapture")
            }
        }

        val resolver = contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        if (uri == null) {
            Toast.makeText(this, R.string.save_failed, Toast.LENGTH_LONG).show()
            return
        }

        try {
            resolver.openOutputStream(uri)?.use { stream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)
            }
            if (NasSettings.isEnabled(this) && NasSettings.isConfigured(this)) {
                NasSettings.enqueueUpload(this, uri, name)
                Toast.makeText(this, getString(R.string.saved_uploading, name), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, getString(R.string.saved_to, name), Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            Toast.makeText(this, R.string.save_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun showNasDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_nas_settings, null)
        val enabledSwitch = view.findViewById<MaterialSwitch>(R.id.nasSwitch)
        val hostInput = view.findViewById<TextInputEditText>(R.id.hostInput)
        val shareInput = view.findViewById<TextInputEditText>(R.id.shareInput)
        val folderInput = view.findViewById<TextInputEditText>(R.id.folderInput)
        val userInput = view.findViewById<TextInputEditText>(R.id.userInput)
        val passwordInput = view.findViewById<TextInputEditText>(R.id.passwordInput)

        enabledSwitch.isChecked = NasSettings.isEnabled(this)
        hostInput.setText(NasSettings.host(this))
        shareInput.setText(NasSettings.share(this))
        folderInput.setText(NasSettings.folder(this))
        userInput.setText(NasSettings.username(this))
        passwordInput.setText(NasSettings.password(this))

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.nas_title)
            .setView(view)
            .setNegativeButton(R.string.nas_cancel, null)
            .setPositiveButton(R.string.nas_save) { _, _ ->
                val enabled = enabledSwitch.isChecked
                NasSettings.save(
                    this,
                    enabled = enabled,
                    host = hostInput.text?.toString().orEmpty(),
                    share = shareInput.text?.toString().orEmpty(),
                    folder = folderInput.text?.toString().orEmpty(),
                    username = userInput.text?.toString().orEmpty(),
                    password = passwordInput.text?.toString().orEmpty(),
                )
                val message = when {
                    enabled && NasSettings.isConfigured(this) -> R.string.nas_enabled
                    enabled -> R.string.nas_missing_config
                    else -> R.string.nas_disabled
                }
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            }
            .show()
    }
}

package com.example.docscanner

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var overlayView: DocumentOverlayView
    private lateinit var captureButton: FloatingActionButton
    private lateinit var glareText: TextView
    private lateinit var analysisExecutor: ExecutorService

    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null

    // Glare state, debounced so the UI doesn't flicker frame to frame.
    private var glareActive = false
    private var pendingGlare = false
    private var pendingGlareCount = 0

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            if (results[Manifest.permission.CAMERA] == true) {
                startCamera()
            } else {
                Toast.makeText(this, R.string.camera_permission_denied, Toast.LENGTH_LONG).show()
                finish()
            }
        }

    private val driveAuthLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == RESULT_OK) onDriveAuthorized() else onDriveAuthFailed()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        previewView = findViewById(R.id.previewView)
        overlayView = findViewById(R.id.overlayView)
        captureButton = findViewById(R.id.captureButton)
        glareText = findViewById(R.id.glareText)
        analysisExecutor = Executors.newSingleThreadExecutor()

        findViewById<MaterialButtonToggleGroup>(R.id.modeToggle)
            .addOnButtonCheckedListener { _, checkedId, isChecked ->
                if (isChecked) {
                    overlayView.mode = when (checkedId) {
                        R.id.buttonPassport -> DocumentOverlayView.Mode.PASSPORT
                        else -> DocumentOverlayView.Mode.ID_CARD
                    }
                }
            }

        captureButton.setOnClickListener { capturePhoto() }
        findViewById<ImageButton>(R.id.driveSyncButton).setOnClickListener { showDriveSyncDialog() }

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

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(
                        analysisExecutor,
                        GlareAnalyzer(
                            frameRect = { overlayView.frameRect },
                            viewSize = { previewView.width to previewView.height },
                            onResult = { glare ->
                                runOnUiThread { onGlareResult(glare) }
                            },
                        ),
                    )
                }

            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture,
                    imageAnalysis,
                )
            } catch (e: Exception) {
                Toast.makeText(this, getString(R.string.camera_start_failed, e.message), Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    /**
     * Debounces the per-frame glare signal (3 consecutive agreeing frames)
     * before flipping the UI and nudging exposure down to tame reflections.
     */
    private fun onGlareResult(glare: Boolean) {
        if (glare == pendingGlare) {
            pendingGlareCount++
        } else {
            pendingGlare = glare
            pendingGlareCount = 1
        }
        if (pendingGlareCount >= 3 && glare != glareActive) {
            glareActive = glare
            overlayView.glare = glare
            glareText.visibility = if (glare) View.VISIBLE else View.GONE
            applyGlareExposure(glare)
        }
    }

    /** Pull exposure down while glare is present so highlights aren't blown out. */
    private fun applyGlareExposure(glare: Boolean) {
        val camera = camera ?: return
        val state = camera.cameraInfo.exposureState
        if (!state.isExposureCompensationSupported) return
        val index = if (glare) max(state.exposureCompensationRange.lower, -2) else 0
        camera.cameraControl.setExposureCompensationIndex(index)
    }

    private fun capturePhoto() {
        val imageCapture = imageCapture ?: return
        if (glareActive) {
            Toast.makeText(this, R.string.glare_capture_warning, Toast.LENGTH_SHORT).show()
        }
        captureButton.isEnabled = false

        imageCapture.takePicture(
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val rotationDegrees = image.imageInfo.rotationDegrees
                    val bitmap = image.toBitmap()
                    image.close()

                    val upright = rotateBitmap(bitmap, rotationDegrees)
                    val cropped = cropToFrame(upright)
                    saveToGallery(cropped)
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

    private fun rotateBitmap(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return bitmap
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
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

    private fun saveToGallery(bitmap: Bitmap) {
        val name = "DOC_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/DocScanner")
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
            if (DriveSync.isEnabled(this)) {
                DriveSync.enqueueUpload(this, uri, name)
                Toast.makeText(this, getString(R.string.saved_to_uploading, name), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, getString(R.string.saved_to, name), Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            Toast.makeText(this, R.string.save_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun showDriveSyncDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_drive_sync, null)
        val syncSwitch = view.findViewById<MaterialSwitch>(R.id.syncSwitch)
        val folderInput = view.findViewById<TextInputEditText>(R.id.folderInput)
        syncSwitch.isChecked = DriveSync.isEnabled(this)
        folderInput.setText(DriveSync.folderName(this))

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.drive_sync_title)
            .setView(view)
            .setNegativeButton(R.string.drive_sync_cancel, null)
            .setPositiveButton(R.string.drive_sync_save) { _, _ ->
                DriveSync.setFolderName(this, folderInput.text?.toString().orEmpty())
                if (syncSwitch.isChecked) {
                    requestDriveAuthorization()
                } else if (DriveSync.isEnabled(this)) {
                    DriveSync.setEnabled(this, false)
                    Toast.makeText(this, R.string.drive_sync_disabled, Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    /**
     * Asks Google Play services for the drive.file scope. The first time this
     * shows Google's account picker + consent screen; afterwards it resolves
     * silently, and the background worker reuses the same grant for its tokens.
     */
    private fun requestDriveAuthorization() {
        Identity.getAuthorizationClient(this)
            .authorize(DriveSync.authorizationRequest())
            .addOnSuccessListener { result ->
                val pendingIntent = result.pendingIntent
                if (!result.hasResolution()) {
                    onDriveAuthorized()
                } else if (pendingIntent != null) {
                    driveAuthLauncher.launch(
                        IntentSenderRequest.Builder(pendingIntent.intentSender).build(),
                    )
                } else {
                    onDriveAuthFailed()
                }
            }
            .addOnFailureListener { onDriveAuthFailed() }
    }

    private fun onDriveAuthorized() {
        DriveSync.setEnabled(this, true)
        Toast.makeText(this, R.string.drive_sync_enabled, Toast.LENGTH_SHORT).show()
    }

    private fun onDriveAuthFailed() {
        DriveSync.setEnabled(this, false)
        Toast.makeText(this, R.string.drive_sync_auth_failed, Toast.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::analysisExecutor.isInitialized) analysisExecutor.shutdown()
    }
}

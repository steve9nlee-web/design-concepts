package com.billcapture.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.io.File

class CameraActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private var imageCapture: ImageCapture? = null

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCamera()
            else {
                Toast.makeText(this, "Camera permission is required", Toast.LENGTH_LONG).show()
                finish()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        previewView = findViewById(R.id.previewView)
        val guideBox = findViewById<ImageView>(R.id.guideBoxView)
        guideBox.post { guideBox.setImageBitmap(drawGuideOverlay(guideBox.width, guideBox.height)) }

        findViewById<Button>(R.id.btn_back).setOnClickListener { finish() }
        findViewById<Button>(R.id.btn_capture).setOnClickListener { takePhoto() }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) startCamera()
        else permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val cameraProvider = providerFuture.get()
            val preview = Preview.Builder().build()
                .also { it.surfaceProvider = previewView.surfaceProvider }
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .build()
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture
                )
            } catch (e: Exception) {
                Toast.makeText(this, "Camera unavailable: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        val capture = imageCapture ?: return
        val photoFile = File(cacheDir, "bill_${System.currentTimeMillis()}.jpg")
        val options = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        capture.takePicture(
            options,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val intent = Intent(this@CameraActivity, ProcessingActivity::class.java)
                        .putExtra(ProcessingActivity.EXTRA_PHOTO_PATH, photoFile.absolutePath)
                    startActivity(intent)
                    finish()
                }

                override fun onError(exception: ImageCaptureException) {
                    Toast.makeText(
                        this@CameraActivity,
                        "Capture failed: ${exception.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        )
    }

    /** White guide rectangle with green corner marks, matching the setup guide. */
    private fun drawGuideOverlay(width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val margin = width * 0.08f
        val box = RectF(margin, height * 0.15f, width - margin, height * 0.72f)

        val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }
        canvas.drawRect(box, boxPaint)

        val cornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1f7a1f")
            style = Paint.Style.STROKE
            strokeWidth = 12f
        }
        val len = width * 0.09f
        // Top-left, top-right, bottom-left, bottom-right corner marks
        canvas.drawLine(box.left, box.top, box.left + len, box.top, cornerPaint)
        canvas.drawLine(box.left, box.top, box.left, box.top + len, cornerPaint)
        canvas.drawLine(box.right, box.top, box.right - len, box.top, cornerPaint)
        canvas.drawLine(box.right, box.top, box.right, box.top + len, cornerPaint)
        canvas.drawLine(box.left, box.bottom, box.left + len, box.bottom, cornerPaint)
        canvas.drawLine(box.left, box.bottom, box.left, box.bottom - len, cornerPaint)
        canvas.drawLine(box.right, box.bottom, box.right - len, box.bottom, cornerPaint)
        canvas.drawLine(box.right, box.bottom, box.right, box.bottom - len, cornerPaint)

        return bitmap
    }
}

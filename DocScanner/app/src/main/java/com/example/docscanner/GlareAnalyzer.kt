package com.example.docscanner

import android.graphics.RectF
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import kotlin.math.max

/**
 * Samples luminance inside the guide frame on every preview frame and reports
 * whether a meaningful patch of pixels is blown out (specular glare from a
 * laminated card or passport page).
 *
 * The analysis image arrives in sensor orientation; sampled coordinates are
 * mapped from view space -> upright image space (inverting PreviewView's
 * FILL_CENTER transform) -> sensor space (inverting the display rotation).
 */
class GlareAnalyzer(
    private val frameRect: () -> RectF,
    private val viewSize: () -> Pair<Int, Int>,
    private val onResult: (Boolean) -> Unit,
) : ImageAnalysis.Analyzer {

    private companion object {
        const val LUMA_BLOWN_OUT = 250
        const val GLARE_FRACTION = 0.004f
        const val MIN_BLOWN_PIXELS = 12
        const val SAMPLE_GRID = 72
    }

    override fun analyze(image: ImageProxy) {
        try {
            onResult(hasGlare(image))
        } finally {
            image.close()
        }
    }

    private fun hasGlare(image: ImageProxy): Boolean {
        val frame = frameRect()
        val (viewW, viewH) = viewSize()
        if (frame.isEmpty || viewW == 0 || viewH == 0) return false

        val rotation = image.imageInfo.rotationDegrees
        val sensorW = image.width
        val sensorH = image.height
        val uprightW = if (rotation % 180 == 0) sensorW else sensorH
        val uprightH = if (rotation % 180 == 0) sensorH else sensorW

        // View coords -> upright image coords (FILL_CENTER inverse).
        val scale = max(viewW.toFloat() / uprightW, viewH.toFloat() / uprightH)
        val offsetX = (uprightW * scale - viewW) / 2f
        val offsetY = (uprightH * scale - viewH) / 2f
        val left = ((frame.left + offsetX) / scale).toInt().coerceIn(0, uprightW - 1)
        val top = ((frame.top + offsetY) / scale).toInt().coerceIn(0, uprightH - 1)
        val right = ((frame.right + offsetX) / scale).toInt().coerceIn(left + 1, uprightW)
        val bottom = ((frame.bottom + offsetY) / scale).toInt().coerceIn(top + 1, uprightH)

        val plane = image.planes[0]
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride

        val stepX = max(1, (right - left) / SAMPLE_GRID)
        val stepY = max(1, (bottom - top) / SAMPLE_GRID)
        var blownOut = 0
        var total = 0

        var y = top
        while (y < bottom) {
            var x = left
            while (x < right) {
                // Upright coords -> sensor coords (invert clockwise rotation).
                val sensorX: Int
                val sensorY: Int
                when (rotation) {
                    90 -> { sensorX = y; sensorY = sensorH - 1 - x }
                    180 -> { sensorX = sensorW - 1 - x; sensorY = sensorH - 1 - y }
                    270 -> { sensorX = sensorW - 1 - y; sensorY = x }
                    else -> { sensorX = x; sensorY = y }
                }
                val luma = buffer.get(sensorY * rowStride + sensorX * pixelStride).toInt() and 0xFF
                if (luma >= LUMA_BLOWN_OUT) blownOut++
                total++
                x += stepX
            }
            y += stepY
        }

        return total > 0 &&
            blownOut >= MIN_BLOWN_PIXELS &&
            blownOut.toFloat() / total >= GLARE_FRACTION
    }
}

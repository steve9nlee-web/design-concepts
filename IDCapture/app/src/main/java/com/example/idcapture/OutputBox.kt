package com.example.idcapture

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.min

/**
 * The two supported document types, each with the physical aspect ratio used
 * for the camera guide frame and a fixed pixel-size output box (~20 px/mm)
 * every saved image is normalized into, so all files on the NAS come out at
 * the same predictable dimensions.
 */
enum class DocumentMode(
    val aspectRatio: Float,
    val boxWidth: Int,
    val boxHeight: Int,
    val filePrefix: String,
) {
    /** ISO/IEC 7810 ID-1: 85.60 x 53.98 mm (national ICs, driver's licenses) */
    IC(85.60f / 53.98f, 1712, 1080, "IC"),

    /** Passport data page, roughly 125 x 88 mm */
    PASSPORT(125f / 88f, 2500, 1760, "PASSPORT"),
}

object OutputBox {

    /**
     * Scales [source] uniformly to completely fill the mode's box and
     * center-crops the excess. Used for camera captures, which are already
     * cropped to the guide frame so at most a sliver is trimmed.
     */
    fun fill(source: Bitmap, mode: DocumentMode): Bitmap {
        val out = Bitmap.createBitmap(mode.boxWidth, mode.boxHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.WHITE)

        val scale = maxOf(
            mode.boxWidth.toFloat() / source.width,
            mode.boxHeight.toFloat() / source.height,
        )
        drawScaled(canvas, source, scale, mode)
        return out
    }

    /**
     * Scales [source] uniformly to fit entirely inside the mode's box,
     * centered on a white background. Used for imported photos, whose aspect
     * ratio is unknown — nothing gets cut off.
     */
    fun fit(source: Bitmap, mode: DocumentMode): Bitmap {
        val out = Bitmap.createBitmap(mode.boxWidth, mode.boxHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.WHITE)

        val scale = min(
            mode.boxWidth.toFloat() / source.width,
            mode.boxHeight.toFloat() / source.height,
        )
        drawScaled(canvas, source, scale, mode)
        return out
    }

    private fun drawScaled(canvas: Canvas, source: Bitmap, scale: Float, mode: DocumentMode) {
        val w = source.width * scale
        val h = source.height * scale
        val left = (mode.boxWidth - w) / 2f
        val top = (mode.boxHeight - h) / 2f
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        canvas.drawBitmap(source, null, RectF(left, top, left + w, top + h), paint)
    }

    /** Rotates a bitmap by the given EXIF/sensor rotation, if any. */
    fun rotate(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees % 360 == 0) return bitmap
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}

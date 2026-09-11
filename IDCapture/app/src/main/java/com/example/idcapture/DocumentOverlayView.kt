package com.example.idcapture

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * Full-screen overlay that dims the camera preview and punches out a
 * document-shaped window the user should align the IC / passport with.
 * [frameRect] (view coordinates) is used by MainActivity to crop the capture.
 */
class DocumentOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    var mode: DocumentMode = DocumentMode.IC
        set(value) {
            field = value
            recomputeFrame()
            invalidate()
        }

    val frameRect = RectF()

    private val density = resources.displayMetrics.density
    private val cornerRadius = 12f * density
    private val bracketLength = 26f * density

    private val scrimPaint = Paint().apply {
        color = Color.parseColor("#A6000000")
    }
    private val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        strokeWidth = 1.5f * density
    }
    private val bracketPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#4CAF50")
        strokeWidth = 4f * density
        strokeCap = Paint.Cap.ROUND
    }

    init {
        // PorterDuff.CLEAR needs an offscreen layer to punch a hole in the scrim.
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        recomputeFrame()
    }

    private fun recomputeFrame() {
        if (width == 0 || height == 0) return

        var frameWidth = width * 0.88f
        var frameHeight = frameWidth / mode.aspectRatio
        val maxHeight = height * 0.55f
        if (frameHeight > maxHeight) {
            frameHeight = maxHeight
            frameWidth = frameHeight * mode.aspectRatio
        }

        val left = (width - frameWidth) / 2f
        // Sit the frame a bit above center so it clears the bottom controls.
        val top = (height - frameHeight) * 0.40f
        frameRect.set(left, top, left + frameWidth, top + frameHeight)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (frameRect.isEmpty) return

        // Dim everything, then cut the document window out of the scrim.
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrimPaint)
        canvas.drawRoundRect(frameRect, cornerRadius, cornerRadius, clearPaint)
        canvas.drawRoundRect(frameRect, cornerRadius, cornerRadius, borderPaint)
        drawCornerBrackets(canvas)
    }

    private fun drawCornerBrackets(canvas: Canvas) {
        val r = frameRect
        val len = bracketLength
        val inset = bracketPaint.strokeWidth / 2f

        // Top-left
        canvas.drawLine(r.left - inset, r.top, r.left + len, r.top, bracketPaint)
        canvas.drawLine(r.left, r.top - inset, r.left, r.top + len, bracketPaint)
        // Top-right
        canvas.drawLine(r.right + inset, r.top, r.right - len, r.top, bracketPaint)
        canvas.drawLine(r.right, r.top - inset, r.right, r.top + len, bracketPaint)
        // Bottom-left
        canvas.drawLine(r.left - inset, r.bottom, r.left + len, r.bottom, bracketPaint)
        canvas.drawLine(r.left, r.bottom + inset, r.left, r.bottom - len, bracketPaint)
        // Bottom-right
        canvas.drawLine(r.right + inset, r.bottom, r.right - len, r.bottom, bracketPaint)
        canvas.drawLine(r.right, r.bottom + inset, r.right, r.bottom - len, bracketPaint)
    }
}

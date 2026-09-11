package com.example.routetracker.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import com.example.routetracker.Stop
import com.example.routetracker.TrackPoint
import kotlin.math.cos
import kotlin.math.max

/**
 * Draws the recorded route as a polyline scaled to fit the view,
 * with start (green), end (red) and stop (amber) markers.
 * No map tiles are used, so everything works fully offline.
 */
class RouteView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var points: List<TrackPoint> = emptyList()
    private var stops: List<Stop> = emptyList()

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1E88E5")
        style = Paint.Style.STROKE
        strokeWidth = 6f
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val startPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#43A047") }
    private val endPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E53935") }
    private val stopPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FB8C00") }

    fun setRoute(points: List<TrackPoint>, stops: List<Stop>) {
        this.points = points
        this.stops = stops
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (points.size < 2) return

        val minLat = points.minOf { it.lat }
        val maxLat = points.maxOf { it.lat }
        val minLon = points.minOf { it.lon }
        val maxLon = points.maxOf { it.lon }

        // Longitude degrees shrink with latitude; correct so shapes are not squashed.
        val midLatRad = Math.toRadians((minLat + maxLat) / 2)
        val lonScaleFix = cos(midLatRad)

        val spanLat = max(maxLat - minLat, 1e-6)
        val spanLon = max((maxLon - minLon) * lonScaleFix, 1e-6)

        val pad = 40f
        val w = width - 2 * pad
        val h = height - 2 * pad
        val scale = minOf(w / spanLon.toFloat(), h / spanLat.toFloat())
        val offsetX = pad + (w - spanLon.toFloat() * scale) / 2
        val offsetY = pad + (h - spanLat.toFloat() * scale) / 2

        fun x(lon: Double) = offsetX + ((lon - minLon) * lonScaleFix * scale).toFloat()
        fun y(lat: Double) = offsetY + ((maxLat - lat) * scale).toFloat()

        val path = Path()
        path.moveTo(x(points[0].lon), y(points[0].lat))
        for (i in 1 until points.size) path.lineTo(x(points[i].lon), y(points[i].lat))
        canvas.drawPath(path, linePaint)

        for (s in stops) canvas.drawCircle(x(s.lon), y(s.lat), 14f, stopPaint)
        canvas.drawCircle(x(points.first().lon), y(points.first().lat), 16f, startPaint)
        canvas.drawCircle(x(points.last().lon), y(points.last().lat), 16f, endPaint)
    }
}

package com.example.routetracker

import android.content.Intent
import android.graphics.Color
import android.location.Geocoder
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.routetracker.databinding.ActivityTripDetailBinding
import com.example.routetracker.db.TripStore
import com.example.routetracker.ui.TripAdapter
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class TripDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TRIP_ID = "trip_id"
    }

    private lateinit var binding: ActivityTripDetailBinding
    private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val executor = Executors.newSingleThreadExecutor()
    private var tripId = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // osmdroid setup must happen before the MapView inflates.
        Configuration.getInstance().apply {
            userAgentValue = packageName
            osmdroidBasePath = File(cacheDir, "osmdroid")
            osmdroidTileCache = File(cacheDir, "osmdroid/tiles")
        }

        binding = ActivityTripDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        tripId = intent.getLongExtra(EXTRA_TRIP_ID, -1L)
        val trip = TripStore.get(this).getTrip(tripId)
        if (trip == null) {
            finish()
            return
        }

        val points = TripStore.get(this).getPoints(tripId)
        val stops = StopDetector.detect(points)
        setUpMap(points, stops)

        title = SimpleDateFormat("EEE d MMM yyyy", Locale.getDefault()).format(Date(trip.startTime))

        val km = StopDetector.totalDistanceMeters(points) / 1000.0
        val end = trip.endTime
        binding.txtHeader.text = if (end != null) {
            getString(
                R.string.detail_header,
                timeFmt.format(Date(trip.startTime)),
                timeFmt.format(Date(end)),
                TripAdapter.formatDuration(end - trip.startTime),
                km
            )
        } else {
            getString(R.string.trip_in_progress, timeFmt.format(Date(trip.startTime)))
        }

        binding.txtTimeline.text = buildTimeline(trip, stops)
        resolveAddressesAsync(trip, stops)

        binding.btnExport.setOnClickListener { exportGpx(points) }
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
    }

    override fun onPause() {
        binding.mapView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    // ----------------------------------------------------------------- Map --

    private fun setUpMap(points: List<TrackPoint>, stops: List<Stop>) {
        val map = binding.mapView
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.minZoomLevel = 3.0

        // Let the map own drag gestures instead of the surrounding ScrollView.
        map.setOnTouchListener { v, event ->
            v.parent.requestDisallowInterceptTouchEvent(true)
            if (event.action == android.view.MotionEvent.ACTION_UP) v.performClick()
            false
        }

        if (points.size < 2) {
            map.controller.setZoom(3.0)
            return
        }

        val geoPoints = points.map { GeoPoint(it.lat, it.lon) }

        val line = Polyline(map).apply {
            setPoints(geoPoints)
            outlinePaint.color = Color.parseColor("#1E88E5")
            outlinePaint.strokeWidth = 8f
        }
        map.overlays.add(line)

        fun marker(p: GeoPoint, label: String, colorHex: String) {
            map.overlays.add(Marker(map).apply {
                position = p
                title = label
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                icon = icon?.mutate()?.apply { setTint(Color.parseColor(colorHex)) }
            })
        }

        stops.forEachIndexed { i, s ->
            marker(
                GeoPoint(s.lat, s.lon),
                getString(
                    R.string.map_stop_label,
                    i + 1,
                    timeFmt.format(Date(s.arrival)),
                    timeFmt.format(Date(s.departure)),
                    TripAdapter.formatDuration(s.durationMs)
                ),
                "#FB8C00"
            )
        }
        marker(geoPoints.first(), getString(R.string.map_start_label), "#43A047")
        marker(geoPoints.last(), getString(R.string.map_end_label), "#E53935")

        val box = BoundingBox.fromGeoPoints(geoPoints)
        map.post { map.zoomToBoundingBox(box.increaseByScale(1.3f), false) }
    }

    // ------------------------------------------------------------ Timeline --

    private fun buildTimeline(
        trip: Trip,
        stops: List<Stop>,
        names: Map<Int, String> = emptyMap()
    ): String {
        val sb = StringBuilder()
        sb.append(getString(R.string.tl_departed, timeFmt.format(Date(trip.startTime)), trip.homeSsid))
        stops.forEachIndexed { i, s ->
            val place = names[i] ?: "%.5f, %.5f".format(s.lat, s.lon)
            sb.append('\n').append(
                getString(
                    R.string.tl_stop,
                    timeFmt.format(Date(s.arrival)),
                    timeFmt.format(Date(s.departure)),
                    TripAdapter.formatDuration(s.durationMs),
                    place
                )
            )
        }
        val end = trip.endTime
        if (end != null) {
            sb.append('\n').append(getString(R.string.tl_returned, timeFmt.format(Date(end)), trip.homeSsid))
        }
        return sb.toString()
    }

    /** Best-effort reverse geocoding of stop coordinates into street addresses. */
    private fun resolveAddressesAsync(trip: Trip, stops: List<Stop>) {
        if (stops.isEmpty() || !Geocoder.isPresent()) return
        executor.execute {
            val geocoder = Geocoder(this, Locale.getDefault())
            val names = mutableMapOf<Int, String>()
            stops.forEachIndexed { i, s ->
                runCatching {
                    @Suppress("DEPRECATION")
                    val addr = geocoder.getFromLocation(s.lat, s.lon, 1)?.firstOrNull()
                    val line = addr?.getAddressLine(0)
                    if (line != null) names[i] = line
                }
            }
            if (names.isNotEmpty() && !isFinishing) {
                runOnUiThread {
                    if (!isDestroyed) binding.txtTimeline.text = buildTimeline(trip, stops, names)
                }
            }
        }
    }

    // --------------------------------------------------------------- Export --

    private fun exportGpx(points: List<TrackPoint>) {
        if (points.isEmpty()) return
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        sb.append("""<gpx version="1.1" creator="RouteTracker" xmlns="http://www.topografix.com/GPX/1/1">""")
        sb.append("\n<trk><name>Trip $tripId</name><trkseg>\n")
        for (p in points) {
            sb.append("""<trkpt lat="${p.lat}" lon="${p.lon}"><time>${fmt.format(Date(p.time))}</time></trkpt>""")
            sb.append('\n')
        }
        sb.append("</trkseg></trk></gpx>\n")

        val dir = File(cacheDir, "gpx").apply { mkdirs() }
        val file = File(dir, "trip_$tripId.gpx")
        file.writeText(sb.toString())

        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND)
                    .setType("application/gpx+xml")
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                getString(R.string.export_gpx)
            )
        )
    }
}

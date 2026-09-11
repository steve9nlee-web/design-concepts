package com.example.routetracker

import android.content.Intent
import android.location.Geocoder
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.example.routetracker.databinding.ActivityTripDetailBinding
import com.example.routetracker.db.TripStore
import com.example.routetracker.ui.TripAdapter
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
        binding.routeView.setRoute(points, stops)

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

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

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

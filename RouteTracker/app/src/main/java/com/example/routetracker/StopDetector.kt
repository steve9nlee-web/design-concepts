package com.example.routetracker

import android.location.Location

/**
 * Finds "stops" in a track: periods where consecutive points stay within
 * [radiusMeters] of the cluster's first point for at least [minDurationMs].
 */
object StopDetector {
    private const val radiusMeters = 60f
    private const val minDurationMs = 3 * 60 * 1000L

    fun detect(points: List<TrackPoint>): List<Stop> {
        if (points.size < 2) return emptyList()
        val stops = mutableListOf<Stop>()
        var clusterStart = 0
        var i = 1
        while (i <= points.size) {
            val within = i < points.size &&
                distance(points[clusterStart], points[i]) <= radiusMeters
            if (!within) {
                val duration = points[i - 1].time - points[clusterStart].time
                if (duration >= minDurationMs) {
                    val cluster = points.subList(clusterStart, i)
                    stops.add(
                        Stop(
                            lat = cluster.sumOf { it.lat } / cluster.size,
                            lon = cluster.sumOf { it.lon } / cluster.size,
                            arrival = cluster.first().time,
                            departure = cluster.last().time
                        )
                    )
                }
                clusterStart = i
            }
            i++
        }
        return stops
    }

    fun totalDistanceMeters(points: List<TrackPoint>): Double {
        var total = 0.0
        for (j in 1 until points.size) total += distance(points[j - 1], points[j])
        return total
    }

    private fun distance(a: TrackPoint, b: TrackPoint): Float {
        val result = FloatArray(1)
        Location.distanceBetween(a.lat, a.lon, b.lat, b.lon, result)
        return result[0]
    }
}

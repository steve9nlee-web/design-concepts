package com.example.routetracker

/** One recorded journey: from leaving home WiFi until reconnecting to it. */
data class Trip(
    val id: Long,
    val startTime: Long,
    val endTime: Long?, // null while still recording
    val homeSsid: String
)

/** A single GPS fix belonging to a trip. */
data class TrackPoint(
    val tripId: Long,
    val time: Long,
    val lat: Double,
    val lon: Double,
    val accuracy: Float,
    val speed: Float
)

/** A detected stay: the user remained within a small radius for a while. */
data class Stop(
    val lat: Double,
    val lon: Double,
    val arrival: Long,
    val departure: Long
) {
    val durationMs: Long get() = departure - arrival
}

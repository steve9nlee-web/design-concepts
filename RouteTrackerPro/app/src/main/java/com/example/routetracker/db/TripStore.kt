package com.example.routetracker.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.routetracker.TrackPoint
import com.example.routetracker.Trip

class TripStore private constructor(ctx: Context) :
    SQLiteOpenHelper(ctx.applicationContext, "trips.db", null, 1) {

    companion object {
        @Volatile
        private var instance: TripStore? = null

        fun get(ctx: Context): TripStore =
            instance ?: synchronized(this) {
                instance ?: TripStore(ctx).also { instance = it }
            }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE trips(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                start_time INTEGER NOT NULL,
                end_time INTEGER,
                home_ssid TEXT NOT NULL)"""
        )
        db.execSQL(
            """CREATE TABLE points(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                trip_id INTEGER NOT NULL,
                time INTEGER NOT NULL,
                lat REAL NOT NULL,
                lon REAL NOT NULL,
                accuracy REAL NOT NULL,
                speed REAL NOT NULL)"""
        )
        db.execSQL("CREATE INDEX idx_points_trip ON points(trip_id)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun startTrip(startTime: Long, homeSsid: String): Long =
        writableDatabase.insert("trips", null, ContentValues().apply {
            put("start_time", startTime)
            put("home_ssid", homeSsid)
        })

    fun endTrip(tripId: Long, endTime: Long) {
        writableDatabase.update(
            "trips",
            ContentValues().apply { put("end_time", endTime) },
            "id=?", arrayOf(tripId.toString())
        )
    }

    fun deleteTrip(tripId: Long) {
        writableDatabase.delete("points", "trip_id=?", arrayOf(tripId.toString()))
        writableDatabase.delete("trips", "id=?", arrayOf(tripId.toString()))
    }

    fun addPoint(p: TrackPoint) {
        writableDatabase.insert("points", null, ContentValues().apply {
            put("trip_id", p.tripId)
            put("time", p.time)
            put("lat", p.lat)
            put("lon", p.lon)
            put("accuracy", p.accuracy)
            put("speed", p.speed)
        })
    }

    fun pointCount(tripId: Long): Long =
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM points WHERE trip_id=?", arrayOf(tripId.toString())
        ).use { c -> if (c.moveToFirst()) c.getLong(0) else 0L }

    fun getTrip(tripId: Long): Trip? =
        readableDatabase.rawQuery(
            "SELECT id, start_time, end_time, home_ssid FROM trips WHERE id=?",
            arrayOf(tripId.toString())
        ).use { c ->
            if (c.moveToFirst()) {
                Trip(c.getLong(0), c.getLong(1), if (c.isNull(2)) null else c.getLong(2), c.getString(3))
            } else null
        }

    fun getTrips(): List<Trip> {
        val out = mutableListOf<Trip>()
        readableDatabase.rawQuery(
            "SELECT id, start_time, end_time, home_ssid FROM trips ORDER BY start_time DESC", null
        ).use { c ->
            while (c.moveToNext()) {
                out.add(Trip(c.getLong(0), c.getLong(1), if (c.isNull(2)) null else c.getLong(2), c.getString(3)))
            }
        }
        return out
    }

    fun getPoints(tripId: Long): List<TrackPoint> {
        val out = mutableListOf<TrackPoint>()
        readableDatabase.rawQuery(
            "SELECT time, lat, lon, accuracy, speed FROM points WHERE trip_id=? ORDER BY time ASC",
            arrayOf(tripId.toString())
        ).use { c ->
            while (c.moveToNext()) {
                out.add(TrackPoint(tripId, c.getLong(0), c.getDouble(1), c.getDouble(2), c.getFloat(3), c.getFloat(4)))
            }
        }
        return out
    }
}

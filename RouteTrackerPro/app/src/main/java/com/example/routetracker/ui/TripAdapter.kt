package com.example.routetracker.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.routetracker.R
import com.example.routetracker.StopDetector
import com.example.routetracker.Trip
import com.example.routetracker.databinding.ItemTripBinding
import com.example.routetracker.db.TripStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TripAdapter(
    private val onClick: (Trip) -> Unit,
    private val onLongClick: (Trip) -> Unit
) : RecyclerView.Adapter<TripAdapter.Holder>() {

    private val trips = mutableListOf<Trip>()
    private val dateFmt = SimpleDateFormat("EEE d MMM yyyy", Locale.getDefault())
    private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())

    fun submit(newTrips: List<Trip>) {
        trips.clear()
        trips.addAll(newTrips)
        notifyDataSetChanged()
    }

    class Holder(val binding: ItemTripBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemTripBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = trips.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val trip = trips[position]
        val ctx = holder.binding.root.context
        holder.binding.txtDate.text = dateFmt.format(Date(trip.startTime))

        val end = trip.endTime
        holder.binding.txtTimes.text = if (end != null) {
            ctx.getString(
                R.string.trip_times,
                timeFmt.format(Date(trip.startTime)),
                timeFmt.format(Date(end)),
                formatDuration(end - trip.startTime)
            )
        } else {
            ctx.getString(R.string.trip_in_progress, timeFmt.format(Date(trip.startTime)))
        }

        val points = TripStore.get(ctx).getPoints(trip.id)
        val stops = StopDetector.detect(points)
        val km = StopDetector.totalDistanceMeters(points) / 1000.0
        holder.binding.txtSummary.text =
            ctx.getString(R.string.trip_summary, km, stops.size)

        holder.binding.root.setOnClickListener { onClick(trip) }
        holder.binding.root.setOnLongClickListener { onLongClick(trip); true }
    }

    companion object {
        fun formatDuration(ms: Long): String {
            val totalMin = ms / 60000
            val h = totalMin / 60
            val m = totalMin % 60
            return if (h > 0) "${h}h ${m}min" else "${m}min"
        }
    }
}

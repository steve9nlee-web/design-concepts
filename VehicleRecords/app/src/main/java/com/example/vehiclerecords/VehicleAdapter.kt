package com.example.vehiclerecords

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

class VehicleAdapter(
    private val onClick: (Vehicle) -> Unit,
    private val onLongClick: (Vehicle) -> Unit
) : RecyclerView.Adapter<VehicleAdapter.Holder>() {

    private val vehicles = mutableListOf<Vehicle>()

    fun submit(items: List<Vehicle>) {
        vehicles.clear()
        vehicles.addAll(items)
        notifyDataSetChanged()
    }

    class Holder(view: ViewGroup) : RecyclerView.ViewHolder(view) {
        val plate: TextView = view.findViewById(R.id.plateText)
        val typeBadge: TextView = view.findViewById(R.id.typeBadge)
        val details: TextView = view.findViewById(R.id.detailsText)
        val roadTax: TextView = view.findViewById(R.id.roadTaxText)
        val insurance: TextView = view.findViewById(R.id.insuranceText)
        val inspection: TextView = view.findViewById(R.id.inspectionText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_vehicle, parent, false) as ViewGroup
        return Holder(view)
    }

    override fun getItemCount(): Int = vehicles.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val vehicle = vehicles[position]
        val ctx = holder.itemView.context

        holder.plate.text = vehicle.plateNo
        holder.typeBadge.text = vehicle.type
        holder.details.text = listOf(vehicle.make, vehicle.model, vehicle.color)
            .filter { it.isNotBlank() }
            .joinToString(" • ")
            .ifBlank { ctx.getString(R.string.no_details) }

        bindRenewal(holder.roadTax, ctx.getString(R.string.road_tax), vehicle.roadTax)
        bindRenewal(holder.insurance, ctx.getString(R.string.insurance), vehicle.insurance)
        bindRenewal(holder.inspection, ctx.getString(R.string.inspection), vehicle.inspection)

        holder.itemView.setOnClickListener { onClick(vehicle) }
        holder.itemView.setOnLongClickListener { onLongClick(vehicle); true }
    }

    private fun bindRenewal(view: TextView, label: String, renewal: Renewal) {
        val ctx = view.context
        val parts = mutableListOf<String>()
        parts.add(if (renewal.date.isBlank()) ctx.getString(R.string.no_date) else renewal.date)
        if (renewal.cost.isNotBlank()) {
            parts.add(ctx.getString(R.string.cost_value, renewal.cost))
        }

        val days = renewal.daysLeft()
        val (status, colorRes) = when {
            days == null -> "" to R.color.status_none
            days < 0 -> ctx.getString(R.string.overdue_by, -days) to R.color.status_overdue
            days <= 30 -> ctx.getString(R.string.days_left, days) to R.color.status_soon
            else -> ctx.getString(R.string.days_left, days) to R.color.status_ok
        }
        if (status.isNotBlank()) parts.add(status)

        view.text = ctx.getString(R.string.renewal_line, label, parts.joinToString(" • "))
        view.setTextColor(ContextCompat.getColor(ctx, colorRes))
    }
}

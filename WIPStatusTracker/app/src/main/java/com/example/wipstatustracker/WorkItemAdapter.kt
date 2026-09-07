package com.example.wipstatustracker

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WorkItemAdapter(
    private val items: MutableList<WorkItem>,
    private val onItemClick: (WorkItem) -> Unit
) : RecyclerView.Adapter<WorkItemAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: MaterialCardView = view.findViewById(R.id.cardItem)
        val statusDot: View = view.findViewById(R.id.statusDot)
        val company: TextView = view.findViewById(R.id.textCompany)
        val due: TextView = view.findViewById(R.id.textDue)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_work, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val context = holder.itemView.context

        holder.company.text = item.company

        val statusColor = ContextCompat.getColor(
            context,
            when (item.status) {
                Status.URGENT -> R.color.status_urgent
                Status.WIP -> R.color.status_wip
                Status.OK -> R.color.status_ok
            }
        )
        val backgroundColor = ContextCompat.getColor(
            context,
            when (item.status) {
                Status.URGENT -> R.color.status_urgent_bg
                Status.WIP -> R.color.status_wip_bg
                Status.OK -> R.color.status_ok_bg
            }
        )

        holder.statusDot.backgroundTintList = ColorStateList.valueOf(statusColor)
        holder.card.setCardBackgroundColor(backgroundColor)
        holder.card.strokeColor = statusColor

        val deadline = item.deadlineMillis
        when {
            deadline == null -> holder.due.visibility = View.GONE
            item.isOverdue() -> {
                holder.due.visibility = View.VISIBLE
                holder.due.text = context.getString(R.string.overdue_badge)
                holder.due.setTextColor(ContextCompat.getColor(context, R.color.status_urgent))
            }
            else -> {
                holder.due.visibility = View.VISIBLE
                holder.due.text = SimpleDateFormat("dd MMM", Locale.getDefault())
                    .format(Date(deadline))
                holder.due.setTextColor(
                    ContextCompat.getColor(
                        context,
                        if (item.isDueToday()) R.color.status_urgent else R.color.text_secondary
                    )
                )
            }
        }

        holder.card.setOnClickListener { onItemClick(item) }
    }
}

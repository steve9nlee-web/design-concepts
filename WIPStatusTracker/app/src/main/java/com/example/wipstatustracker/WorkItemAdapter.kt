package com.example.wipstatustracker

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

class WorkItemAdapter(
    private val items: MutableList<WorkItem>,
    private val onStatusChanged: (WorkItem, Status) -> Unit,
    private val onDelete: (WorkItem) -> Unit
) : RecyclerView.Adapter<WorkItemAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: MaterialCardView = view.findViewById(R.id.cardItem)
        val timestamp: TextView = view.findViewById(R.id.textTimestamp)
        val badge: TextView = view.findViewById(R.id.textStatusBadge)
        val company: TextView = view.findViewById(R.id.textCompany)
        val description: TextView = view.findViewById(R.id.textDescription)
        val urgentButton: Button = view.findViewById(R.id.buttonUrgent)
        val wipButton: Button = view.findViewById(R.id.buttonWip)
        val okButton: Button = view.findViewById(R.id.buttonOk)
        val deleteButton: Button = view.findViewById(R.id.buttonDelete)
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

        holder.timestamp.text = item.formattedTimestamp()
        holder.company.text = item.company
        holder.description.text = item.description

        val statusColor: Int
        val statusBackground: Int
        val badgeTextColor: Int
        when (item.status) {
            Status.URGENT -> {
                statusColor = ContextCompat.getColor(context, R.color.status_urgent)
                statusBackground = ContextCompat.getColor(context, R.color.status_urgent_bg)
                badgeTextColor = Color.WHITE
            }
            Status.WIP -> {
                statusColor = ContextCompat.getColor(context, R.color.status_wip)
                statusBackground = ContextCompat.getColor(context, R.color.status_wip_bg)
                badgeTextColor = Color.BLACK
            }
            Status.OK -> {
                statusColor = ContextCompat.getColor(context, R.color.status_ok)
                statusBackground = ContextCompat.getColor(context, R.color.status_ok_bg)
                badgeTextColor = Color.BLACK
            }
        }

        holder.card.setCardBackgroundColor(statusBackground)
        holder.card.strokeColor = statusColor

        holder.badge.text = context.getString(
            when (item.status) {
                Status.URGENT -> R.string.status_urgent
                Status.WIP -> R.string.status_wip
                Status.OK -> R.string.status_ok
            }
        )
        holder.badge.setBackgroundResource(R.drawable.badge_background)
        holder.badge.backgroundTintList = ColorStateList.valueOf(statusColor)
        holder.badge.setTextColor(badgeTextColor)

        styleStatusButton(holder.urgentButton, item.status == Status.URGENT, R.color.status_urgent, Color.WHITE)
        styleStatusButton(holder.wipButton, item.status == Status.WIP, R.color.status_wip, Color.BLACK)
        styleStatusButton(holder.okButton, item.status == Status.OK, R.color.status_ok, Color.BLACK)

        holder.urgentButton.setOnClickListener { onStatusChanged(item, Status.URGENT) }
        holder.wipButton.setOnClickListener { onStatusChanged(item, Status.WIP) }
        holder.okButton.setOnClickListener { onStatusChanged(item, Status.OK) }
        holder.deleteButton.setOnClickListener { onDelete(item) }
    }

    private fun styleStatusButton(button: Button, active: Boolean, activeColorRes: Int, activeTextColor: Int) {
        val context = button.context
        if (active) {
            button.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(context, activeColorRes))
            button.setTextColor(activeTextColor)
        } else {
            button.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(context, R.color.status_inactive))
            button.setTextColor(ContextCompat.getColor(context, R.color.status_inactive_text))
        }
    }
}

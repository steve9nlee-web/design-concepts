package com.ideacapture.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import java.text.DateFormat
import java.util.Date

class IdeasAdapter(
    private val onClick: (Idea) -> Unit,
    private val onLongClick: (Idea) -> Unit,
    private val onStatusClick: (Idea) -> Unit
) : RecyclerView.Adapter<IdeasAdapter.IdeaViewHolder>() {

    private val ideas = mutableListOf<Idea>()

    fun submit(newIdeas: List<Idea>) {
        ideas.clear()
        ideas.addAll(newIdeas)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): IdeaViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_idea, parent, false)
        return IdeaViewHolder(view)
    }

    override fun getItemCount(): Int = ideas.size

    override fun onBindViewHolder(holder: IdeaViewHolder, position: Int) {
        holder.bind(ideas[position])
    }

    inner class IdeaViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val title: TextView = view.findViewById(R.id.ideaTitle)
        private val description: TextView = view.findViewById(R.id.ideaDescription)
        private val statusBadge: TextView = view.findViewById(R.id.statusBadge)
        private val timestamp: TextView = view.findViewById(R.id.ideaTimestamp)

        fun bind(idea: Idea) {
            title.text = idea.title
            if (idea.description.isBlank()) {
                description.visibility = View.GONE
            } else {
                description.visibility = View.VISIBLE
                description.text = idea.description
            }
            statusBadge.text = idea.status.label
            val (bg, fg) = when (idea.status) {
                IdeaStatus.URGENT -> R.drawable.badge_urgent to R.color.badge_urgent_text
                IdeaStatus.NOT_SO_URGENT -> R.drawable.badge_not_so_urgent to R.color.badge_not_so_urgent_text
                IdeaStatus.WIP -> R.drawable.badge_wip to R.color.badge_wip_text
            }
            statusBadge.setBackgroundResource(bg)
            statusBadge.setTextColor(ContextCompat.getColor(itemView.context, fg))
            timestamp.text = DateFormat.getDateTimeInstance(
                DateFormat.MEDIUM, DateFormat.SHORT
            ).format(Date(idea.updatedAt))

            itemView.setOnClickListener { onClick(ideas[bindingAdapterPosition]) }
            itemView.setOnLongClickListener {
                onLongClick(ideas[bindingAdapterPosition])
                true
            }
            statusBadge.setOnClickListener { onStatusClick(ideas[bindingAdapterPosition]) }
        }
    }
}

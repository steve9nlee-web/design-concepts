package com.subcontracker.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class EntriesAdapter(private val entries: List<WorkEntry>) :
    RecyclerView.Adapter<EntriesAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtName: TextView = view.findViewById(R.id.txt_entry_name)
        val txtStamp: TextView = view.findViewById(R.id.txt_entry_stamp)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_entry, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = entries[position]
        holder.txtName.text = entry.name
        holder.txtStamp.text = "${entry.date}   ${entry.time} ${entry.amPm}"
    }

    override fun getItemCount() = entries.size
}

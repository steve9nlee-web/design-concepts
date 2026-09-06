package com.scamcallguard.app.ui

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.scamcallguard.app.R
import com.scamcallguard.app.databinding.ItemScreenedCallBinding
import com.scamcallguard.app.db.ScreenedCall
import com.scamcallguard.app.screening.ScamCallScreeningService

class HistoryAdapter : RecyclerView.Adapter<HistoryAdapter.Holder>() {

    private val items = mutableListOf<ScreenedCall>()

    fun submit(calls: List<ScreenedCall>) {
        items.clear()
        items.addAll(calls)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemScreenedCallBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return Holder(binding)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) =
        holder.bind(items[position])

    class Holder(private val binding: ItemScreenedCallBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(call: ScreenedCall) {
            binding.numberText.text = call.number
            binding.labelText.text = call.label
            binding.actionText.setText(
                if (call.action == ScamCallScreeningService.ACTION_BLOCKED) {
                    R.string.action_blocked
                } else {
                    R.string.action_warned
                }
            )
            binding.timeText.text = DateUtils.getRelativeTimeSpanString(
                call.timestamp, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS
            )
        }
    }
}

package com.mileageclaim.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

class HistoryAdapter : RecyclerView.Adapter<HistoryAdapter.Holder>() {

    private val claims = mutableListOf<Claim>()

    fun submit(list: List<Claim>) {
        claims.clear()
        claims.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_claim, parent, false)
        return Holder(view)
    }

    override fun getItemCount(): Int = claims.size

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(claims[position])

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        private val title: TextView = view.findViewById(R.id.claim_title)
        private val subtitle: TextView = view.findViewById(R.id.claim_subtitle)
        private val km: TextView = view.findViewById(R.id.claim_km)
        private val status: TextView = view.findViewById(R.id.claim_status)

        fun bind(claim: Claim) {
            title.text = claim.destination
            subtitle.text = String.format(
                Locale.US, "%s %s · %s", claim.date, claim.time, claim.purpose
            )
            km.text = String.format(Locale.US, "%.1f km", claim.km)
            val ctx = itemView.context
            if (claim.sent) {
                status.text = ctx.getString(R.string.status_sent)
                status.setTextColor(ContextCompat.getColor(ctx, R.color.primary))
            } else {
                status.text = ctx.getString(R.string.status_pending)
                status.setTextColor(ContextCompat.getColor(ctx, R.color.accent))
            }
        }
    }
}

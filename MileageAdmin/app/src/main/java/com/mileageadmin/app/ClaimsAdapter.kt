package com.mileageadmin.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

class ClaimsAdapter : RecyclerView.Adapter<ClaimsAdapter.Holder>() {

    private val claims = mutableListOf<AdminClaim>()

    fun submit(list: List<AdminClaim>) {
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
        private val staff: TextView = view.findViewById(R.id.claim_staff)
        private val km: TextView = view.findViewById(R.id.claim_km)
        private val destination: TextView = view.findViewById(R.id.claim_destination)
        private val purpose: TextView = view.findViewById(R.id.claim_purpose)
        private val timestamp: TextView = view.findViewById(R.id.claim_timestamp)

        fun bind(claim: AdminClaim) {
            staff.text = claim.staff
            km.text = String.format(Locale.US, "%.1f km", claim.km)
            destination.text = claim.destination
            purpose.text = claim.purpose
            timestamp.text = String.format(Locale.US, "%s %s", claim.date, claim.time)
        }
    }
}

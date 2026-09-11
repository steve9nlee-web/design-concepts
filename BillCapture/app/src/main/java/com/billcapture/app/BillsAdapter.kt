package com.billcapture.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class BillsAdapter(private val bills: List<BillEntry>) :
    RecyclerView.Adapter<BillsAdapter.BillViewHolder>() {

    class BillViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val date: TextView = view.findViewById(R.id.text_date)
        val billNo: TextView = view.findViewById(R.id.text_bill_no)
        val company: TextView = view.findViewById(R.id.text_company)
        val description: TextView = view.findViewById(R.id.text_description)
        val amount: TextView = view.findViewById(R.id.text_amount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BillViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_bill, parent, false)
        return BillViewHolder(view)
    }

    override fun onBindViewHolder(holder: BillViewHolder, position: Int) {
        val bill = bills[position]
        holder.date.text = "Date: ${bill.date}"
        holder.billNo.text = "Bill No: ${bill.billNo}"
        holder.company.text = "Company: ${bill.company}"
        holder.description.text = "Description: ${bill.description}"
        holder.amount.text = "Amount: ${bill.amount}"
    }

    override fun getItemCount(): Int = bills.size
}

package com.example.wipstatustracker

import android.app.DatePickerDialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputEditText
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var store: WorkItemStore
    private lateinit var adapter: WorkItemAdapter
    private lateinit var emptyText: TextView
    private val items = mutableListOf<WorkItem>()
    private var pendingDeadlineMillis: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        store = WorkItemStore(this)
        items.addAll(store.load())

        emptyText = findViewById(R.id.textEmpty)
        val companyInput = findViewById<TextInputEditText>(R.id.editCompany)
        val descriptionInput = findViewById<TextInputEditText>(R.id.editDescription)
        val addButton = findViewById<Button>(R.id.buttonAdd)
        val recycler = findViewById<RecyclerView>(R.id.recyclerItems)
        val deadlinePicker = findViewById<TextView>(R.id.textDeadlinePicker)
        val clearDeadlineButton = findViewById<Button>(R.id.buttonClearDeadline)

        deadlinePicker.setOnClickListener {
            val calendar = Calendar.getInstance()
            pendingDeadlineMillis?.let { calendar.timeInMillis = it }
            DatePickerDialog(
                this,
                { _, year, month, day ->
                    val picked = Calendar.getInstance().apply {
                        set(year, month, day, 12, 0, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    pendingDeadlineMillis = picked.timeInMillis
                    val formatted = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                        .format(Date(picked.timeInMillis))
                    deadlinePicker.text = getString(R.string.deadline_prefix, formatted)
                    clearDeadlineButton.visibility = View.VISIBLE
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        clearDeadlineButton.setOnClickListener {
            pendingDeadlineMillis = null
            deadlinePicker.text = getString(R.string.deadline_not_set)
            clearDeadlineButton.visibility = View.GONE
        }

        adapter = WorkItemAdapter(items) { item -> showDetailDialog(item) }
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        addButton.setOnClickListener {
            val company = companyInput.text?.toString()?.trim().orEmpty()
            val description = descriptionInput.text?.toString()?.trim().orEmpty()

            if (company.isEmpty()) {
                companyInput.error = getString(R.string.company_required)
                return@setOnClickListener
            }
            if (description.isEmpty()) {
                descriptionInput.error = getString(R.string.description_required)
                return@setOnClickListener
            }

            val item = WorkItem(
                company = company,
                description = description,
                deadlineMillis = pendingDeadlineMillis
            )
            items.add(0, item)
            store.save(items)
            adapter.notifyItemInserted(0)
            recycler.scrollToPosition(0)

            companyInput.text?.clear()
            descriptionInput.text?.clear()
            pendingDeadlineMillis = null
            deadlinePicker.text = getString(R.string.deadline_not_set)
            clearDeadlineButton.visibility = View.GONE
            companyInput.requestFocus()
            updateEmptyState()
            Toast.makeText(this, R.string.item_added, Toast.LENGTH_SHORT).show()
        }

        updateEmptyState()
    }

    private fun showDetailDialog(item: WorkItem) {
        val view = layoutInflater.inflate(R.layout.dialog_work_detail, null)
        val dialog = AlertDialog.Builder(this).setView(view).create()

        val statusBadge = view.findViewById<TextView>(R.id.textStatusBadge)
        val overdueBadge = view.findViewById<TextView>(R.id.textOverdueBadge)
        val deadlineText = view.findViewById<TextView>(R.id.textDeadline)
        val urgentButton = view.findViewById<Button>(R.id.buttonUrgent)
        val wipButton = view.findViewById<Button>(R.id.buttonWip)
        val okButton = view.findViewById<Button>(R.id.buttonOk)

        view.findViewById<TextView>(R.id.textTimestamp).text = item.formattedTimestamp()
        view.findViewById<TextView>(R.id.textCompany).text = item.company
        view.findViewById<TextView>(R.id.textDescription).text = item.description

        fun refresh() {
            val statusColor: Int
            val badgeTextColor: Int
            when (item.status) {
                Status.URGENT -> {
                    statusColor = ContextCompat.getColor(this, R.color.status_urgent)
                    badgeTextColor = Color.WHITE
                }
                Status.WIP -> {
                    statusColor = ContextCompat.getColor(this, R.color.status_wip)
                    badgeTextColor = Color.BLACK
                }
                Status.OK -> {
                    statusColor = ContextCompat.getColor(this, R.color.status_ok)
                    badgeTextColor = Color.BLACK
                }
            }
            statusBadge.text = getString(
                when (item.status) {
                    Status.URGENT -> R.string.status_urgent
                    Status.WIP -> R.string.status_wip
                    Status.OK -> R.string.status_ok
                }
            )
            statusBadge.setBackgroundResource(R.drawable.badge_background)
            statusBadge.backgroundTintList = ColorStateList.valueOf(statusColor)
            statusBadge.setTextColor(badgeTextColor)

            val formattedDeadline = item.formattedDeadline()
            if (formattedDeadline == null) {
                deadlineText.visibility = View.GONE
            } else {
                deadlineText.visibility = View.VISIBLE
                when {
                    item.isOverdue() -> {
                        deadlineText.text = getString(
                            R.string.overdue_days, formattedDeadline, item.overdueDays()
                        )
                        deadlineText.setTextColor(ContextCompat.getColor(this, R.color.status_urgent))
                    }
                    item.isDueToday() -> {
                        deadlineText.text = getString(R.string.due_today, formattedDeadline)
                        deadlineText.setTextColor(ContextCompat.getColor(this, R.color.status_urgent))
                    }
                    else -> {
                        deadlineText.text = getString(R.string.finish_by, formattedDeadline)
                        deadlineText.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
                    }
                }
            }

            if (item.isOverdue()) {
                overdueBadge.visibility = View.VISIBLE
                overdueBadge.setBackgroundResource(R.drawable.badge_background)
                overdueBadge.backgroundTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(this, R.color.status_urgent)
                )
            } else {
                overdueBadge.visibility = View.GONE
            }

            styleStatusButton(urgentButton, item.status == Status.URGENT, R.color.status_urgent, Color.WHITE)
            styleStatusButton(wipButton, item.status == Status.WIP, R.color.status_wip, Color.BLACK)
            styleStatusButton(okButton, item.status == Status.OK, R.color.status_ok, Color.BLACK)
        }

        fun setStatus(status: Status) {
            item.status = status
            store.save(items)
            val index = items.indexOfFirst { it.id == item.id }
            if (index != -1) adapter.notifyItemChanged(index)
            refresh()
        }

        urgentButton.setOnClickListener { setStatus(Status.URGENT) }
        wipButton.setOnClickListener { setStatus(Status.WIP) }
        okButton.setOnClickListener { setStatus(Status.OK) }

        view.findViewById<Button>(R.id.buttonDelete).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(item.company)
                .setMessage(R.string.delete_done)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    deleteItem(item)
                    dialog.dismiss()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        refresh()
        dialog.show()
    }

    private fun styleStatusButton(button: Button, active: Boolean, activeColorRes: Int, activeTextColor: Int) {
        if (active) {
            button.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(this, activeColorRes))
            button.setTextColor(activeTextColor)
        } else {
            button.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(this, R.color.status_inactive))
            button.setTextColor(ContextCompat.getColor(this, R.color.status_inactive_text))
        }
    }

    private fun deleteItem(item: WorkItem) {
        val index = items.indexOfFirst { it.id == item.id }
        if (index == -1) return
        items.removeAt(index)
        store.save(items)
        adapter.notifyItemRemoved(index)
        updateEmptyState()
        Toast.makeText(this, R.string.item_deleted, Toast.LENGTH_SHORT).show()
    }

    private fun updateEmptyState() {
        emptyText.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }
}

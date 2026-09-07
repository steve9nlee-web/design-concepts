package com.example.wipstatustracker

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputEditText

class MainActivity : AppCompatActivity() {

    private lateinit var store: WorkItemStore
    private lateinit var adapter: WorkItemAdapter
    private lateinit var emptyText: TextView
    private val items = mutableListOf<WorkItem>()

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

        adapter = WorkItemAdapter(
            items,
            onStatusChanged = { item, status -> updateStatus(item, status) },
            onDelete = { item -> confirmDelete(item) }
        )
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

            val item = WorkItem(company = company, description = description)
            items.add(0, item)
            store.save(items)
            adapter.notifyItemInserted(0)
            recycler.scrollToPosition(0)

            companyInput.text?.clear()
            descriptionInput.text?.clear()
            companyInput.requestFocus()
            updateEmptyState()
            Toast.makeText(this, R.string.item_added, Toast.LENGTH_SHORT).show()
        }

        updateEmptyState()
    }

    private fun updateStatus(item: WorkItem, status: Status) {
        val index = items.indexOfFirst { it.id == item.id }
        if (index == -1) return
        items[index].status = status
        store.save(items)
        adapter.notifyItemChanged(index)
    }

    private fun confirmDelete(item: WorkItem) {
        AlertDialog.Builder(this)
            .setTitle(item.company)
            .setMessage(R.string.delete_done)
            .setPositiveButton(android.R.string.ok) { _, _ -> deleteItem(item) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
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

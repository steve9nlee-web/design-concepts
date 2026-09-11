package com.billcapture.app

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HistoryActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerview_bills)
        recyclerView.layoutManager = LinearLayoutManager(this)

        findViewById<Button>(R.id.btn_back).setOnClickListener { finish() }

        lifecycleScope.launch {
            val bills = withContext(Dispatchers.IO) {
                try {
                    ExcelManager.readAll(this@HistoryActivity).reversed()
                } catch (e: Exception) {
                    emptyList()
                }
            }
            if (bills.isEmpty()) {
                Toast.makeText(
                    this@HistoryActivity,
                    "No bills captured yet",
                    Toast.LENGTH_SHORT
                ).show()
            }
            recyclerView.adapter = BillsAdapter(bills)
        }
    }
}

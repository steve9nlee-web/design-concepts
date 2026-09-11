package com.subcontracker.app

import android.os.Bundle
import android.widget.TextView
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

        val list = findViewById<RecyclerView>(R.id.list_entries)
        val empty = findViewById<TextView>(R.id.txt_empty)
        list.layoutManager = LinearLayoutManager(this)

        lifecycleScope.launch(Dispatchers.IO) {
            val entries = ExcelManager.readAll(this@HistoryActivity).reversed()
            withContext(Dispatchers.Main) {
                if (entries.isEmpty()) {
                    empty.visibility = TextView.VISIBLE
                } else {
                    list.adapter = EntriesAdapter(entries)
                }
            }
        }
    }
}

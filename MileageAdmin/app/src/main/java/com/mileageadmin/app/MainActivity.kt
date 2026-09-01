package com.mileageadmin.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var swipe: SwipeRefreshLayout
    private lateinit var summary: TextView
    private lateinit var errorBanner: TextView
    private lateinit var emptyText: TextView

    private val adapter = ClaimsAdapter()
    private var xlsxUrl: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = Prefs(this)
        swipe = findViewById(R.id.swipe_refresh)
        summary = findViewById(R.id.summary)
        errorBanner = findViewById(R.id.error_banner)
        emptyText = findViewById(R.id.empty_text)

        val list = findViewById<RecyclerView>(R.id.claims_list)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter

        swipe.setOnRefreshListener { refresh() }
        findViewById<TextView>(R.id.settings_button).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<TextView>(R.id.open_excel_button).setOnClickListener { openExcel() }
    }

    override fun onResume() {
        super.onResume()
        if (prefs.isConfigured()) {
            refresh()
        } else {
            errorBanner.text = getString(R.string.error_not_configured)
            errorBanner.visibility = TextView.VISIBLE
        }
    }

    private fun refresh() {
        if (!prefs.isConfigured()) {
            swipe.isRefreshing = false
            startActivity(Intent(this, SettingsActivity::class.java))
            return
        }
        swipe.isRefreshing = true
        lifecycleScope.launch {
            val result = ApiClient.fetchClaims(prefs.serverUrl, prefs.secret)
            swipe.isRefreshing = false
            result.onSuccess { response ->
                errorBanner.visibility = TextView.GONE
                xlsxUrl = response.xlsxUrl
                adapter.submit(response.claims)
                emptyText.visibility =
                    if (response.claims.isEmpty()) TextView.VISIBLE else TextView.GONE
                summary.text = getString(
                    R.string.summary,
                    response.count,
                    String.format(Locale.US, "%.1f", response.totalKm)
                )
            }.onFailure {
                errorBanner.text = getString(R.string.error_fetch, it.message)
                errorBanner.visibility = TextView.VISIBLE
            }
        }
    }

    private fun openExcel() {
        if (xlsxUrl.isBlank()) {
            Toast.makeText(this, R.string.error_no_xlsx, Toast.LENGTH_LONG).show()
            return
        }
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(xlsxUrl)))
    }
}

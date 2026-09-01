package com.mileageclaim.app

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var store: ClaimStore

    private lateinit var staffField: EditText
    private lateinit var dateField: TextView
    private lateinit var timeField: TextView
    private lateinit var destinationField: EditText
    private lateinit var purposeField: EditText
    private lateinit var kmField: EditText
    private lateinit var submitButton: Button
    private lateinit var syncButton: Button
    private lateinit var historyList: RecyclerView
    private lateinit var historyEmpty: TextView

    private val adapter = HistoryAdapter()
    private val stamp: Calendar = Calendar.getInstance()
    private var syncing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = Prefs(this)
        store = ClaimStore(prefs)

        staffField = findViewById(R.id.staff_field)
        dateField = findViewById(R.id.date_field)
        timeField = findViewById(R.id.time_field)
        destinationField = findViewById(R.id.destination_field)
        purposeField = findViewById(R.id.purpose_field)
        kmField = findViewById(R.id.km_field)
        submitButton = findViewById(R.id.submit_button)
        syncButton = findViewById(R.id.sync_button)
        historyList = findViewById(R.id.history_list)
        historyEmpty = findViewById(R.id.history_empty)

        historyList.layoutManager = LinearLayoutManager(this)
        historyList.isNestedScrollingEnabled = false
        historyList.adapter = adapter

        staffField.setText(prefs.staffName)
        stampNow()

        dateField.setOnClickListener { pickDate() }
        timeField.setOnClickListener { pickTime() }
        submitButton.setOnClickListener { submit() }
        syncButton.setOnClickListener { syncPending(showResult = true) }
        findViewById<TextView>(R.id.settings_button).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        refreshHistory()
        if (prefs.isConfigured() && store.pending().isNotEmpty()) syncPending(showResult = false)
    }

    /** Stamp the form with the current date and time. */
    private fun stampNow() {
        stamp.timeInMillis = System.currentTimeMillis()
        renderStamp()
    }

    private fun renderStamp() {
        dateField.text = String.format(
            Locale.US, "%04d-%02d-%02d",
            stamp.get(Calendar.YEAR), stamp.get(Calendar.MONTH) + 1, stamp.get(Calendar.DAY_OF_MONTH)
        )
        timeField.text = String.format(
            Locale.US, "%02d:%02d",
            stamp.get(Calendar.HOUR_OF_DAY), stamp.get(Calendar.MINUTE)
        )
    }

    private fun pickDate() {
        DatePickerDialog(
            this,
            { _, y, m, d ->
                stamp.set(y, m, d)
                renderStamp()
            },
            stamp.get(Calendar.YEAR), stamp.get(Calendar.MONTH), stamp.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun pickTime() {
        TimePickerDialog(
            this,
            { _, h, min ->
                stamp.set(Calendar.HOUR_OF_DAY, h)
                stamp.set(Calendar.MINUTE, min)
                renderStamp()
            },
            stamp.get(Calendar.HOUR_OF_DAY), stamp.get(Calendar.MINUTE), true
        ).show()
    }

    private fun submit() {
        val staff = staffField.text.toString().trim()
        val destination = destinationField.text.toString().trim()
        val purpose = purposeField.text.toString().trim()
        val km = kmField.text.toString().trim().toDoubleOrNull()

        when {
            staff.isEmpty() -> return staffField.errorAndFocus(getString(R.string.error_staff))
            destination.isEmpty() -> return destinationField.errorAndFocus(getString(R.string.error_destination))
            purpose.isEmpty() -> return purposeField.errorAndFocus(getString(R.string.error_purpose))
            km == null || km <= 0.0 -> return kmField.errorAndFocus(getString(R.string.error_km))
        }
        if (!prefs.isConfigured()) {
            Toast.makeText(this, R.string.error_not_configured, Toast.LENGTH_LONG).show()
            startActivity(Intent(this, SettingsActivity::class.java))
            return
        }

        prefs.staffName = staff
        val claim = Claim(
            date = dateField.text.toString(),
            time = timeField.text.toString(),
            staff = staff,
            destination = destination,
            purpose = purpose,
            km = km!!,
        )
        store.add(claim)
        destinationField.text.clear()
        purposeField.text.clear()
        kmField.text.clear()
        stampNow()
        refreshHistory()
        syncPending(showResult = true)
    }

    /** Send every pending claim; claims that fail stay pending for the next try. */
    private fun syncPending(showResult: Boolean) {
        if (syncing) return
        val pending = store.pending()
        if (pending.isEmpty()) return
        syncing = true
        submitButton.isEnabled = false
        lifecycleScope.launch {
            var sent = 0
            var lastError: String? = null
            for (claim in pending) {
                ApiClient.submit(prefs.serverUrl, prefs.secret, claim)
                    .onSuccess {
                        store.markSent(claim.id)
                        sent++
                    }
                    .onFailure { lastError = it.message }
            }
            syncing = false
            submitButton.isEnabled = true
            refreshHistory()
            if (showResult) {
                val msg = when {
                    lastError == null -> getString(R.string.sync_ok, sent)
                    sent > 0 -> getString(R.string.sync_partial, sent, lastError)
                    else -> getString(R.string.sync_failed, lastError)
                }
                Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun refreshHistory() {
        val claims = store.load()
        adapter.submit(claims)
        historyEmpty.visibility = if (claims.isEmpty()) TextView.VISIBLE else TextView.GONE
        val pendingCount = claims.count { !it.sent }
        syncButton.visibility = if (pendingCount > 0) Button.VISIBLE else Button.GONE
        syncButton.text = getString(R.string.sync_pending, pendingCount)
    }

    private fun EditText.errorAndFocus(message: String) {
        error = message
        requestFocus()
    }
}

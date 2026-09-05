package com.triplogger.app

import android.app.TimePickerDialog
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    companion object {
        private const val PREFS = "trip_logger_prefs"
        private const val KEY_DRIVERS = "extra_drivers"
        private const val KEY_URL = "webapp_url"
        private const val KEY_QUEUE = "pending_queue"
        private val DEFAULT_DRIVERS = listOf("Arthur", "Ah Huat", "Alex", "Adrian")
        private val TRIP_TIMES = listOf(
            "AM 7am",
            "PM 7pm",
            "OT 4pm",
            "OT 8pm",
            "OT 9pm",
            "Key in time…"
        )
        private val COMPANIES = listOf("Sumtec", "Hacks")
    }

    private lateinit var txtDateTime: TextView
    private lateinit var txtPending: TextView
    private lateinit var editCustomTime: EditText
    private lateinit var spinnerTripTime: Spinner
    private lateinit var spinnerCompany: Spinner
    private lateinit var spinnerDriver: Spinner
    private lateinit var editDescription: EditText
    private lateinit var btnSave: Button

    private val clockHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private val clockFormat = SimpleDateFormat("EEE, dd MMM yyyy  hh:mm:ss a", Locale.US)
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.US)
    private val timeFormat = SimpleDateFormat("hh:mm a", Locale.US)

    private val clockTick = object : Runnable {
        override fun run() {
            txtDateTime.text = clockFormat.format(Date())
            clockHandler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        txtDateTime = findViewById(R.id.txtDateTime)
        txtPending = findViewById(R.id.txtPending)
        editCustomTime = findViewById(R.id.editCustomTime)
        spinnerTripTime = findViewById(R.id.spinnerTripTime)
        spinnerCompany = findViewById(R.id.spinnerCompany)
        spinnerDriver = findViewById(R.id.spinnerDriver)
        editDescription = findViewById(R.id.editDescription)
        btnSave = findViewById(R.id.btnSave)

        spinnerTripTime.adapter = simpleAdapter(TRIP_TIMES)
        spinnerCompany.adapter = simpleAdapter(COMPANIES)
        refreshDriverSpinner()

        spinnerTripTime.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                editCustomTime.visibility =
                    if (pos == TRIP_TIMES.size - 1) View.VISIBLE else View.GONE
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        editCustomTime.setOnClickListener { pickCustomTime() }
        findViewById<Button>(R.id.btnAddDriver).setOnClickListener { showAddDriverDialog() }
        findViewById<Button>(R.id.btnSettings).setOnClickListener { showSettingsDialog() }
        btnSave.setOnClickListener { onSave() }

        updatePendingLabel()
        flushQueue()
    }

    override fun onResume() {
        super.onResume()
        clockHandler.post(clockTick)
    }

    override fun onPause() {
        super.onPause()
        clockHandler.removeCallbacks(clockTick)
    }

    private fun simpleAdapter(items: List<String>): ArrayAdapter<String> {
        val a = ArrayAdapter(this, android.R.layout.simple_spinner_item, items)
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        return a
    }

    private fun prefs() = getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun allDrivers(): List<String> {
        val extra = prefs().getString(KEY_DRIVERS, "") ?: ""
        val extras = extra.split("|").map { it.trim() }.filter { it.isNotEmpty() }
        return DEFAULT_DRIVERS + extras
    }

    private fun refreshDriverSpinner(selectName: String? = null) {
        val drivers = allDrivers()
        spinnerDriver.adapter = simpleAdapter(drivers)
        selectName?.let {
            val i = drivers.indexOf(it)
            if (i >= 0) spinnerDriver.setSelection(i)
        }
    }

    private fun showAddDriverDialog() {
        val input = EditText(this)
        input.hint = "Driver name"
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        AlertDialog.Builder(this)
            .setTitle("Add driver")
            .setView(input)
            .setPositiveButton("Add") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty()) return@setPositiveButton
                if (allDrivers().any { it.equals(name, ignoreCase = true) }) {
                    Toast.makeText(this, "$name is already in the list", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val extra = prefs().getString(KEY_DRIVERS, "") ?: ""
                val updated = if (extra.isEmpty()) name else "$extra|$name"
                prefs().edit().putString(KEY_DRIVERS, updated).apply()
                refreshDriverSpinner(name)
                Toast.makeText(this, "Driver $name added", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun pickCustomTime() {
        val now = Calendar.getInstance()
        TimePickerDialog(this, { _, hour, minute ->
            val c = Calendar.getInstance()
            c.set(Calendar.HOUR_OF_DAY, hour)
            c.set(Calendar.MINUTE, minute)
            editCustomTime.setText(timeFormat.format(c.time))
        }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), false).show()
    }

    private fun showSettingsDialog() {
        val input = EditText(this)
        input.hint = "https://script.google.com/macros/s/…/exec"
        input.setText(prefs().getString(KEY_URL, "") ?: "")
        input.inputType = InputType.TYPE_TEXT_VARIATION_URI
        AlertDialog.Builder(this)
            .setTitle("Google Sheet Web App URL")
            .setMessage("Paste the Apps Script Web App URL that saves rows to your Google Sheet (see the setup guide), then tap Save & Test.")
            .setView(input)
            .setPositiveButton("Save & Test") { _, _ ->
                val url = input.text.toString().trim()
                prefs().edit().putString(KEY_URL, url).apply()
                testConnection(url)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun testConnection(url: String) {
        if (url.isEmpty()) {
            Toast.makeText(this, "URL is empty", Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(this, "Testing…", Toast.LENGTH_SHORT).show()
        executor.execute {
            val result: String = try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 15000
                conn.readTimeout = 15000
                conn.instanceFollowRedirects = true
                val body = readBody(conn)
                when {
                    body.contains("Trip Logger web app is running") ->
                        "✓ Connected!\n\nThe app can reach your Google Sheet script. Saves will now appear in the spreadsheet."
                    body.contains("accounts.google.com") || body.contains("ServiceLogin") ->
                        "✗ Google is asking for a login.\n\nIn Apps Script: Deploy → Manage deployments → edit → set 'Who has access' to 'Anyone' → Deploy, then paste the NEW URL here."
                    body.contains("Sorry, unable to open the file") || body.contains("Page not found") ->
                        "✗ This URL does not point to a working web app deployment.\n\nIn Apps Script use Deploy → New deployment → Web app, and copy the URL ending in /exec."
                    else ->
                        "✗ The URL responded but not with the Trip Logger script.\n\nMake sure you pasted the whole Code.gs into Apps Script and copied the Web app URL ending in /exec."
                }
            } catch (e: Exception) {
                "✗ Could not reach the URL.\n\nCheck the URL is complete and the phone has internet. (${e.javaClass.simpleName})"
            }
            runOnUiThread {
                AlertDialog.Builder(this)
                    .setTitle("Connection test")
                    .setMessage(result)
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }

    private fun readBody(conn: HttpURLConnection): String {
        return try {
            val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
            stream?.bufferedReader()?.use { it.readText() } ?: ""
        } catch (e: Exception) {
            ""
        } finally {
            conn.disconnect()
        }
    }

    private fun onSave() {
        val url = prefs().getString(KEY_URL, "") ?: ""
        if (url.isEmpty()) {
            Toast.makeText(this, "Set the Web App URL first (Settings)", Toast.LENGTH_LONG).show()
            showSettingsDialog()
            return
        }

        val tripPos = spinnerTripTime.selectedItemPosition
        val tripTime = if (tripPos == TRIP_TIMES.size - 1) {
            val t = editCustomTime.text.toString().trim()
            if (t.isEmpty()) {
                Toast.makeText(this, "Please key in the time", Toast.LENGTH_SHORT).show()
                return
            }
            "Key in: $t"
        } else {
            TRIP_TIMES[tripPos]
        }

        val now = Date()
        val entry = JSONObject().apply {
            put("date", dateFormat.format(now))
            put("time", timeFormat.format(now))
            put("tripTime", tripTime)
            put("company", spinnerCompany.selectedItem.toString())
            put("driver", spinnerDriver.selectedItem.toString())
            put("description", editDescription.text.toString().trim())
        }

        btnSave.isEnabled = false
        executor.execute {
            val ok = postEntry(url, entry)
            runOnUiThread {
                btnSave.isEnabled = true
                if (ok) {
                    Toast.makeText(this, "Saved to Google Sheet ✓", Toast.LENGTH_SHORT).show()
                    editDescription.setText("")
                    editCustomTime.setText("")
                    flushQueue()
                } else {
                    enqueue(entry)
                    Toast.makeText(
                        this,
                        "No connection — entry saved on phone, will retry",
                        Toast.LENGTH_LONG
                    ).show()
                }
                updatePendingLabel()
            }
        }
    }

    private fun postEntry(url: String, entry: JSONObject): Boolean {
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(entry.toString()) }
            // Only count it saved when the script itself confirms — a login page
            // or error page must not look like success.
            readBody(conn).contains("\"ok\":true")
        } catch (e: Exception) {
            false
        }
    }

    private fun enqueue(entry: JSONObject) {
        val arr = JSONArray(prefs().getString(KEY_QUEUE, "[]"))
        arr.put(entry)
        prefs().edit().putString(KEY_QUEUE, arr.toString()).apply()
    }

    private fun flushQueue() {
        val url = prefs().getString(KEY_URL, "") ?: return
        if (url.isEmpty()) return
        val arr = JSONArray(prefs().getString(KEY_QUEUE, "[]"))
        if (arr.length() == 0) return
        executor.execute {
            val remaining = JSONArray()
            var sent = 0
            for (i in 0 until arr.length()) {
                val e = arr.getJSONObject(i)
                if (postEntry(url, e)) sent++ else remaining.put(e)
            }
            prefs().edit().putString(KEY_QUEUE, remaining.toString()).apply()
            if (sent > 0) {
                runOnUiThread {
                    Toast.makeText(this, "$sent pending entr${if (sent == 1) "y" else "ies"} uploaded", Toast.LENGTH_SHORT).show()
                    updatePendingLabel()
                }
            }
        }
    }

    private fun updatePendingLabel() {
        val n = JSONArray(prefs().getString(KEY_QUEUE, "[]")).length()
        if (n > 0) {
            txtPending.visibility = View.VISIBLE
            txtPending.text = "$n entr${if (n == 1) "y" else "ies"} waiting to upload"
        } else {
            txtPending.visibility = View.GONE
        }
    }
}

package com.subcontracker.app

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var btnDrive: Button
    private lateinit var inputName: TextInputEditText
    private lateinit var txtClock: TextView
    private lateinit var txtStatus: TextView
    private lateinit var radioAm: RadioButton
    private lateinit var radioPm: RadioButton

    private val clockHandler = Handler(Looper.getMainLooper())
    private var userChoseAmPm = false

    private val clockFormat = SimpleDateFormat("EEE, dd MMM yyyy   hh:mm:ss a", Locale.US)
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.US)
    private val timeFormat = SimpleDateFormat("hh:mm:ss", Locale.US)
    private val amPmFormat = SimpleDateFormat("a", Locale.US)

    private val clockTick = object : Runnable {
        override fun run() {
            val now = Date()
            txtClock.text = clockFormat.format(now)
            if (!userChoseAmPm) {
                val isAm = Calendar.getInstance().get(Calendar.AM_PM) == Calendar.AM
                radioAm.isChecked = isAm
                radioPm.isChecked = !isAm
            }
            clockHandler.postDelayed(this, 1000)
        }
    }

    private val signInLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                updateSignInButton()
                Toast.makeText(this, "Signed in as ${account.email}", Toast.LENGTH_LONG).show()
            } catch (e: ApiException) {
                Toast.makeText(
                    this,
                    "Sign-in failed (code ${e.statusCode}). Check OAuth SHA-1 setup.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnDrive = findViewById(R.id.btn_drive)
        inputName = findViewById(R.id.input_name)
        txtClock = findViewById(R.id.txt_clock)
        txtStatus = findViewById(R.id.txt_status)
        radioAm = findViewById(R.id.radio_am)
        radioPm = findViewById(R.id.radio_pm)

        inputName.setText(prefs().getString(KEY_LAST_NAME, ""))

        radioAm.setOnClickListener { userChoseAmPm = true }
        radioPm.setOnClickListener { userChoseAmPm = true }

        btnDrive.setOnClickListener {
            signInLauncher.launch(SessionManager.buildSignInClient(this).signInIntent)
        }

        findViewById<Button>(R.id.btn_record).setOnClickListener { recordWorkDay() }

        findViewById<Button>(R.id.btn_view_history).setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }

        updateSignInButton()
    }

    override fun onResume() {
        super.onResume()
        clockHandler.post(clockTick)
    }

    override fun onPause() {
        super.onPause()
        clockHandler.removeCallbacks(clockTick)
    }

    private fun recordWorkDay() {
        val name = inputName.text?.toString()?.trim().orEmpty()
        if (name.isEmpty()) {
            inputName.error = getString(R.string.error_name_required)
            return
        }
        prefs().edit().putString(KEY_LAST_NAME, name).apply()

        val now = Date()
        val entry = WorkEntry(
            name = name,
            date = dateFormat.format(now),
            time = timeFormat.format(now),
            amPm = if (radioAm.isChecked) "AM" else "PM"
        )
        userChoseAmPm = false
        txtStatus.text = getString(R.string.status_saving)

        lifecycleScope.launch(Dispatchers.IO) {
            var message: String
            try {
                ExcelManager.append(this@MainActivity, entry)
                message = getString(R.string.status_saved, entry.name, entry.time, entry.amPm)

                val account = SessionManager.signedInAccount(this@MainActivity)
                message += if (account != null) {
                    try {
                        DriveUploader.upload(
                            this@MainActivity, account, ExcelManager.masterFile(this@MainActivity)
                        )
                        "\n" + getString(R.string.status_drive_ok)
                    } catch (e: Exception) {
                        "\n" + getString(R.string.status_drive_failed, e.message ?: "")
                    }
                } else {
                    "\n" + getString(R.string.status_drive_not_signed_in)
                }
            } catch (e: Exception) {
                message = getString(R.string.status_save_failed, e.message ?: "")
            }
            withContext(Dispatchers.Main) {
                txtStatus.text = message
            }
        }
    }

    private fun updateSignInButton() {
        val account = SessionManager.signedInAccount(this)
        btnDrive.text =
            if (account != null) "Drive: ${account.email}"
            else getString(R.string.connect_google_drive)
    }

    private fun prefs() = getSharedPreferences("subcontracker", MODE_PRIVATE)

    companion object {
        private const val KEY_LAST_NAME = "last_name"
    }
}

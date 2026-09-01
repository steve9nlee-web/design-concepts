package com.mileageadmin.app

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var urlField: EditText
    private lateinit var secretField: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        prefs = Prefs(this)
        urlField = findViewById(R.id.url_field)
        secretField = findViewById(R.id.secret_field)
        urlField.setText(prefs.serverUrl)
        secretField.setText(prefs.secret)

        findViewById<Button>(R.id.save_button).setOnClickListener {
            save()
            Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show()
            finish()
        }
        findViewById<Button>(R.id.test_button).setOnClickListener { testConnection() }
        findViewById<TextView>(R.id.back_button).setOnClickListener { finish() }
    }

    private fun save() {
        prefs.serverUrl = urlField.text.toString()
        prefs.secret = secretField.text.toString()
    }

    private fun testConnection() {
        save()
        if (!prefs.isConfigured()) {
            Toast.makeText(this, R.string.error_not_configured, Toast.LENGTH_LONG).show()
            return
        }
        val button = findViewById<Button>(R.id.test_button)
        button.isEnabled = false
        lifecycleScope.launch {
            val result = ApiClient.ping(prefs.serverUrl, prefs.secret)
            button.isEnabled = true
            val msg = result.fold(
                onSuccess = { getString(R.string.test_ok) },
                onFailure = { getString(R.string.test_failed, it.message) },
            )
            Toast.makeText(this@SettingsActivity, msg, Toast.LENGTH_LONG).show()
        }
    }
}

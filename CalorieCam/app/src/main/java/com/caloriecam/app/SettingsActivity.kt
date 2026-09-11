package com.caloriecam.app

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.caloriecam.app.databinding.ActivitySettingsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var profile: UserProfile

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        profile = UserProfile(this)

        binding.inputApiKey.setText(profile.apiKey)
        binding.inputSheetUrl.setText(profile.sheetUrl)

        binding.buttonSave.setOnClickListener {
            saveFields()
            Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
            finish()
        }

        binding.buttonTestSheet.setOnClickListener {
            saveFields()
            testSheetConnection()
        }
    }

    private fun saveFields() {
        profile.apiKey = binding.inputApiKey.text.toString()
        profile.sheetUrl = binding.inputSheetUrl.text.toString()
    }

    private fun testSheetConnection() {
        binding.buttonTestSheet.isEnabled = false
        binding.buttonTestSheet.text = "Testing…"
        lifecycleScope.launch(Dispatchers.IO) {
            val result = SheetSync.testConnection(profile)
            withContext(Dispatchers.Main) {
                binding.buttonTestSheet.isEnabled = true
                binding.buttonTestSheet.text = "Test sheet connection"
                AlertDialog.Builder(this@SettingsActivity)
                    .setTitle(if (result.ok) "Connection OK ✔" else "Connection failed")
                    .setMessage(
                        if (result.ok)
                            "A test row was added to your Google Sheet. " +
                            "Open the sheet and check the last row (you can delete it)."
                        else result.message
                    )
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }
}

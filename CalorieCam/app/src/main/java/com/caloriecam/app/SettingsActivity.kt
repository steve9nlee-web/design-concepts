package com.caloriecam.app

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.caloriecam.app.databinding.ActivitySettingsBinding

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
            profile.apiKey = binding.inputApiKey.text.toString()
            profile.sheetUrl = binding.inputSheetUrl.text.toString()
            Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}

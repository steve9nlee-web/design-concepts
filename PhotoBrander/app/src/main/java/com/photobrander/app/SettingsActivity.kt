package com.photobrander.app

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.photobrander.app.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var settings: AppSettings

    private val pickLogoLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            if (settings.saveLogo(uri)) {
                showLogoPreview()
                Toast.makeText(this, R.string.logo_saved, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, R.string.logo_save_failed, Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        settings = AppSettings(this)

        binding.editWebhookUrl.setText(settings.webhookUrl)
        binding.editCompanyName.setText(settings.companyName)
        binding.editCompanyDetails.setText(settings.companyDetails)
        showLogoPreview()

        binding.buttonPickLogo.setOnClickListener {
            pickLogoLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
        binding.buttonSaveSettings.setOnClickListener { save() }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (item.itemId == android.R.id.home) {
            finish()
            true
        } else {
            super.onOptionsItemSelected(item)
        }
    }

    private fun save() {
        val url = binding.editWebhookUrl.text?.toString()?.trim().orEmpty()
        if (url.isNotEmpty() && !url.startsWith("http://") && !url.startsWith("https://")) {
            Toast.makeText(this, R.string.error_invalid_url, Toast.LENGTH_LONG).show()
            return
        }
        settings.webhookUrl = url
        settings.companyName = binding.editCompanyName.text?.toString().orEmpty()
        settings.companyDetails = binding.editCompanyDetails.text?.toString().orEmpty()
        Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun showLogoPreview() {
        val logo = settings.loadLogo()
        if (logo != null) {
            binding.imageLogoPreview.setImageBitmap(logo)
            binding.imageLogoPreview.visibility = View.VISIBLE
        } else {
            binding.imageLogoPreview.visibility = View.GONE
        }
    }
}

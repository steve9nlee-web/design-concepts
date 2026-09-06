package com.scamcallguard.app.ui

import android.app.role.RoleManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.scamcallguard.app.R
import com.scamcallguard.app.databinding.ActivityMainBinding
import com.scamcallguard.app.databinding.DialogAddNumberBinding
import com.scamcallguard.app.db.BlockEntry
import com.scamcallguard.app.db.BlocklistDb
import com.scamcallguard.app.db.PhoneNumbers
import com.scamcallguard.app.screening.ScamCallScreeningService
import com.scamcallguard.app.sync.BlocklistUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var historyAdapter: HistoryAdapter

    private val roleRequest =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            refreshStatus()
        }

    private val notificationPermissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        historyAdapter = HistoryAdapter()
        binding.historyList.layoutManager = LinearLayoutManager(this)
        binding.historyList.adapter = historyAdapter

        binding.enableButton.setOnClickListener { requestScreeningRole() }
        binding.blockModeSwitch.setOnCheckedChangeListener { _, checked ->
            prefs().edit().putBoolean(ScamCallScreeningService.KEY_BLOCK_MODE, checked).apply()
            binding.blockModeSwitch.setText(
                if (checked) R.string.mode_block else R.string.mode_warn
            )
        }
        binding.lookupButton.setOnClickListener { lookupNumber() }
        binding.addNumberButton.setOnClickListener { showAddNumberDialog() }
        binding.updateFeedButton.setOnClickListener { updateFromFeed() }

        if (Build.VERSION.SDK_INT >= 33) {
            notificationPermissionRequest.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        refreshHistory()
    }

    private fun prefs() =
        getSharedPreferences(ScamCallScreeningService.PREFS, MODE_PRIVATE)

    private fun requestScreeningRole() {
        val roleManager = getSystemService(RoleManager::class.java)
        if (!roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
            Toast.makeText(this, R.string.role_unavailable, Toast.LENGTH_LONG).show()
            return
        }
        roleRequest.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
    }

    private fun refreshStatus() {
        val roleManager = getSystemService(RoleManager::class.java)
        val active = roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
        binding.statusText.setText(
            if (active) R.string.status_active else R.string.status_inactive
        )
        binding.enableButton.isEnabled = !active
        binding.blockModeSwitch.isChecked =
            prefs().getBoolean(ScamCallScreeningService.KEY_BLOCK_MODE, true)

        lifecycleScope.launch {
            val count = withContext(Dispatchers.IO) { BlocklistDb.get(this@MainActivity).blocklistCount() }
            binding.dbCountText.text = getString(R.string.db_count, count)
        }
    }

    private fun refreshHistory() {
        lifecycleScope.launch {
            val calls = withContext(Dispatchers.IO) { BlocklistDb.get(this@MainActivity).recentCalls() }
            historyAdapter.submit(calls)
            binding.historyEmptyText.visibility =
                if (calls.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        }
    }

    private fun lookupNumber() {
        val input = binding.lookupInput.text?.toString().orEmpty()
        val normalized = PhoneNumbers.normalize(input)
        if (normalized == null) {
            binding.lookupResult.setText(R.string.lookup_invalid)
            return
        }
        lifecycleScope.launch {
            val entry = withContext(Dispatchers.IO) { BlocklistDb.get(this@MainActivity).lookup(input) }
            binding.lookupResult.text = if (entry != null) {
                getString(R.string.lookup_hit, entry.label, entry.category)
            } else {
                getString(R.string.lookup_clean)
            }
        }
    }

    private fun showAddNumberDialog() {
        val dialogBinding = DialogAddNumberBinding.inflate(layoutInflater)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.add_number_title)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.add) { _, _ ->
                val number = dialogBinding.numberInput.text?.toString().orEmpty()
                val label = dialogBinding.labelInput.text?.toString().orEmpty()
                    .ifBlank { getString(R.string.default_user_label) }
                lifecycleScope.launch {
                    val added = withContext(Dispatchers.IO) {
                        BlocklistDb.get(this@MainActivity).upsert(
                            BlockEntry(
                                number = number,
                                label = label,
                                category = "user_reported",
                                source = "user",
                                addedAt = System.currentTimeMillis()
                            )
                        )
                    }
                    if (added) {
                        Toast.makeText(this@MainActivity, R.string.number_added, Toast.LENGTH_SHORT).show()
                        refreshStatus()
                    } else {
                        Toast.makeText(this@MainActivity, R.string.lookup_invalid, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun updateFromFeed() {
        val feedUrl = binding.feedUrlInput.text?.toString().orEmpty().trim()
        if (feedUrl.isEmpty()) {
            Toast.makeText(this, R.string.feed_url_missing, Toast.LENGTH_SHORT).show()
            return
        }
        binding.updateFeedButton.isEnabled = false
        lifecycleScope.launch {
            val result = BlocklistUpdater.update(this@MainActivity, feedUrl)
            binding.updateFeedButton.isEnabled = true
            when (result) {
                is BlocklistUpdater.Result.Success -> {
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.feed_updated, result.imported),
                        Toast.LENGTH_LONG
                    ).show()
                    refreshStatus()
                }
                is BlocklistUpdater.Result.Failure -> {
                    Toast.makeText(this@MainActivity, result.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}

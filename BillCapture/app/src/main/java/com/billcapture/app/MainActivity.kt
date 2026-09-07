package com.billcapture.app

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var btnSelectCloud: Button

    // Upload a bill photo and let OCR try to fill the A–H fields first;
    // the review screen still offers manual entry if it can't read the bill.
    private val uploadScanLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri == null) return@registerForActivityResult
            lifecycleScope.launch {
                val path = withContext(Dispatchers.IO) {
                    BillRepository.copyToCache(this@MainActivity, uri)
                }
                if (path == null) {
                    Toast.makeText(
                        this@MainActivity, R.string.photo_load_failed, Toast.LENGTH_LONG
                    ).show()
                } else {
                    startActivity(
                        Intent(this@MainActivity, ProcessingActivity::class.java)
                            .putExtra(ProcessingActivity.EXTRA_PHOTO_PATH, path)
                    )
                }
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

        btnSelectCloud = findViewById(R.id.btn_select_cloud)
        btnSelectCloud.setOnClickListener {
            signInLauncher.launch(SessionManager.buildSignInClient(this).signInIntent)
        }

        findViewById<Button>(R.id.btn_upload_scan).setOnClickListener {
            uploadScanLauncher.launch("image/*")
        }

        findViewById<Button>(R.id.btn_start_capture).setOnClickListener {
            startActivity(Intent(this, CameraActivity::class.java))
        }

        findViewById<Button>(R.id.btn_manual_upload).setOnClickListener {
            startActivity(Intent(this, ManualEntryActivity::class.java))
        }

        findViewById<Button>(R.id.btn_view_history).setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }

        updateSignInButton()
    }

    private fun updateSignInButton() {
        val account = SessionManager.signedInAccount(this)
        btnSelectCloud.text =
            if (account != null) "Drive: ${account.email}" else getString(R.string.select_google_drive)
    }
}

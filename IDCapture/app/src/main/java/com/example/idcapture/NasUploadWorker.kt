package com.example.idcapture

import android.content.Context
import android.net.Uri
import androidx.work.Worker
import androidx.work.WorkerParameters
import jcifs.CIFSContext
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbFile
import java.io.IOException
import java.util.Properties

/**
 * Uploads one saved capture to the UGREEN NAS over SMB2/3 (jcifs-ng).
 * The destination is smb://<host>/<share>/<folder>/<file>; missing folders
 * are created on first upload. UGREEN's UGOS shares work out of the box —
 * SMB is enabled by default under Control Panel > File Services.
 */
class NasUploadWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        val context = applicationContext
        if (!NasSettings.isEnabled(context)) return Result.success()
        if (!NasSettings.isConfigured(context)) return Result.failure()

        val uri = inputData.getString(KEY_URI)?.let(Uri::parse) ?: return Result.failure()
        val name = inputData.getString(KEY_NAME) ?: return Result.failure()

        return try {
            upload(context, uri, name)
            Result.success()
        } catch (e: Exception) {
            // Covers SmbException, IOException and DNS failures alike: the NAS
            // is often simply unreachable (phone off Wi-Fi), so retry later.
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    private fun upload(context: Context, uri: Uri, name: String) {
        val cifs = cifsContext(context)
        val dirUrl = buildString {
            append("smb://")
            append(NasSettings.host(context))
            append('/')
            append(NasSettings.share(context))
            append('/')
            val folder = NasSettings.folder(context)
            if (folder.isNotBlank()) {
                append(folder)
                append('/')
            }
        }

        val dir = SmbFile(dirUrl, cifs)
        try {
            if (!dir.exists()) dir.mkdirs()

            val file = SmbFile(dir, name)
            try {
                file.openOutputStream().use { out ->
                    context.contentResolver.openInputStream(uri)?.use { it.copyTo(out) }
                        ?: throw IOException("Capture no longer readable: $uri")
                }
            } finally {
                file.close()
            }
        } finally {
            dir.close()
        }
    }

    private fun cifsContext(context: Context): CIFSContext {
        val props = Properties().apply {
            setProperty("jcifs.smb.client.minVersion", "SMB202")
            setProperty("jcifs.smb.client.maxVersion", "SMB311")
            setProperty("jcifs.smb.client.responseTimeout", "20000")
            setProperty("jcifs.smb.client.connTimeout", "10000")
        }
        val base = BaseContext(PropertyConfiguration(props))
        val username = NasSettings.username(context)
        return if (username.isBlank()) {
            base.withGuestCrendentials()
        } else {
            base.withCredentials(
                NtlmPasswordAuthenticator(null, username, NasSettings.password(context)),
            )
        }
    }

    companion object {
        const val KEY_URI = "uri"
        const val KEY_NAME = "name"
        private const val MAX_ATTEMPTS = 8
    }
}

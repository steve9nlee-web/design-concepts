package com.photobrander.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Sends the photo + description + company branding to the n8n webhook and
 * returns the generated image bytes that the workflow responds with.
 */
class N8nClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        // AI image generation can take a while; keep the socket open.
        .readTimeout(5, TimeUnit.MINUTES)
        .writeTimeout(2, TimeUnit.MINUTES)
        .build()

    suspend fun generateBrandedPhoto(
        webhookUrl: String,
        photoJpeg: ByteArray,
        description: String,
        companyName: String,
        companyDetails: String,
        logoFile: File?,
    ): ByteArray = withContext(Dispatchers.IO) {
        val bodyBuilder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "photo", "photo.jpg",
                photoJpeg.toRequestBody("image/jpeg".toMediaType())
            )
            .addFormDataPart("description", description)
            .addFormDataPart("company_name", companyName)
            .addFormDataPart("company_details", companyDetails)

        if (logoFile != null && logoFile.exists()) {
            bodyBuilder.addFormDataPart(
                "logo", "logo.png",
                logoFile.asRequestBody("image/png".toMediaType())
            )
        }

        val request = Request.Builder()
            .url(webhookUrl)
            .post(bodyBuilder.build())
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val snippet = response.body?.string()?.take(300) ?: ""
                throw IOException("n8n returned HTTP ${response.code}. $snippet")
            }
            val bytes = response.body?.bytes()
                ?: throw IOException("n8n returned an empty response")
            if (bytes.isEmpty()) throw IOException("n8n returned an empty image")
            bytes
        }
    }
}

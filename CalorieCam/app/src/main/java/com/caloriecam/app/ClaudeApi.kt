package com.caloriecam.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Base64
import androidx.exifinterface.media.ExifInterface
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class FoodAnalysis(
    val foodName: String,
    val calories: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
    val notes: String
)

/**
 * Calls the Claude API vision endpoint to estimate calories from a food photo.
 */
object ClaudeApi {

    private const val ENDPOINT = "https://api.anthropic.com/v1/messages"
    private const val MODEL = "claude-sonnet-5"

    private const val PROMPT =
        "You are a nutritionist. Look at this photo of food and estimate its nutrition. " +
        "Respond with ONLY a JSON object, no markdown fences, with exactly these keys: " +
        "\"food_name\" (short description of the dish), \"calories\" (integer, total kcal), " +
        "\"protein_g\" (integer grams), \"carbs_g\" (integer grams), \"fat_g\" (integer grams), " +
        "\"notes\" (one short sentence about the estimate or portion). " +
        "If the image does not contain food, use food_name \"Not food\" and zeros."

    fun analyzePhoto(photoFile: File, apiKey: String): FoodAnalysis {
        val imageB64 = encodeImage(photoFile)

        val body = JSONObject().apply {
            put("model", MODEL)
            put("max_tokens", 512)
            put("messages", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("content", JSONArray().apply {
                    put(JSONObject().apply {
                        put("type", "image")
                        put("source", JSONObject().apply {
                            put("type", "base64")
                            put("media_type", "image/jpeg")
                            put("data", imageB64)
                        })
                    })
                    put(JSONObject().apply {
                        put("type", "text")
                        put("text", PROMPT)
                    })
                })
            }))
        }

        val conn = URL(ENDPOINT).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 30_000
            conn.readTimeout = 90_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("x-api-key", apiKey)
            conn.setRequestProperty("anthropic-version", "2023-06-01")
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            val response = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.readText() ?: ""
            if (code !in 200..299) {
                throw RuntimeException(apiErrorMessage(code, response))
            }
            return parseAnalysis(response)
        } finally {
            conn.disconnect()
        }
    }

    private fun apiErrorMessage(code: Int, response: String): String {
        val detail = try {
            JSONObject(response).optJSONObject("error")?.optString("message") ?: ""
        } catch (_: Exception) { "" }
        return when (code) {
            401 -> "Invalid API key. Check it in Settings."
            429 -> "Rate limited by the API, try again in a minute."
            else -> "API error $code${if (detail.isNotBlank()) ": $detail" else ""}"
        }
    }

    private fun parseAnalysis(response: String): FoodAnalysis {
        val root = JSONObject(response)
        val sb = StringBuilder()
        val content = root.optJSONArray("content") ?: JSONArray()
        for (i in 0 until content.length()) {
            val block = content.getJSONObject(i)
            if (block.optString("type") == "text") sb.append(block.optString("text"))
        }
        val text = sb.toString()
        // Extract the first JSON object from the reply, tolerating stray text/fences.
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) throw RuntimeException("Could not read the analysis result.")
        val json = JSONObject(text.substring(start, end + 1))
        return FoodAnalysis(
            foodName = json.optString("food_name", "Unknown food"),
            calories = json.optInt("calories", 0),
            proteinG = json.optInt("protein_g", 0),
            carbsG = json.optInt("carbs_g", 0),
            fatG = json.optInt("fat_g", 0),
            notes = json.optString("notes", "")
        )
    }

    /** Downscale to max 1280px, fix EXIF rotation, JPEG-compress, base64-encode. */
    private fun encodeImage(file: File): String {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opts)
        var sample = 1
        val maxDim = maxOf(opts.outWidth, opts.outHeight)
        while (maxDim / sample > 2560) sample *= 2

        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
        var bitmap = BitmapFactory.decodeFile(file.absolutePath, decodeOpts)
            ?: throw RuntimeException("Could not read the photo.")

        val rotation = when (
            ExifInterface(file.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
            )
        ) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (rotation != 0f) {
            val m = Matrix().apply { postRotate(rotation) }
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
        }

        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest > 1280) {
            val scale = 1280f / longest
            bitmap = Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt().coerceAtLeast(1),
                (bitmap.height * scale).toInt().coerceAtLeast(1),
                true
            )
        }

        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }
}

package com.billcapture.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Base64
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream

object BitmapUtil {

    /**
     * Decodes a photo downscaled to roughly [maxDim] on its longest side and
     * corrects orientation from EXIF. Keeps Vision API payloads small.
     */
    fun loadScaled(path: String, maxDim: Int = 1600): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)

        var sample = 1
        var longest = maxOf(bounds.outWidth, bounds.outHeight)
        while (longest / 2 >= maxDim) {
            sample *= 2
            longest /= 2
        }

        val bitmap = BitmapFactory.decodeFile(
            path,
            BitmapFactory.Options().apply { inSampleSize = sample }
        ) ?: throw IllegalStateException("Could not decode photo: $path")

        return rotateFromExif(bitmap, path)
    }

    fun toBase64Jpeg(bitmap: Bitmap, quality: Int = 85): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }

    private fun rotateFromExif(bitmap: Bitmap, path: String): Bitmap {
        val orientation = ExifInterface(path).getAttributeInt(
            ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
        )
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> return bitmap
        }
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}

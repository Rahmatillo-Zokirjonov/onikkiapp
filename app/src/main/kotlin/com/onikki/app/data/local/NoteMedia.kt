package com.onikki.app.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.util.UUID

/**
 * Photos and voice recordings attached to notes. Kept in the app's private storage
 * (filesDir/note_media), so they're removed with the app and never show up in the gallery.
 */
object NoteMedia {
    private const val DIR = "note_media"
    private const val MAX_IMAGE_PX = 1600

    fun dir(context: Context): File = File(context.filesDir, DIR).apply { mkdirs() }

    fun file(context: Context, name: String): File = File(dir(context), name)

    fun delete(context: Context, name: String) {
        runCatching { file(context, name).delete() }
    }

    fun newAudioFile(context: Context): File = File(dir(context), "a_${UUID.randomUUID()}.m4a")

    /** A temporary file + content Uri for the camera app to write into. */
    fun cameraTarget(context: Context): Pair<File, Uri> {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        val file = File(dir, "c_${UUID.randomUUID()}.jpg")
        return file to FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }

    /**
     * Copies a picked or captured photo in, scaled to at most [MAX_IMAGE_PX] and rotated upright,
     * as JPEG. Returns the stored file name, or null if it couldn't be read.
     */
    fun importImage(context: Context, uri: Uri): String? = runCatching {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_IMAGE_PX) sample *= 2
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val rotation = resolver.openInputStream(uri)?.use {
            when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
        val scale = minOf(1f, MAX_IMAGE_PX / maxOf(decoded.width, decoded.height).toFloat())
        val matrix = Matrix().apply {
            if (scale < 1f) postScale(scale, scale)
            if (rotation != 0f) postRotate(rotation)
        }
        val bitmap = if (matrix.isIdentity) decoded else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        val name = "i_${UUID.randomUUID()}.jpg"
        file(context, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        name
    }.getOrNull()

    /** Decodes a stored photo at roughly [maxPx] on its long side (thumbnails, viewer). */
    fun loadBitmap(context: Context, name: String, maxPx: Int): Bitmap? = runCatching {
        val f = file(context, name)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxPx) sample *= 2
        BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()
}

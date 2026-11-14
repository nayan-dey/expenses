package com.kharcha.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.core.content.FileProvider
import com.kharcha.app.core.util.newId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Receipt pictures. They are copied into the app's own folder (shrunk to a sensible size),
 * so they stay with the data even if the original photo is deleted from the gallery.
 */
class ImageStore(private val context: Context) {
    val dir: File = File(context.filesDir, "images").apply { mkdirs() }
    private val authority = context.packageName + ".files"

    fun file(name: String): File = File(dir, name)

    /** An empty file for the camera app to write into, and the address to hand to it. */
    fun newCameraTarget(): Pair<String, Uri> {
        dir.mkdirs()
        val name = newId() + ".jpg"
        return name to FileProvider.getUriForFile(context, authority, file(name))
    }

    /** Copies a picture the person chose into our folder. Returns its file name, or null if it could not be read. */
    suspend fun importFrom(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@runCatching null
            val name = newId() + ".jpg"
            if (shrinkInto(bytes, file(name))) name else null
        }.getOrNull()
    }

    /** After the camera has written a photo: turn it upright and shrink it. False if no photo was taken. */
    suspend fun finishCamera(name: String): Boolean = withContext(Dispatchers.IO) {
        val target = file(name)
        if (!target.exists() || target.length() == 0L) {
            target.delete()
            return@withContext false
        }
        runCatching { shrinkInto(target.readBytes(), target) }.getOrDefault(false)
    }

    private fun shrinkInto(bytes: ByteArray, target: File): Boolean {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_SIDE * 2) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return false

        // Phones store photos sideways and note the real direction separately; apply it.
        val degrees = runCatching {
            when (ExifInterface(bytes.inputStream()).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        }.getOrDefault(0f)
        val scale = minOf(1f, MAX_SIDE.toFloat() / maxOf(decoded.width, decoded.height))
        val matrix = Matrix().apply {
            postScale(scale, scale)
            if (degrees != 0f) postRotate(degrees)
        }
        val result = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        target.outputStream().use { result.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        return true
    }

    /** Reads a picture for showing on screen, no larger than `maxSide` pixels. */
    suspend fun load(name: String, maxSide: Int): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val path = file(name).path
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
        }.getOrNull()
    }

    fun delete(names: Collection<String>) {
        names.forEach { runCatching { file(it).delete() } }
    }

    /** Removes pictures no item points at any more (left behind by a cancelled form, for example). */
    fun deleteUnused(inUse: Set<String>) {
        dir.listFiles()?.forEach { if (it.name !in inUse) runCatching { it.delete() } }
    }

    /** The address another app (a share target) can read this picture from. */
    fun shareUri(name: String): Uri = FileProvider.getUriForFile(context, authority, file(name))

    private companion object {
        const val MAX_SIDE = 1600
    }
}

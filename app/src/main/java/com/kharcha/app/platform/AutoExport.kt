package com.kharcha.app.platform

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.kharcha.app.data.Exchange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The automatic daily backup: a full backup file written into the phone's Downloads folder,
 * in a "Kharcha" folder, keeping the newest seven.
 *
 * It uses Android's shared-storage service (MediaStore), which lets an app add its own files
 * to Downloads without asking for any storage permission. That exists from Android 10.
 */
object AutoExport {
    private const val KEEP = 7
    private const val PREFIX = "kharcha-auto-"

    fun supported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    /** Writes today's backup. Returns false if it could not be written. */
    suspend fun run(context: Context, exchange: Exchange, day: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        return withContext(Dispatchers.IO) {
            runCatching {
                val resolver = context.contentResolver
                val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, "$PREFIX$day.zip")
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Kharcha")
                }
                val uri = resolver.insert(collection, values) ?: return@runCatching false
                (resolver.openOutputStream(uri) ?: return@runCatching false).use { exchange.writeBackup(it) }

                // Keep only the newest few. An app can always see and delete the files it made itself.
                val ids = ArrayList<Long>()
                resolver.query(
                    collection,
                    arrayOf(MediaStore.MediaColumns._ID),
                    "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?",
                    arrayOf("$PREFIX%"),
                    "${MediaStore.MediaColumns.DATE_ADDED} DESC",
                )?.use { cursor ->
                    while (cursor.moveToNext()) ids += cursor.getLong(0)
                }
                ids.drop(KEEP).forEach { id -> runCatching { resolver.delete(ContentUris.withAppendedId(collection, id), null, null) } }
                true
            }.getOrDefault(false)
        }
    }
}

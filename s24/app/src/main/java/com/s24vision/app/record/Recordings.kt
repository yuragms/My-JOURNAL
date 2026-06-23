package com.s24vision.app.record

import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat

/**
 * Доступ к видеозаписям через MediaStore. Файлы пишутся в публичную папку
 * `Movies/S24Vision`, поэтому видны в проводнике и Галерее (рядом с Viber/Telegram).
 */
object Recordings {

    val RELATIVE_DIR = "${Environment.DIRECTORY_MOVIES}/S24Vision/"

    data class Item(
        val id: Long,
        val name: String,
        val uri: Uri,
        val sizeBytes: Long,
    )

    sealed interface DeleteResult {
        data object Success : DeleteResult
        data object Failed : DeleteResult
        data class NeedsConfirmation(val intentSender: IntentSender) : DeleteResult
    }

    private val collection: Uri
        get() = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

    fun canReadAll(context: Context): Boolean {
        val perm = readPermission()
        return ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
    }

    fun readPermission(): String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        android.Manifest.permission.READ_MEDIA_VIDEO
    } else {
        android.Manifest.permission.READ_EXTERNAL_STORAGE
    }

    fun create(context: Context, displayName: String): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, RELATIVE_DIR)
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        return context.contentResolver.insert(collection, values)
            ?: error("Не удалось создать запись в MediaStore")
    }

    fun publish(context: Context, uri: Uri) {
        val values = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
        context.contentResolver.update(uri, values, null, null)
    }

    fun list(context: Context): List<Item> {
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.SIZE,
        )
        val selection = "${MediaStore.Video.Media.RELATIVE_PATH} = ? OR ${MediaStore.Video.Media.RELATIVE_PATH} = ?"
        val args = arrayOf(RELATIVE_DIR, RELATIVE_DIR.trimEnd('/'))
        val sort = "${MediaStore.Video.Media.DATE_ADDED} DESC"
        val out = ArrayList<Item>()
        context.contentResolver.query(collection, projection, selection, args, sort)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                out.add(
                    Item(
                        id = id,
                        name = c.getString(nameCol),
                        uri = ContentUris.withAppendedId(collection, id),
                        sizeBytes = c.getLong(sizeCol).coerceAtLeast(0L),
                    ),
                )
            }
        }
        return out
    }

    /** Удаляет ролик из MediaStore (и файл с диска). */
    fun delete(context: Context, uri: Uri): DeleteResult {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return try {
                val request = MediaStore.createDeleteRequest(context.contentResolver, listOf(uri))
                DeleteResult.NeedsConfirmation(request.intentSender)
            } catch (e: Exception) {
                tryDirectDelete(context, uri)
            }
        }
        return tryDirectDelete(context, uri)
    }

    private fun tryDirectDelete(context: Context, uri: Uri): DeleteResult = try {
        if (context.contentResolver.delete(uri, null, null) > 0) DeleteResult.Success else DeleteResult.Failed
    } catch (e: RecoverableSecurityException) {
        DeleteResult.NeedsConfirmation(e.userAction.actionIntent.intentSender)
    } catch (_: Exception) {
        DeleteResult.Failed
    }
}

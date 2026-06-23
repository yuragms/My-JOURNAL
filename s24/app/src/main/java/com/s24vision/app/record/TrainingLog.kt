package com.s24vision.app.record

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri

/** Какие ролики уже использовались для обучения (ключ — MediaStore _ID). */
class TrainingLog(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("training_log", Context.MODE_PRIVATE)

    fun mark(uri: Uri) {
        val id = uri.mediaId() ?: return
        prefs.edit().putBoolean(key(id), true).apply()
    }

    fun isTrained(uri: Uri): Boolean {
        val id = uri.mediaId() ?: return false
        return prefs.getBoolean(key(id), false)
    }

    fun clear(uri: Uri) {
        val id = uri.mediaId() ?: return
        prefs.edit().remove(key(id)).apply()
    }

    private fun key(id: Long) = "v$id"

    private fun Uri.mediaId(): Long? = runCatching {
        android.content.ContentUris.parseId(this)
    }.getOrNull()
}

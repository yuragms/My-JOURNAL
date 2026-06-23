package com.s24vision.app.vision

import android.content.Context
import org.json.JSONArray

/**
 * Словарь классов YOLOE-26 prompt-free (RAM++, 4585 имён), загружается из
 * ассета `models/yoloe_names.json`. Также определяет, относится ли класс к людям
 * (для ветки лицо+тело).
 */
class ClassNames(context: Context) {

    private val names: List<String> = run {
        val json = context.assets.open("models/yoloe_names.json")
            .bufferedReader().use { it.readText() }
        val arr = JSONArray(json)
        List(arr.length()) { arr.getString(it) }
    }

    fun nameOf(id: Int): String = names.getOrElse(id) { GENERIC }

    fun isPerson(id: Int): Boolean = nameOf(id).lowercase() in PERSON_TAGS

    companion object {
        const val GENERIC = "object"
        private val PERSON_TAGS = setOf(
            "person", "man", "woman", "boy", "girl", "pedestrian", "human", "people",
        )
    }
}

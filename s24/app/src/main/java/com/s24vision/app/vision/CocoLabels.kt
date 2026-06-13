package com.s24vision.app.vision

/**
 * Отображение id класса → имя.
 *
 * Для prompt-free YOLOE полный словарь (RAM++ ~4585 классов) фиксируется после
 * экспорта модели по её метаданным. Здесь зафиксирован ключевой класс `person`;
 * остальные id, не попавшие в карту, считаются обобщённым `object`.
 */
object CocoLabels {

    private val map = mapOf(0 to "person")

    fun nameOf(id: Int): String = map[id] ?: "object"

    const val GENERIC = "object"
}

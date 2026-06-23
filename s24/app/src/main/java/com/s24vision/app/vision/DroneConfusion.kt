package com.s24vision.app.vision

/**
 * Классы RAM++ (YOLOE), которые часто путают с дроном.
 * Для них разрешаем подпись обученного профиля по обычному порогу [objTh].
 */
object DroneConfusion {

    private val yoloeLabels = setOf(
        "assemble",
        "aircraft model",
        "aircraft cabin",
        "cargo aircraft",
        "helicopter",
        "airship",
        "toy",
    )

    fun isConfusable(label: String): Boolean = label.lowercase() in yoloeLabels
}

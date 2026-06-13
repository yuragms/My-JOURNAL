package com.s24vision.app.vision

import kotlin.math.max
import kotlin.math.min

/**
 * Суммарная точность личности (`total`) — порт формулы из hikvision-test1.
 * Лицо точнее, тело устойчивее к повороту. Веса по умолчанию: лицо 0.6, тело 0.4.
 */
class IdentityFusion(
    private val faceTh: Float,
    private val bodyTh: Float,
    private val faceW: Float = 0.6f,
    private val bodyW: Float = 0.4f,
    private val agreeBonus: Float = 0.2f,
    private val weakBlend: Float = 0.25f,
) {
    fun total(face: Float, body: Float): Float {
        val bothOk = face >= faceTh && body >= bodyTh
        return if (bothOk) {
            min(1f, faceW * face + bodyW * body + agreeBonus * min(face, body))
        } else {
            min(1f, max(face, body) + weakBlend * min(face, body))
        }
    }
}

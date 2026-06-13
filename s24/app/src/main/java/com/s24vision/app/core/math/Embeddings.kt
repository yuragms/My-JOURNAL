package com.s24vision.app.core.math

import kotlin.math.sqrt

/** Векторная математика для распознавания по эмбеддингам. Чистые функции. */
object Embeddings {

    fun l2normalize(v: FloatArray): FloatArray {
        var s = 0f
        for (x in v) s += x * x
        val n = sqrt(s).coerceAtLeast(1e-12f)
        return FloatArray(v.size) { v[it] / n }
    }

    /** Скалярное произведение. Для нормированных векторов равно косинусной близости. */
    fun cosine(a: FloatArray, b: FloatArray): Float {
        var d = 0f
        for (i in a.indices) d += a[i] * b[i]
        return d
    }

    /**
     * @return пара (индекс лучшего эталона, косинус). Векторы предполагаются нормированными.
     *         Для пустой галереи возвращает (-1, -1f).
     */
    fun bestMatch(q: FloatArray, gallery: List<FloatArray>): Pair<Int, Float> {
        var bi = -1
        var bs = -1f
        gallery.forEachIndexed { i, g ->
            val s = cosine(q, g)
            if (s > bs) {
                bs = s
                bi = i
            }
        }
        return bi to bs
    }
}

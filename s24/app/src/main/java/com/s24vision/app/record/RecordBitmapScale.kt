package com.s24vision.app.record

import android.graphics.Bitmap

/** Уменьшает кадр перед H.264 — быстрее кодер, меньше нагрузка на CPU. */
object RecordBitmapScale {

    private const val MAX_LONG_EDGE = 1280

    /** @return [src] или уменьшенная копия (исходник тогда нужно recycle вызывающему). */
    fun downscale(src: Bitmap, maxLongEdge: Int = MAX_LONG_EDGE): Bitmap {
        val (nw, nh) = outputSize(src.width, src.height, maxLongEdge)
        if (nw == src.width && nh == src.height) return src
        return Bitmap.createScaledBitmap(src, nw, nh, true)
    }

    fun outputSize(width: Int, height: Int, maxLongEdge: Int = MAX_LONG_EDGE): Pair<Int, Int> {
        val long = maxOf(width, height)
        if (long <= maxLongEdge) return width to height
        val scale = maxLongEdge.toFloat() / long
        val nw = (width * scale).toInt().coerceAtLeast(1)
        val nh = (height * scale).toInt().coerceAtLeast(1)
        return nw to nh
    }
}

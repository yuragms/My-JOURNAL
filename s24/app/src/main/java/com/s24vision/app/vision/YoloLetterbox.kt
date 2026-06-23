package com.s24vision.app.vision

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color

/** Letterbox до квадрата, как в Ultralytics YOLO (сохраняет пропорции, чёрные поля). */
data class LetterboxResult(
    val bitmap: Bitmap,
    val scale: Float,
    val padX: Float,
    val padY: Float,
)

object YoloLetterbox {

    fun fit(source: Bitmap, size: Int): LetterboxResult {
        val w = source.width
        val h = source.height
        val scale = minOf(size.toFloat() / w, size.toFloat() / h)
        val nw = (w * scale).toInt().coerceAtLeast(1)
        val nh = (h * scale).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(source, nw, nh, true)
        val padded = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(padded)
        canvas.drawColor(Color.BLACK)
        val padX = (size - nw) / 2f
        val padY = (size - nh) / 2f
        canvas.drawBitmap(scaled, padX, padY, null)
        if (scaled !== source) scaled.recycle()
        return LetterboxResult(padded, scale, padX, padY)
    }

    fun mapRectToFrame(
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        lb: LetterboxResult,
    ): android.graphics.RectF {
        val l = ((x1 - lb.padX) / lb.scale).coerceIn(0f, Float.MAX_VALUE)
        val t = ((y1 - lb.padY) / lb.scale).coerceIn(0f, Float.MAX_VALUE)
        val r = ((x2 - lb.padX) / lb.scale).coerceIn(0f, Float.MAX_VALUE)
        val b = ((y2 - lb.padY) / lb.scale).coerceIn(0f, Float.MAX_VALUE)
        return android.graphics.RectF(l, t, r, b)
    }
}

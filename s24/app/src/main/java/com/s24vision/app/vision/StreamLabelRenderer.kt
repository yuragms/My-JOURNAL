package com.s24vision.app.vision

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF

/** Подписи «Детекция/Запись: WxH · fps» на bitmap (для mp4). */
object StreamLabelRenderer {

    fun format(prefix: String, width: Int, height: Int, fps: Float): String {
        val res = if (width > 0 && height > 0) "${width}×${height}" else "—"
        val fpsText = if (fps > 0.5f) "%.1f fps".format(fps) else "— fps"
        return "$prefix: $res · $fpsText"
    }

    fun drawOnBitmap(src: Bitmap, lines: List<String>): Bitmap {
        val labels = lines.filter { it.isNotBlank() }
        if (labels.isEmpty()) return src

        val bmp = if (src.isMutable) src else src.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(bmp)
        val h = bmp.height.toFloat()
        val textSize = (h * 0.024f).coerceIn(12f, 28f)
        val margin = (h * 0.012f).coerceAtLeast(6f)
        var y = margin
        labels.forEach { line ->
            y = drawBadge(canvas, line, margin, y, textSize)
        }
        return bmp
    }

    private fun drawBadge(
        canvas: Canvas,
        text: String,
        x: Float,
        y: Float,
        textSize: Float,
    ): Float {
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            this.textSize = textSize
        }
        val padH = textSize * 0.65f
        val padV = textSize * 0.35f
        val fm = textPaint.fontMetrics
        val textH = fm.descent - fm.ascent
        val rect = RectF(
            x,
            y,
            x + textPaint.measureText(text) + padH * 2f,
            y + textH + padV * 2f,
        )
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb((255 * 0.58f).toInt(), 0, 0, 0)
        }
        val corner = textSize * 0.35f
        canvas.drawRoundRect(rect, corner, corner, bg)
        canvas.drawText(text, rect.left + padH, rect.top + padV - fm.ascent, textPaint)
        return rect.bottom + textSize * 0.35f
    }
}

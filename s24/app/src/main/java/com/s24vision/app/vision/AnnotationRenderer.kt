package com.s24vision.app.vision

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas

/** Отрисовка рамок с номером (#N). */
object AnnotationRenderer {

    private const val STROKE_ALPHA = 0.9f
    private const val FILL_ALPHA = 0.22f
    private const val TEXT_ALPHA = 0.95f

    fun caption(ann: Annotation): String =
        "#${ann.boxIndex} ${ann.displayLabel} %.2f".format(ann.recognitionScore)

    fun drawOnBitmap(src: Bitmap, annos: List<Annotation>): Bitmap {
        val bmp = src.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(bmp)
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb((255 * TEXT_ALPHA).toInt(), 255, 255, 0)
            textSize = 36f
        }
        annos.forEach { a ->
            val (s, f) = colors(a.det.isPerson)
            stroke.color = s
            stroke.strokeWidth = 4f
            fill.color = f
            val r = a.det.rect
            canvas.drawRect(r, fill)
            canvas.drawRect(r, stroke)
            canvas.drawText(caption(a), r.left, (r.top - 8f).coerceAtLeast(28f), text)
        }
        return bmp
    }

    fun DrawScope.drawOverlay(annos: List<Annotation>, frameWidth: Int, frameHeight: Int) {
        if (frameWidth == 0 || frameHeight == 0) return
        val sx = size.width / frameWidth
        val sy = size.height / frameHeight
        annos.forEach { a ->
            val r = a.det.rect
            val left = r.left * sx
            val top = r.top * sy
            val w = r.width() * sx
            val h = r.height() * sy
            val (stroke, fill) = composeColors(a.det.isPerson)
            drawRect(color = fill, topLeft = Offset(left, top), size = Size(w, h))
            drawRect(color = stroke, topLeft = Offset(left, top), size = Size(w, h), style = Stroke(3f))
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawText(
                    caption(a),
                    left,
                    (top - 8f).coerceAtLeast(20f),
                    android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.argb((255 * TEXT_ALPHA).toInt(), 255, 255, 0)
                        textSize = 36f
                    },
                )
            }
        }
    }

    private fun colors(isPerson: Boolean): Pair<Int, Int> =
        if (isPerson) {
            rgba(0, 255, 255, STROKE_ALPHA) to rgba(0, 255, 255, FILL_ALPHA)
        } else {
            rgba(0, 255, 0, STROKE_ALPHA) to rgba(0, 255, 0, FILL_ALPHA)
        }

    private fun composeColors(isPerson: Boolean): Pair<
        androidx.compose.ui.graphics.Color,
        androidx.compose.ui.graphics.Color,
    > {
        val stroke = if (isPerson) {
            androidx.compose.ui.graphics.Color.Cyan.copy(alpha = STROKE_ALPHA)
        } else {
            androidx.compose.ui.graphics.Color.Green.copy(alpha = STROKE_ALPHA)
        }
        return stroke to stroke.copy(alpha = FILL_ALPHA)
    }

    private fun rgba(r: Int, g: Int, b: Int, a: Float) =
        Color.argb((255 * a).toInt(), r, g, b)
}

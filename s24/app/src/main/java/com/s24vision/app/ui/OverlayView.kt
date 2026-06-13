package com.s24vision.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import com.s24vision.app.vision.Annotation

/**
 * Рисует боксы и подписи. Координаты аннотаций — в пикселях кадра; масштабируем
 * к размеру Canvas (предполагается режим заполнения превью FILL_CENTER ≈ совпадает
 * по соотношению; при необходимости уточняется на устройстве).
 */
@Composable
fun OverlayView(
    annotations: List<Annotation>,
    frameWidth: Int,
    frameHeight: Int,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        if (frameWidth == 0 || frameHeight == 0) return@Canvas
        val sx = size.width / frameWidth
        val sy = size.height / frameHeight
        annotations.forEach { a ->
            val r = a.det.rect
            val color = if (a.det.isPerson) Color.Cyan else Color.Green
            drawRect(
                color = color,
                topLeft = Offset(r.left * sx, r.top * sy),
                size = Size(r.width() * sx, r.height() * sy),
                style = Stroke(3f),
            )
            drawContext.canvas.nativeCanvas.drawText(
                a.displayLabel,
                r.left * sx,
                (r.top * sy - 8f).coerceAtLeast(20f),
                android.graphics.Paint().apply {
                    this.color = android.graphics.Color.YELLOW
                    textSize = 36f
                },
            )
        }
    }
}

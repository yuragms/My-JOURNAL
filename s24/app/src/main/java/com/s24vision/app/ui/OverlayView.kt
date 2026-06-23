package com.s24vision.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.s24vision.app.vision.Annotation
import com.s24vision.app.vision.AnnotationRenderer

/** Прозрачные боксы с номерами (#N) поверх превью камеры. */
@Composable
fun OverlayView(
    annotations: List<Annotation>,
    frameWidth: Int,
    frameHeight: Int,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        AnnotationRenderer.run { drawOverlay(annotations, frameWidth, frameHeight) }
    }
}

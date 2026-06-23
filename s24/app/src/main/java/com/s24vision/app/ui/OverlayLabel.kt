package com.s24vision.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Подпись поверх камеры/видео: читаема на светлом и тёмном фоне. */
@Composable
fun OverlayLabel(text: String, modifier: Modifier = Modifier) {
    if (text.isBlank()) return
    Surface(
        modifier = modifier,
        color = Color.Black.copy(alpha = 0.58f),
        shape = RoundedCornerShape(6.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

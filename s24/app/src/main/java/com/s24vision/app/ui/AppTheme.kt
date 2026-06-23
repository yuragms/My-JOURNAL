package com.s24vision.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Отступы оверлея камеры. */
internal object AppUi {
    private const val BTN_SCALE = 1.1f * 1.1f // +10% поверх прошлого (+21% от базы 34dp)
    private const val LIFT_DOWN = 0.93f * 0.85f // −7% и ещё −15% вниз

    val controlButtonHeight = 34.dp * BTN_SCALE
    /** Подъём над Samsung-панелью: −20% и ещё −30% высоты кнопки от края. */
    val cameraNavExtraLift = controlButtonHeight * (1.2f * LIFT_DOWN - 0.5f)
    val cameraBarButtonMinWidth = 46.dp * BTN_SCALE
    val cameraBarButtonWideMinWidth = 52.dp * BTN_SCALE
    val cameraBarButtonProfileMinWidth = 62.dp * BTN_SCALE
    val statsLineHeight = 22.dp
    val cameraStatsTopPadding = 8.dp + statsLineHeight
    val cameraBarHGap = 6.dp * 1.5f
    val cameraBarSidePadding = 8.dp
}

/** Фон экранов меню — темнее, чтобы строка уведомлений на телефоне читалась. */
private val AppBackground = Color(0xFFB0B6BD)
private val AppSurface = Color(0xFFC2C8CF)

private val AppPrimary = Color(0xFF1565C0)
private val AppOnPrimary = Color.White
private val AppSecondary = Color(0xFF37474F)
private val AppError = Color(0xFFC62828)

@Composable
fun S24VisionTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = AppPrimary,
            onPrimary = AppOnPrimary,
            secondary = AppSecondary,
            onSecondary = Color.White,
            background = AppBackground,
            surface = AppSurface,
            onSurface = Color(0xFF1A1A1A),
            surfaceVariant = Color(0xFFB5BBC2),
            onSurfaceVariant = Color(0xFF37474F),
            error = AppError,
            onError = Color.White,
            outline = AppPrimary,
        ),
        content = content,
    )
}

@Composable
fun AppFilledButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    colors: androidx.compose.material3.ButtonColors = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.38f),
        disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f),
    ),
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        contentPadding = contentPadding,
        shape = RoundedCornerShape(8.dp),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 3.dp,
            pressedElevation = 6.dp,
            disabledElevation = 0.dp,
        ),
        colors = colors,
        content = content,
    )
}

@Composable
fun AppOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        contentPadding = contentPadding,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color(0xFFE8EEF4),
            contentColor = MaterialTheme.colorScheme.primary,
            disabledContainerColor = Color(0xFFEEEEEE),
            disabledContentColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.38f),
        ),
        content = content,
    )
}

@Composable
fun AppTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = ButtonDefaults.textButtonColors(
            contentColor = MaterialTheme.colorScheme.primary,
        ),
        content = content,
    )
}

@Composable
fun AppDangerButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    AppFilledButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        contentPadding = contentPadding,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
            disabledContainerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.38f),
            disabledContentColor = MaterialTheme.colorScheme.onError.copy(alpha = 0.6f),
        ),
        content = content,
    )
}

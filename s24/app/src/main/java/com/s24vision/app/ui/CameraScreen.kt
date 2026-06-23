package com.s24vision.app.ui

import androidx.camera.view.PreviewView
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.s24vision.app.camera.CameraController

@Composable
fun CameraScreen(onOpenRecordings: () -> Unit, onOpenProfiles: () -> Unit) {
    val owner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val state = remember {
        CameraScreenState(
            owner = owner,
            controller = CameraController(context),
            pipeline = AppDeps.pipeline(),
            settings = AppDeps.recognitionSettings(),
        )
    }
    val barStyle = MaterialTheme.typography.labelMedium.copy(
        fontSize = 14.sp,
        lineHeight = 17.sp,
    )
    Box(
        Modifier
            .fillMaxSize()
            .pinchToZoom(state),
    ) {
        AndroidView(
            factory = { ctx -> PreviewView(ctx).also { state.attachPreview(it) } },
            modifier = Modifier.fillMaxSize(),
        )
        OverlayView(
            annotations = state.annotations.value,
            frameWidth = state.frameWidth.value,
            frameHeight = state.frameHeight.value,
            modifier = Modifier.fillMaxSize(),
        )
        OverlayLabel(
            text = state.detectStreamLabel.value,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 8.dp, top = AppUi.cameraStatsTopPadding),
        )
        if (state.recording.value && state.recordStreamLabel.value.isNotBlank()) {
            OverlayLabel(
                text = state.recordStreamLabel.value,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 8.dp, top = AppUi.cameraStatsTopPadding + 28.dp),
            )
        }
        OverlayLabel(
            text = state.statsLabel.value,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(
                    start = 8.dp,
                    top = AppUi.cameraStatsTopPadding + if (state.recording.value) 56.dp else 28.dp,
                ),
        )
        if (state.recording.value) {
            RecordingBadge(
                seconds = state.recordSeconds.value,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = AppUi.cameraStatsTopPadding),
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(
                    start = AppUi.cameraBarSidePadding,
                    end = AppUi.cameraBarSidePadding,
                    bottom = AppUi.cameraNavExtraLift,
                ),
            horizontalArrangement = Arrangement.spacedBy(AppUi.cameraBarHGap, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CameraBarButton("Камера", barStyle) { state.toggleLens() }
            CameraBarButton(if (state.recording.value) "Стоп" else "REC", barStyle) {
                state.toggleRecord()
            }
            CameraBarButton("Записи", barStyle, minWidth = AppUi.cameraBarButtonWideMinWidth) { onOpenRecordings() }
            CameraBarButton("Профили", barStyle, minWidth = AppUi.cameraBarButtonProfileMinWidth) { onOpenProfiles() }
        }
    }
}

@Composable
private fun RecordingBadge(seconds: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(Color.Red),
        )
        Text(
            text = "REC %02d:%02d".format(seconds / 60, seconds % 60),
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

private fun Modifier.pinchToZoom(state: CameraScreenState): Modifier = pointerInput(state) {
    detectTransformGestures { _, _, zoom, _ ->
        if (zoom != 1f) state.setZoomRatio(state.currentZoomRatio() * zoom)
    }
}

@Composable
private fun CameraBarButton(
    label: String,
    textStyle: androidx.compose.ui.text.TextStyle,
    minWidth: androidx.compose.ui.unit.Dp = AppUi.cameraBarButtonMinWidth,
    onClick: () -> Unit,
) {
    AppFilledButton(
        onClick = onClick,
        modifier = Modifier
            .height(AppUi.controlButtonHeight)
            .defaultMinSize(minWidth = minWidth),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = label,
            style = textStyle,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
        )
    }
}

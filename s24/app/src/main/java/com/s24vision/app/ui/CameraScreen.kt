package com.s24vision.app.ui

import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
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
        )
    }
    Box(Modifier.fillMaxSize()) {
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
        Text(state.statsLabel.value, Modifier.padding(8.dp).align(Alignment.TopStart))
        Row(
            Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(16.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            Button(onClick = { state.toggleLens() }) { Text("Камера") }
            Spacer(Modifier.width(12.dp))
            Button(onClick = { state.toggleRecord() }) {
                Text(if (state.recording.value) "Стоп" else "REC")
            }
            Spacer(Modifier.width(12.dp))
            Button(onClick = onOpenRecordings) { Text("Записи") }
            Spacer(Modifier.width(12.dp))
            Button(onClick = onOpenProfiles) { Text("Профили") }
        }
    }
}

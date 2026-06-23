package com.s24vision.app.ui

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.s24vision.app.record.Recordings
import com.s24vision.app.record.VideoInfo
import com.s24vision.app.record.formatBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder

@Composable
fun RecordingsScreen(onTrain: (videoUri: String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var refreshTick by remember { mutableIntStateOf(0) }
    var items by remember { mutableStateOf<List<Recordings.Item>>(emptyList()) }
    var infoMap by remember { mutableStateOf<Map<Long, VideoInfo>>(emptyMap()) }
    var needsPermission by remember { mutableStateOf(!Recordings.canReadAll(context)) }
    var playUri by remember { mutableStateOf<Uri?>(null) }
    var playTitle by remember { mutableStateOf("") }
    var deleteError by remember { mutableStateOf("") }
    var pendingDeleteUri by remember { mutableStateOf<Uri?>(null) }

    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        val uri = pendingDeleteUri
        pendingDeleteUri = null
        if (result.resultCode == Activity.RESULT_OK && uri != null) {
            AppDeps.trainingLog().clear(uri)
            refreshTick++
        } else if (uri != null) {
            deleteError = "Удаление отменено"
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        needsPermission = !granted
        if (granted) refreshTick++
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(refreshTick) {
        if (!Recordings.canReadAll(context)) {
            permissionLauncher.launch(Recordings.readPermission())
            return@LaunchedEffect
        }
        val loaded = Recordings.list(context)
        items = loaded
        infoMap = withContext(Dispatchers.IO) {
            loaded.associate { item ->
                item.id to (VideoInfo.probe(context, item.uri) ?: VideoInfo(15f, 0, 0))
            }
        }
    }

    if (playUri != null) {
        VideoPlayerDialog(
            uri = playUri!!,
            title = playTitle,
            onDismiss = { playUri = null },
        )
    }

    Column(Modifier.fillMaxSize()) {
        Text(
            "Записи",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        Text(
            "✓ обучено   · не обучено",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        if (deleteError.isNotBlank()) {
            Text(
                deleteError,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp).padding(top = 4.dp),
            )
        }
        HorizontalDivider(Modifier.padding(top = 8.dp))

        when {
            needsPermission && items.isEmpty() -> {
                Box(
                    Modifier.fillMaxSize().padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Нужен доступ к видео, чтобы показать записи в Movies/S24Vision.")
                }
            }
            items.isEmpty() -> {
                Box(
                    Modifier.fillMaxSize().padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Записей пока нет.\nВключите запись на экране камеры при обнаружении объектов.")
                }
            }
            else -> {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(items, key = { it.id }) { item ->
                        RecordingRow(
                            item = item,
                            info = infoMap[item.id],
                            trained = AppDeps.trainingLog().isTrained(item.uri),
                            onPlay = {
                                playUri = item.uri
                                playTitle = item.name
                            },
                            onTrain = { onTrain(URLEncoder.encode(item.uri.toString(), "UTF-8")) },
                            onDelete = {
                                deleteError = ""
                                when (val r = Recordings.delete(context, item.uri)) {
                                    is Recordings.DeleteResult.Success -> {
                                        AppDeps.trainingLog().clear(item.uri)
                                        refreshTick++
                                    }
                                    is Recordings.DeleteResult.NeedsConfirmation -> {
                                        pendingDeleteUri = item.uri
                                        deleteLauncher.launch(
                                            IntentSenderRequest.Builder(r.intentSender).build(),
                                        )
                                    }
                                    is Recordings.DeleteResult.Failed ->
                                        deleteError = "Не удалось удалить запись"
                                }
                            },
                        )
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordingRow(
    item: Recordings.Item,
    info: VideoInfo?,
    trained: Boolean,
    onPlay: () -> Unit,
    onTrain: () -> Unit,
    onDelete: () -> Unit,
) {
    val meta = info?.summary(item.sizeBytes) ?: formatBytes(item.sizeBytes)
    val btnHeight = 36.dp
    val btnStyle = MaterialTheme.typography.labelMedium

    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (trained) "✓" else "·",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (trained) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.width(20.dp),
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp),
                ) {
                    Text(
                        item.name,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, start = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppOutlinedButton(
                    onClick = onPlay,
                    modifier = Modifier.width(44.dp).height(btnHeight),
                    contentPadding = PaddingValues(0.dp),
                ) { Text("▶", style = btnStyle) }
                AppFilledButton(
                    onClick = onTrain,
                    modifier = Modifier.height(btnHeight),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                ) {
                    Text("Обучить", style = btnStyle, maxLines = 1)
                }
                AppDangerButton(
                    onClick = onDelete,
                    modifier = Modifier.width(48.dp).height(btnHeight),
                    contentPadding = PaddingValues(0.dp),
                ) { Text("del", style = btnStyle) }
            }
        }
    }
}

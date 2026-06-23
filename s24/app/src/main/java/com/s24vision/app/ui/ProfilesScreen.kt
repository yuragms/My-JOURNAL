package com.s24vision.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.s24vision.app.core.onnx.InferenceBackend
import com.s24vision.app.core.onnx.OnnxRuntimeStatus
import com.s24vision.app.core.profiles.ProfileMetrics
import com.s24vision.app.core.profiles.ProfileType
import com.s24vision.app.core.settings.BuiltinModels
import com.s24vision.app.core.settings.DroneDetVariant
import com.s24vision.app.core.settings.RecognitionSettings

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfilesScreen() {
    val store = AppDeps.store()
    val settings = AppDeps.recognitionSettings()
    var refresh by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val profileRows = remember(refresh) {
        ProfileType.entries.flatMap { t -> store.list(t).map { t to it } }
    }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
        item {
            Text(
                "Модели и профили",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }

        item {
            InferenceBackendSection(settings = settings, refresh = refresh, onChanged = { refresh++ })
        }

        item {
            DroneDetVariantSection(settings = settings, refresh = refresh, onChanged = { refresh++ })
        }

        item {
            Text(
                "YOLOE выкл. → без RAM++ (стул, laptop…). Дрон: включите «Детектор дронов». " +
                    "YOLOE для объектов — только если есть профили кроме drone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        item {
            SectionTitle("Встроенные модели (assets/models)")
        }
        items(BuiltinModels.all, key = { it.id }) { model ->
            val enabled = remember(refresh, model.id) { settings.isBuiltinEnabled(model.id) }
            ModelRow(
                title = model.titleRu,
                assets = model.assets,
                description = model.descriptionRu,
                sizeLabel = BuiltinModels.formatSizeMb(model.sizeMb),
                accuracyLabel = BuiltinModels.formatAccuracy(model.accuracyHint),
                enabled = enabled,
                onToggle = { checked ->
                    settings.setBuiltinEnabled(model.id, checked)
                    refresh++
                },
            )
        }

        item {
            SectionTitle("Обученные профили")
            if (profileRows.isEmpty()) {
                Text(
                    "Пока нет — обучите через Записи → Обучить.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }

        items(profileRows, key = { (t, name) -> "${t.name}/$name" }) { (type, name) ->
            val enabled = remember(refresh, type, name) { settings.isProfileEnabled(type, name) }
            val sizeMb = remember(refresh, type, name) {
                ProfileMetrics.bytesToMb(store.fileSizeBytes(type, name))
            }
            val samples = remember(refresh, type, name) { store.sampleCount(type, name) }
            ModelRow(
                title = name,
                assets = "${type.dir}/$name.bin",
                description = "${type.displayRu} · $samples этал.",
                sizeLabel = ProfileMetrics.formatSizeMb(sizeMb),
                accuracyLabel = ProfileMetrics.formatAccuracy(
                    ProfileMetrics.estimateAccuracy(type, samples),
                ),
                enabled = enabled,
                onToggle = { checked ->
                    settings.setProfileEnabled(type, name, checked)
                    refresh++
                },
                trailing = {
                    AppDangerButton(onClick = {
                        store.delete(type, name)
                        settings.clearProfile(type, name)
                        refresh++
                    }) { Text("Удалить") }
                },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DroneDetVariantSection(
    settings: RecognitionSettings,
    refresh: Int,
    onChanged: () -> Unit,
) {
    val variant = remember(refresh) { settings.droneDetVariant() }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.45f)),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("Размер drone_det (YOLO11)", style = MaterialTheme.typography.titleMedium)
            Text(
                "n → l: сравните скорость и дальность на одной сцене",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.padding(top = 2.dp, bottom = 6.dp),
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                DroneDetVariant.entries.forEach { option ->
                    FilterChip(
                        selected = variant == option,
                        onClick = {
                            settings.setDroneDetVariant(option)
                            onChanged()
                        },
                        label = { Text(option.titleRu) },
                    )
                }
            }
            Text(
                "Файл: ${variant.assetFile} · ~${BuiltinModels.formatSizeMb(variant.sizeMbHint)}\n${variant.sourceHint}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InferenceBackendSection(
    settings: RecognitionSettings,
    refresh: Int,
    onChanged: () -> Unit,
) {
    val backend = remember(refresh) { settings.inferenceBackend() }
    val ortLines = remember(refresh) { OnnxRuntimeStatus.detailLines() }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                "Ускоритель нейросетей",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "CPU / NNAPI / NNAPI строгий — для сравнения скорости и качества",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 2.dp, bottom = 6.dp),
            )
            Text(
                backend.hintRu,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                InferenceBackend.entries.forEach { option ->
                    FilterChip(
                        selected = backend == option,
                        onClick = {
                            settings.setInferenceBackend(option)
                            onChanged()
                        },
                        label = { Text(option.titleRu) },
                    )
                }
            }
            if (ortLines.isNotEmpty()) {
                Text(
                    "Активные сессии:\n" + ortLines.joinToString("\n"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp),
                )
            } else {
                Text(
                    "Сначала откройте камеру — появится список, где крутится каждая модель.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
    HorizontalDivider()
}

@Composable
private fun ModelRow(
    title: String,
    assets: String,
    description: String,
    sizeLabel: String,
    accuracyLabel: String,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    trailing: @Composable (() -> Unit)? = null,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = {
            Column {
                Text(
                    assets,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(description, style = MaterialTheme.typography.bodySmall)
            }
        },
        trailingContent = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                trailing?.invoke()
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        sizeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        accuracyLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Switch(checked = enabled, onCheckedChange = onToggle)
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

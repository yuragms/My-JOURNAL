package com.s24vision.app.ui

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.s24vision.app.core.profiles.EnrollResult
import com.s24vision.app.enroll.Enroller
import kotlinx.coroutines.launch

@Composable
fun TrainScreen(videoPath: String?) {
    var name by remember { mutableStateOf("") }
    var boxIndexText by remember { mutableStateOf("1") }
    var isPerson by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var progressLabel by remember { mutableStateOf("") }
    var systemLoad by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    val boxIndex = boxIndexText.toIntOrNull()?.coerceAtLeast(1) ?: 1
    val profileExists = name.isNotBlank() && AppDeps.profileExists(name, isPerson)
    val canAct = name.isNotBlank() && videoPath != null && !isProcessing && boxIndexText.toIntOrNull() != null

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(if (videoPath != null) "Ролик выбран" else "Ролик не выбран")
        OutlinedTextField(
            value = name,
            onValueChange = { if (!isProcessing) name = it },
            enabled = !isProcessing,
            label = { Text("Имя человека / объекта") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = boxIndexText,
            onValueChange = { if (!isProcessing) boxIndexText = it.filter { c -> c.isDigit() }.take(2) },
            enabled = !isProcessing,
            label = { Text("Номер бокса (цифра)") },
            supportingText = {
                Text(
                    if (isPerson) {
                        "Только цифра: 1, 2… Символ # с экрана вводить не нужно — на камере #1 значит в поле «1»."
                    } else {
                        "Только цифра: 1, 2… # на рамке в записи — это подпись; в поле вводите 1. «Это человек?» выкл."
                    },
                )
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Это человек?")
            Switch(
                checked = isPerson,
                onCheckedChange = { if (!isProcessing) isPerson = it },
                enabled = !isProcessing,
            )
        }
        if (!isPerson) {
            Text(
                "Для дрона и других объектов переключатель должен быть выключен. " +
                    "На видео должен быть виден номер рамки #N (как на экране камеры).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        Spacer(Modifier.height(8.dp))
        Text(
            when {
                isProcessing -> progressLabel
                name.isBlank() -> "Введите имя профиля."
                profileExists -> "Профиль «$name» уже есть — можно дообучить."
                else -> "Профиля «$name» ещё нет — можно обучить новый."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (isProcessing) {
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "$progress%",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 6.dp),
            )
            if (systemLoad.isNotBlank()) {
                Text(
                    systemLoad,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppFilledButton(
                enabled = canAct && !profileExists,
                onClick = {
                    scope.launch {
                        runEnrollUi(
                            videoPath!!, name, isPerson, boxIndex, Enroller.Mode.CREATE,
                            onProgress = { p, label, load ->
                                progress = p
                                progressLabel = label
                                systemLoad = load
                            },
                            onDone = { message = it },
                            setProcessing = { isProcessing = it },
                        )
                    }
                },
                modifier = Modifier.weight(1f),
            ) { Text("Обучить") }
            AppOutlinedButton(
                enabled = canAct && profileExists,
                onClick = {
                    scope.launch {
                        runEnrollUi(
                            videoPath!!, name, isPerson, boxIndex, Enroller.Mode.IMPROVE,
                            onProgress = { p, label, load ->
                                progress = p
                                progressLabel = label
                                systemLoad = load
                            },
                            onDone = { message = it },
                            setProcessing = { isProcessing = it },
                        )
                    }
                },
                modifier = Modifier.weight(1f),
            ) { Text("Дообучить") }
        }

        Spacer(Modifier.height(12.dp))
        Text(message)
    }
}

private suspend fun runEnrollUi(
    videoPath: String,
    name: String,
    isPerson: Boolean,
    boxIndex: Int,
    mode: Enroller.Mode,
    onProgress: (percent: Int, label: String, load: String) -> Unit,
    onDone: (String) -> Unit,
    setProcessing: (Boolean) -> Unit,
) {
    setProcessing(true)
    onProgress(0, "Запуск…", "")
    try {
        val outcome = AppDeps.enroller().enroll(videoPath, name, isPerson, mode, boxIndex) { p ->
            onProgress(p.percent, p.label, p.systemLoad)
        }
        onDone(formatOutcome(outcome, videoPath, name, boxIndex))
    } finally {
        setProcessing(false)
    }
}

private fun formatOutcome(
    outcome: Enroller.Outcome,
    videoPath: String,
    name: String,
    boxIndex: Int,
): String {
    val r = outcome.face ?: outcome.body ?: outcome.obj
        ?: return "Бокс #$boxIndex не найден в ролике"
    return when (r) {
        is EnrollResult.Created -> {
            AppDeps.trainingLog().mark(Uri.parse(videoPath))
            val where = if (outcome.obj != null) "Объект" else "Лицо+Тело"
            "Создан профиль «$name» ($where, бокс #$boxIndex, ${r.total} образцов). Смотрите в «Профили»."
        }
        is EnrollResult.Improved -> {
            AppDeps.trainingLog().mark(Uri.parse(videoPath))
            "Профиль «$name» (бокс #$boxIndex) улучшен: было ${r.before}, стало ${r.after}. Смотрите в «Профили»."
        }
        is EnrollResult.AlreadyExists ->
            "Профиль «${r.name}» уже существует — используйте «Дообучить»"
        is EnrollResult.NotFound ->
            "Профиля «${r.name}» нет — сначала нажмите «Обучить»"
        is EnrollResult.Empty -> r.reason
    }
}

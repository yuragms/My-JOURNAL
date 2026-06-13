package com.s24vision.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.s24vision.app.core.profiles.EnrollResult
import com.s24vision.app.enroll.Enroller

@Composable
fun TrainScreen(videoPath: String?) {
    var name by remember { mutableStateOf("") }
    var isPerson by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(if (videoPath != null) "Ролик выбран" else "Ролик не выбран")
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Имя") })
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Это человек?")
            Switch(checked = isPerson, onCheckedChange = { isPerson = it })
        }
        Button(
            enabled = name.isNotBlank() && videoPath != null,
            onClick = {
                val existedBefore = AppDeps.profileExists(name, isPerson)
                val outcome = AppDeps.enroller().enroll(videoPath!!, name, isPerson)
                message = renderMessage(existedBefore, outcome)
            },
        ) { Text("Обучить / Дообучить") }
        Spacer(Modifier.height(12.dp))
        Text(message)
    }
}

private fun renderMessage(@Suppress("UNUSED_PARAMETER") existedBefore: Boolean, o: Enroller.Outcome): String {
    val r = o.face ?: o.body ?: o.obj ?: return "Объект в ролике не найден"
    return when (r) {
        is EnrollResult.Created -> "Профиля не было — создан новый (${r.total} образцов)"
        is EnrollResult.Improved -> "Профиль улучшен: было ${r.before}, стало ${r.after}"
        is EnrollResult.Empty -> "В ролике не найдено подходящих кадров"
    }
}

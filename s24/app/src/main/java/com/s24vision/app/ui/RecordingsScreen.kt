package com.s24vision.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.net.URLEncoder

@Composable
fun RecordingsScreen(onTrain: (videoPath: String) -> Unit) {
    val files = remember {
        AppDeps.recordingsDir().listFiles()
            ?.filter { it.extension == "mp4" }
            ?.sortedDescending()
            ?: emptyList()
    }
    LazyColumn(Modifier.fillMaxSize().padding(12.dp)) {
        items(files) { f ->
            ListItem(
                headlineContent = { Text(f.name) },
                trailingContent = {
                    Button(onClick = { onTrain(URLEncoder.encode(f.path, "UTF-8")) }) {
                        Text("Обучить")
                    }
                },
            )
        }
    }
}

package com.s24vision.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.s24vision.app.core.profiles.ProfileType

@Composable
fun ProfilesScreen() {
    val store = AppDeps.store()
    var refresh by remember { mutableStateOf(0) }
    val rows = remember(refresh) {
        ProfileType.values().flatMap { t -> store.list(t).map { t to it } }
    }
    LazyColumn(Modifier.fillMaxSize().padding(12.dp)) {
        items(rows) { (t, name) ->
            ListItem(
                headlineContent = { Text(name) },
                supportingContent = { Text(t.name) },
                trailingContent = {
                    Button(onClick = {
                        store.delete(t, name)
                        refresh++
                    }) { Text("Удалить") }
                },
            )
        }
    }
}

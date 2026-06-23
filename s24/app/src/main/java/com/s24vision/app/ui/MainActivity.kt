package com.s24vision.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

class MainActivity : ComponentActivity() {

    private var granted by mutableStateOf(false)

    private val requestPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
            granted = ok
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppDeps.init(this)
        requestPermission.launch(android.Manifest.permission.CAMERA)
        setContent {
            S24VisionTheme {
                Surface(Modifier.fillMaxSize()) {
                    if (granted) AppNav() else PermissionPrompt()
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun PermissionPrompt() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Нужен доступ к камере. Разрешите его в диалоге или настройках приложения.")
    }
}

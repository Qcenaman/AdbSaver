package com.qcenaman.adbsaver.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.qcenaman.adbsaver.Prefs
import com.qcenaman.adbsaver.appVersion
import com.qcenaman.adbsaver.service.EngineService

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.killed = false
        EngineService.start(this)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize()) { Home() }
            }
        }
    }

    @Composable
    private fun Home() {
        val scroll = rememberScrollState()
        var paused by remember { mutableStateOf(Prefs.paused) }
        Column(Modifier.fillMaxSize().padding(20.dp).verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("AdbSaver", style = MaterialTheme.typography.headlineMedium)
            Text("Engine $appVersion · port ${Prefs.port}", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            Text("Token: ${Prefs.token.take(10)}…", style = MaterialTheme.typography.bodySmall)

            Button(onClick = { paused = !paused; Prefs.paused = paused; EngineService.start(this@MainActivity) }, modifier = Modifier.fillMaxWidth()) {
                Text(if (paused) "Resume engine" else "Pause engine")
            }

            Divider(Modifier.padding(vertical = 6.dp))
            Text("Setup", style = MaterialTheme.typography.titleMedium)
            SetupButton("Battery: allow unrestricted") {
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
            }
            SetupButton("Accessibility: enable screen reader") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            SetupButton("Notification access") { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
            SetupButton("All files access") {
                if (Build.VERSION.SDK_INT >= 30)
                    startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName")))
            }
            SetupButton("Wireless debugging (pair for shell access)") { startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) }
        }
    }

    @Composable
    private fun SetupButton(label: String, onClick: () -> Unit) {
        OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(label) }
    }
}

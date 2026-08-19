@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.dionysus.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dionysus.tv.ui.DionysusApp
import com.dionysus.tv.ui.components.AppButton
import com.dionysus.tv.ui.theme.DionysusTheme
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Show the captured trace ONLY when the crash is from this exact version (so a
        // stale report from an older build never nags). Anything else is cleared.
        val crashFile = File(filesDir, DionysusApplication.CRASH_FILE)
        val text = if (crashFile.exists()) runCatching { crashFile.readText() }.getOrNull() else null
        val isCurrent = text != null && text.contains("v${BuildConfig.VERSION_NAME}")
        if (text != null && !isCurrent) runCatching { crashFile.delete() }

        setContent {
            DionysusTheme {
                if (isCurrent && text != null) {
                    CrashReportScreen(text) {
                        runCatching { crashFile.delete() }
                        recreate()
                    }
                } else {
                    DionysusApp()
                }
            }
        }
    }
}

@Composable
private fun CrashReportScreen(trace: String, onDismiss: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0A0D))
            .verticalScroll(rememberScrollState())
            .padding(28.dp),
    ) {
        Text(
            "Dionysus hit an error — please screenshot this and send it over.",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(16.dp))
        SelectionContainer {
            Text(
                text = trace,
                color = Color(0xFFEDE7DA),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            )
        }
        Spacer(Modifier.height(24.dp))
        AppButton(onClick = onDismiss) { Text("Dismiss & continue") }
    }
}

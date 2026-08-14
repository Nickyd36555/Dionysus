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

        // If the previous run crashed, show the captured stack trace instead of the
        // app so it can be screenshotted — then Dismiss clears it and re-enters.
        val crashFile = File(filesDir, DionysusApplication.CRASH_FILE)
        val lastCrash = if (crashFile.exists()) runCatching { crashFile.readText() }.getOrNull() else null

        setContent {
            DionysusTheme {
                if (!lastCrash.isNullOrBlank()) {
                    CrashReportScreen(lastCrash) {
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

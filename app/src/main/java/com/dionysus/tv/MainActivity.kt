package com.dionysus.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.dionysus.tv.ui.DionysusApp
import com.dionysus.tv.ui.theme.DionysusTheme
import dagger.hilt.android.AndroidEntryPoint
import java.io.File

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Clear any leftover crash report; the app no longer shows a crash screen.
        runCatching { File(filesDir, DionysusApplication.CRASH_FILE).delete() }
        setContent {
            DionysusTheme {
                DionysusApp()
            }
        }
    }
}

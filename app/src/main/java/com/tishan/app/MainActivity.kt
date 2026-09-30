package com.tishan.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tishan.app.core.navigation.TishanApp
import com.tishan.app.core.designsystem.theme.TishanTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by (application as TishanApplication)
                .settingsRepository
                .state
                .collectAsStateWithLifecycle()
            TishanTheme(
                themeMode = settings.themeMode,
                fontScale = settings.fontScale,
            ) {
                TishanApp()
            }
        }
    }
}

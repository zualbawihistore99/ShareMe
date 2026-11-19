package com.example

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.local.AppThemeMode
import com.example.ui.ShareMeApp
import com.example.ui.ShareMeViewModel
import com.example.ui.theme.ShareMeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val appContainer = (application as ShareMeApplication).container

        setContent {
            val shareMeViewModel: ShareMeViewModel = viewModel(
                factory = ShareMeViewModel.factory(appContainer)
            )
            val prefs by shareMeViewModel.devicePreferences.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val isDarkTheme = when (prefs.themeMode) {
                AppThemeMode.SYSTEM -> systemDark
                AppThemeMode.DAY -> false
                AppThemeMode.NIGHT -> true
            }

            SideEffect {
                enableEdgeToEdge(
                    statusBarStyle = if (isDarkTheme) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    },
                    navigationBarStyle = if (isDarkTheme) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    }
                )
            }

            ShareMeTheme(darkTheme = isDarkTheme) {
                ShareMeApp(
                    viewModel = shareMeViewModel,
                    isDarkTheme = isDarkTheme
                )
            }
        }
    }
}

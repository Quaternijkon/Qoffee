package com.qoffee

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Surface
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import com.qoffee.ui.AppViewModel
import com.qoffee.ui.QoffeeApp
import com.qoffee.ui.theme.QoffeeTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        setContent {
            val appViewModel: AppViewModel = hiltViewModel()
            val appUiState by appViewModel.uiState.collectAsStateWithLifecycle()
            QoffeeTheme(themeStyle = appUiState.settings.themeStyle) {
                Surface(color = Color.Transparent) {
                    QoffeeApp(appViewModel = appViewModel)
                }
            }
        }
    }
}

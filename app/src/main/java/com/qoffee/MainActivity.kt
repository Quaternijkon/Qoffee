package com.qoffee

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import com.qoffee.ui.QoffeeApp
import com.qoffee.ui.theme.QoffeeTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            QoffeeTheme {
                Surface {
                    QoffeeApp()
                }
            }
        }
    }
}

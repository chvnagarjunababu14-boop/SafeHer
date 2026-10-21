package com.safeher.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.safeher.app.ui.screens.home.HomeScreen
import com.safeher.app.ui.theme.SafeHerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SafeHerTheme {
                HomeScreen()
            }
        }
    }
}

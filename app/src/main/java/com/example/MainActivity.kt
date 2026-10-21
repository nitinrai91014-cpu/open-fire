package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.ui.OpenFireApp
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.GameViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: GameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                OpenFireApp(viewModel = viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // If coming back from background, resume appropriate screen audio
        val screen = viewModel.currentScreen.value
        when (screen) {
            com.example.model.Screen.WELCOME,
            com.example.model.Screen.MENU,
            com.example.model.Screen.SETTINGS,
            com.example.model.Screen.GUIDE,
            com.example.model.Screen.PROFILE,
            com.example.model.Screen.ABOUT -> {
                viewModel.audioManager.startMusic(viewModel.settings.value.musicVolume * 0.35f)
            }
            else -> {}
        }
    }

    override fun onPause() {
        super.onPause()
        viewModel.audioManager.stopAllLoops()
        viewModel.ttsManager.stop()
    }
}

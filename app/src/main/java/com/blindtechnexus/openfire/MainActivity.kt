package com.blindtechnexus.openfire

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.blindtechnexus.openfire.ui.OpenFireApp
import com.blindtechnexus.openfire.ui.theme.MyApplicationTheme
import com.blindtechnexus.openfire.viewmodel.GameViewModel

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
            com.blindtechnexus.openfire.model.Screen.WELCOME,
            com.blindtechnexus.openfire.model.Screen.MENU,
            com.blindtechnexus.openfire.model.Screen.SETTINGS,
            com.blindtechnexus.openfire.model.Screen.GUIDE,
            com.blindtechnexus.openfire.model.Screen.PROFILE,
            com.blindtechnexus.openfire.model.Screen.ABOUT -> {
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

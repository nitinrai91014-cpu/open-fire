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
        // Restart the correct screen audio (and the battle fire loop) using the
        // saved volume settings — never a screen-scaled copy of them.
        viewModel.onAppResumed()
    }

    override fun onPause() {
        super.onPause()
        viewModel.onAppPaused()
    }
}

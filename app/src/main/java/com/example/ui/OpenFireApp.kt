package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.example.model.ControlMode
import com.example.model.Screen
import com.example.ui.screens.AboutScreen
import com.example.ui.screens.BattleScreen
import com.example.ui.screens.GuideScreen
import com.example.ui.screens.JumpScreen
import com.example.ui.screens.LandScreen
import com.example.ui.screens.MenuScreen
import com.example.ui.screens.ParaScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.ReaderScreen
import com.example.ui.screens.ResultScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.WelcomeScreen
import com.example.ui.theme.DarkBg
import com.example.viewmodel.GameViewModel
import kotlin.math.abs

@Composable
fun OpenFireApp(viewModel: GameViewModel) {
    val currentScreen by viewModel.currentScreen.collectAsState()
    val settings by viewModel.settings.collectAsState()

    // Handle Android system back button properly
    BackHandler(enabled = currentScreen != Screen.WELCOME && currentScreen != Screen.MENU) {
        if (currentScreen == Screen.BATTLE) {
            viewModel.exitBattle()
        } else {
            viewModel.navigateTo(Screen.MENU)
        }
    }

    val gestureEnabled = settings.mode == ControlMode.GESTURES || settings.mode == ControlMode.BOTH

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = DarkBg
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(DarkBg)
                .pointerInput(gestureEnabled, currentScreen) {
                    if (!gestureEnabled) return@pointerInput

                    awaitEachGesture {
                        val down = awaitFirstDown(pass = PointerEventPass.Initial)
                        var maxFingers = 1
                        val startPos = down.position
                        var bestDx = 0f
                        var bestDy = 0f
                        var maxDelta = 0f

                        do {
                            val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                            val pointerCount = event.changes.size
                            if (pointerCount > maxFingers) {
                                maxFingers = pointerCount
                            }

                            for (change in event.changes) {
                                val dx = change.position.x - startPos.x
                                val dy = change.position.y - startPos.y
                                val dist = kotlin.math.sqrt(dx * dx + dy * dy)
                                if (dist > maxDelta) {
                                    maxDelta = dist
                                    bestDx = dx
                                    bestDy = dy
                                }
                            }
                        } while (event.changes.any { it.pressed })

                        val threshold = if (maxFingers >= 2) 40f else 60f
                        if (maxDelta >= threshold) {
                            val dir = if (abs(bestDx) >= abs(bestDy)) {
                                if (bestDx > 0) "right" else "left"
                            } else {
                                if (bestDy > 0) "down" else "up"
                            }
                            viewModel.handleGesture(dir, maxFingers)
                        }
                    }
                },
            contentAlignment = Alignment.TopCenter
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
            ) {
                when (currentScreen) {
                    Screen.WELCOME -> WelcomeScreen(viewModel)
                    Screen.MENU -> MenuScreen(viewModel)
                    Screen.READER -> ReaderScreen(viewModel)
                    Screen.JUMP -> JumpScreen(viewModel)
                    Screen.PARA -> ParaScreen(viewModel)
                    Screen.LAND -> LandScreen(viewModel)
                    Screen.BATTLE -> BattleScreen(viewModel)
                    Screen.SETTINGS -> SettingsScreen(viewModel)
                    Screen.GUIDE -> GuideScreen(viewModel)
                    Screen.PROFILE -> ProfileScreen(viewModel)
                    Screen.ABOUT -> AboutScreen(viewModel)
                    Screen.RESULT -> ResultScreen(viewModel)
                }
            }
        }
    }
}

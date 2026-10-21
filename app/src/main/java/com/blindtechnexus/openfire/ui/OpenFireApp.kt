package com.blindtechnexus.openfire.ui

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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.blindtechnexus.openfire.model.ControlMode
import com.blindtechnexus.openfire.model.Screen
import com.blindtechnexus.openfire.ui.screens.AboutScreen
import com.blindtechnexus.openfire.ui.screens.BattleScreen
import com.blindtechnexus.openfire.ui.screens.ChangelogScreen
import com.blindtechnexus.openfire.ui.screens.GuideScreen
import com.blindtechnexus.openfire.ui.screens.JumpScreen
import com.blindtechnexus.openfire.ui.screens.LandScreen
import com.blindtechnexus.openfire.ui.screens.MenuScreen
import com.blindtechnexus.openfire.ui.screens.ParaScreen
import com.blindtechnexus.openfire.ui.screens.ProfileScreen
import com.blindtechnexus.openfire.ui.screens.ReaderScreen
import com.blindtechnexus.openfire.ui.screens.ResultScreen
import com.blindtechnexus.openfire.ui.screens.SettingsScreen
import com.blindtechnexus.openfire.ui.screens.WelcomeScreen
import com.blindtechnexus.openfire.ui.theme.DarkBg
import com.blindtechnexus.openfire.viewmodel.GameViewModel
import kotlin.math.abs

@Composable
fun OpenFireApp(viewModel: GameViewModel) {
    val currentScreen by viewModel.currentScreen.collectAsState()
    val settings by viewModel.settings.collectAsState()
    var lastUpSwipeTimestamp by remember { mutableLongStateOf(0L) }

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

                            if (dir == "up" && currentScreen == Screen.BATTLE && maxFingers == 1) {
                                val now = System.currentTimeMillis()
                                if (now - lastUpSwipeTimestamp < 650L) {
                                    // Double swipe up -> Deploy Gloo Wall (100 coins)
                                    lastUpSwipeTimestamp = 0L
                                    viewModel.deployGlooWall()
                                } else {
                                    lastUpSwipeTimestamp = now
                                    viewModel.handleGesture(dir, maxFingers)
                                }
                            } else {
                                viewModel.handleGesture(dir, maxFingers)
                            }
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
                    Screen.CHANGELOG -> ChangelogScreen(viewModel)
                    Screen.RESULT -> ResultScreen(viewModel)
                }
            }
        }
    }
}

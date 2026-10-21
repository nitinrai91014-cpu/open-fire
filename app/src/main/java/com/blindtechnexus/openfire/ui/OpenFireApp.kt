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
import kotlin.math.sqrt

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
                        val firstDown = awaitFirstDown(
                            requireUnconsumed = false,
                            pass = PointerEventPass.Initial
                        )

                        // Track every finger from the exact place it touched
                        // down. Measuring all fingers from the first finger's
                        // position made a three-finger swipe register a bogus
                        // direction as soon as the fingers were spread apart,
                        // which is why three-finger up/down gestures failed
                        // intermittently. Per-pointer deltas fix that.
                        val startPositions = mutableMapOf(firstDown.id to firstDown.position)
                        var maxFingers = 1
                        var bestDx = 0f
                        var bestDy = 0f
                        var maxDelta = 0f

                        while (true) {
                            val event = awaitPointerEvent(pass = PointerEventPass.Initial)

                            val pressedNow = event.changes.count { it.pressed }
                            if (pressedNow > maxFingers) {
                                maxFingers = pressedNow
                            }

                            for (change in event.changes) {
                                val start = startPositions.getOrPut(change.id) { change.position }
                                val dx = change.position.x - start.x
                                val dy = change.position.y - start.y
                                val dist = sqrt(dx * dx + dy * dy)
                                if (dist > maxDelta) {
                                    maxDelta = dist
                                    bestDx = dx
                                    bestDy = dy
                                }
                            }

                            if (event.changes.none { it.pressed }) break
                        }

                        // Multi-finger gestures use a slightly smaller threshold
                        // because several fingers rarely travel in a perfect line.
                        val threshold = if (maxFingers >= 2) 36f else 60f
                        if (maxDelta >= threshold) {
                            val dir = if (abs(bestDx) >= abs(bestDy)) {
                                if (bestDx > 0) "right" else "left"
                            } else {
                                if (bestDy > 0) "down" else "up"
                            }

                            // Every recognised swipe is routed once. A second
                            // fast up-swipe is simply another shot — it must
                            // never silently spend 100 coins on a Gloo Wall.
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
                    Screen.CHANGELOG -> ChangelogScreen(viewModel)
                    Screen.RESULT -> ResultScreen(viewModel)
                }
            }
        }
    }
}

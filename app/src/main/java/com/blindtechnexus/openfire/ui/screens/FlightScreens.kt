package com.blindtechnexus.openfire.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blindtechnexus.openfire.model.Screen
import com.blindtechnexus.openfire.ui.components.ButtonType
import com.blindtechnexus.openfire.ui.components.GameButton
import com.blindtechnexus.openfire.ui.components.GameCard
import com.blindtechnexus.openfire.ui.components.HintBox
import com.blindtechnexus.openfire.ui.components.LogBox
import com.blindtechnexus.openfire.viewmodel.GameViewModel

/**
 * The flight sequence (screen reader, airplane, parachute, landing).
 * Gestures are always active: swipe up advances, swipe down goes back.
 * The buttons are kept as an accessible alternative for touch exploration.
 */
@Composable
fun ReaderScreen(viewModel: GameViewModel) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        GameCard {
            Text(
                text = viewModel.t("readerTitle"),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.semantics { heading() }
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = viewModel.t("readerMsg"),
                fontSize = 16.sp,
                color = Color.White,
                lineHeight = 22.sp
            )
        }

        Spacer(modifier = Modifier.height(14.dp))
        HintBox(text = "${viewModel.t("readerUp")}\n${viewModel.t("readerDown")}")
        Spacer(modifier = Modifier.height(14.dp))

        GameButton(
            text = viewModel.t("continueBtn"),
            onClick = { viewModel.continueToJump() },
            type = ButtonType.PRIMARY,
            testTag = "reader_continue_button"
        )
        Spacer(modifier = Modifier.height(12.dp))
        GameButton(
            text = viewModel.t("backToMenu"),
            onClick = { viewModel.navigateTo(Screen.MENU) },
            type = ButtonType.DANGER,
            testTag = "reader_back_button"
        )
    }
}

@Composable
fun JumpScreen(viewModel: GameViewModel) {
    val gameState by viewModel.gameState.collectAsState()
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        GameButton(
            text = viewModel.t("backToMenu"),
            onClick = { viewModel.navigateTo(Screen.MENU) },
            type = ButtonType.DANGER,
            testTag = "jump_back_button"
        )
        Spacer(modifier = Modifier.height(14.dp))

        GameCard {
            Text(
                text = viewModel.t("jumpMsg"),
                fontSize = 16.sp,
                color = Color.White,
                lineHeight = 22.sp
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        LogBox(text = gameState.log)
        Spacer(modifier = Modifier.height(12.dp))
        HintBox(text = "${viewModel.t("readerUp")} / ${viewModel.t("readerDown")}")
        Spacer(modifier = Modifier.height(14.dp))

        GameButton(
            text = viewModel.t("jump"),
            onClick = { viewModel.doJump() },
            type = ButtonType.PRIMARY,
            testTag = "do_jump_button"
        )
    }
}

@Composable
fun ParaScreen(viewModel: GameViewModel) {
    val gameState by viewModel.gameState.collectAsState()
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        GameButton(
            text = viewModel.t("backToMenu"),
            onClick = { viewModel.navigateTo(Screen.MENU) },
            type = ButtonType.DANGER,
            testTag = "para_back_button"
        )
        Spacer(modifier = Modifier.height(14.dp))

        GameCard {
            Text(
                text = viewModel.t("paraMsg"),
                fontSize = 16.sp,
                color = Color.White,
                lineHeight = 22.sp
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        LogBox(text = gameState.log)
        Spacer(modifier = Modifier.height(12.dp))
        HintBox(text = "${viewModel.t("readerUp")} / ${viewModel.t("readerDown")}")
        Spacer(modifier = Modifier.height(14.dp))

        GameButton(
            text = viewModel.t("openPara"),
            onClick = { viewModel.doPara() },
            type = ButtonType.PRIMARY,
            testTag = "open_para_button"
        )
    }
}

@Composable
fun LandScreen(viewModel: GameViewModel) {
    val gameState by viewModel.gameState.collectAsState()
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        GameButton(
            text = viewModel.t("backToMenu"),
            onClick = { viewModel.navigateTo(Screen.MENU) },
            type = ButtonType.DANGER,
            testTag = "land_back_button"
        )
        Spacer(modifier = Modifier.height(14.dp))

        GameCard {
            Text(
                text = viewModel.t("landMsg"),
                fontSize = 16.sp,
                color = Color.White,
                lineHeight = 22.sp
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        LogBox(text = gameState.log)
        Spacer(modifier = Modifier.height(12.dp))
        HintBox(text = "${viewModel.t("readerUp")} / ${viewModel.t("readerDown")}")
        Spacer(modifier = Modifier.height(14.dp))

        GameButton(
            text = viewModel.t("land"),
            onClick = { viewModel.doLand() },
            type = ButtonType.PRIMARY,
            testTag = "do_land_button"
        )
    }
}

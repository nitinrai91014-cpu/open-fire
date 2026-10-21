package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Strings
import com.example.model.ControlMode
import com.example.model.Screen
import com.example.ui.components.ButtonType
import com.example.ui.components.GameButton
import com.example.ui.components.GameCard
import com.example.ui.components.HintBox
import com.example.ui.components.TipBox
import com.example.ui.theme.CardBorder
import com.example.ui.theme.TextMuted
import com.example.viewmodel.GameViewModel

@Composable
fun GuideScreen(viewModel: GameViewModel) {
    val settings by viewModel.settings.collectAsState()
    val scrollState = rememberScrollState()
    val guideLines = Strings.getGuide(settings.lang)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        GameCard {
            Text(
                text = viewModel.t("guideTitle"),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.semantics { heading() }
            )
            Spacer(modifier = Modifier.height(14.dp))
            guideLines.forEachIndexed { index, line ->
                Text(
                    text = "${index + 1}. $line",
                    fontSize = 15.sp,
                    color = Color.White,
                    lineHeight = 22.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        GameButton(
            text = viewModel.t("back"),
            onClick = { viewModel.navigateTo(Screen.MENU) },
            type = ButtonType.PRIMARY,
            testTag = "guide_back_button"
        )
    }
}

@Composable
fun ProfileScreen(viewModel: GameViewModel) {
    val settings by viewModel.settings.collectAsState()
    val stats by viewModel.stats.collectAsState()
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        GameCard {
            Text(
                text = viewModel.t("profileTitle"),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.semantics { heading() }
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(text = "${viewModel.t("name")}: ${settings.name}", fontSize = 16.sp, color = Color.White)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "${viewModel.t("level")}: ${stats.level}", fontSize = 16.sp, color = Color.White)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "${viewModel.t("needWin")}: ${stats.needWinsForNext}", fontSize = 16.sp, color = Color.White)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "${viewModel.t("coins")}: ${stats.coins}", fontSize = 16.sp, color = Color.White)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "${viewModel.t("games")}: ${stats.games}", fontSize = 16.sp, color = Color.White)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "${viewModel.t("wins")}: ${stats.wins}", fontSize = 16.sp, color = Color.White)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "${viewModel.t("kills")}: ${stats.kills}", fontSize = 16.sp, color = Color.White)
        }

        Spacer(modifier = Modifier.height(16.dp))

        GameButton(
            text = viewModel.t("back"),
            onClick = { viewModel.navigateTo(Screen.MENU) },
            type = ButtonType.PRIMARY,
            testTag = "profile_back_button"
        )
    }
}

@Composable
fun AboutScreen(viewModel: GameViewModel) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        GameCard {
            Text(
                text = viewModel.t("aboutTitle"),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.semantics { heading() }
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = viewModel.t("aboutText"),
                fontSize = 16.sp,
                color = Color.White,
                lineHeight = 22.sp
            )
            Spacer(modifier = Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(CardBorder)
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = viewModel.t("developed"),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = viewModel.t("version"),
                fontSize = 14.sp,
                color = TextMuted
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        GameButton(
            text = viewModel.t("back"),
            onClick = { viewModel.navigateTo(Screen.MENU) },
            type = ButtonType.PRIMARY,
            testTag = "about_back_button"
        )
    }
}

@Composable
fun ResultScreen(viewModel: GameViewModel) {
    val settings by viewModel.settings.collectAsState()
    val stats by viewModel.stats.collectAsState()
    val result by viewModel.resultData.collectAsState()
    val scrollState = rememberScrollState()

    val gesturesAllowed = settings.mode == ControlMode.GESTURES || settings.mode == ControlMode.BOTH
    val buttonsAllowed = settings.mode == ControlMode.BUTTONS || settings.mode == ControlMode.BOTH

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        GameCard {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (result.won) viewModel.t("victory") else viewModel.t("gameOver"),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() }
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (result.won) viewModel.t("victoryMsg") else viewModel.t("defeatMsg"),
                    fontSize = 16.sp,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
            }
        }

        if (result.levelUp) {
            Spacer(modifier = Modifier.height(12.dp))
            TipBox(text = "${viewModel.t("levelUp")} ${viewModel.t("newLevel")}: ${result.level}")
        }

        Spacer(modifier = Modifier.height(12.dp))

        GameCard {
            Text(
                text = "${viewModel.t("earned")}: ${result.coins}",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "${viewModel.t("hits")}: ${result.hits} / ${result.shots}",
                fontSize = 16.sp,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "${viewModel.t("devilShots")}: ${result.devilShots}",
                fontSize = 16.sp,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "${viewModel.t("yourHealth")}: ${maxOf(0, result.hp)}",
                fontSize = 16.sp,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "${viewModel.t("level")}: ${stats.level}",
                fontSize = 16.sp,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "${viewModel.t("needWin")}: ${stats.needWinsForNext}",
                fontSize = 16.sp,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (buttonsAllowed) {
            GameButton(
                text = viewModel.t("playAgain"),
                onClick = { viewModel.playAgain() },
                type = ButtonType.PRIMARY,
                testTag = "play_again_button"
            )
            Spacer(modifier = Modifier.height(12.dp))
            GameButton(
                text = viewModel.t("mainMenu"),
                onClick = { viewModel.navigateTo(Screen.MENU) },
                type = ButtonType.DANGER,
                testTag = "main_menu_button"
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (gesturesAllowed) {
            HintBox(text = "${viewModel.t("readerUp")}: ${viewModel.t("playAgain")}\n${viewModel.t("readerDown")}: ${viewModel.t("mainMenu")}")
        }
    }
}

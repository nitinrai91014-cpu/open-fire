package com.blindtechnexus.openfire.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blindtechnexus.openfire.model.Screen
import com.blindtechnexus.openfire.ui.components.ButtonType
import com.blindtechnexus.openfire.ui.components.GameButton
import com.blindtechnexus.openfire.ui.components.GameCard
import com.blindtechnexus.openfire.ui.components.StatBadge
import com.blindtechnexus.openfire.viewmodel.GameViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MenuScreen(viewModel: GameViewModel) {
    val settings by viewModel.settings.collectAsState()
    val stats by viewModel.stats.collectAsState()
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        GameCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = viewModel.t("title"),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() }
                )
                Spacer(modifier = Modifier.height(8.dp))
                val welcomeText = "${viewModel.t("welcome")}, ${settings.name}"
                Text(
                    text = welcomeText,
                    fontSize = 17.sp,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.semantics {
                        contentDescription = welcomeText
                    }
                )
                Spacer(modifier = Modifier.height(14.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.Center,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    StatBadge(label = viewModel.t("coins"), value = stats.coins.toString())
                    StatBadge(label = viewModel.t("round"), value = stats.round.toString())
                    StatBadge(label = viewModel.t("level"), value = stats.level.toString())
                    StatBadge(label = viewModel.t("wins"), value = stats.wins.toString())
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        GameButton(
            text = viewModel.t("play"),
            onClick = { viewModel.startNewGame() },
            type = ButtonType.PRIMARY,
            testTag = "play_game_button"
        )
        Spacer(modifier = Modifier.height(12.dp))
        GameButton(
            text = viewModel.t("guide"),
            onClick = { viewModel.navigateTo(Screen.GUIDE) },
            testTag = "guide_button"
        )
        Spacer(modifier = Modifier.height(12.dp))
        GameButton(
            text = viewModel.t("profile"),
            onClick = { viewModel.navigateTo(Screen.PROFILE) },
            testTag = "profile_button"
        )
        Spacer(modifier = Modifier.height(12.dp))
        GameButton(
            text = viewModel.t("settings"),
            onClick = { viewModel.navigateTo(Screen.SETTINGS) },
            testTag = "settings_button"
        )
        Spacer(modifier = Modifier.height(12.dp))
        // Changelog button placed ABOVE About button as required
        GameButton(
            text = viewModel.t("changelog"),
            onClick = { viewModel.navigateTo(Screen.CHANGELOG) },
            type = ButtonType.WARN,
            testTag = "changelog_button"
        )
        Spacer(modifier = Modifier.height(12.dp))
        GameButton(
            text = viewModel.t("about"),
            onClick = { viewModel.navigateTo(Screen.ABOUT) },
            testTag = "about_button"
        )
    }
}

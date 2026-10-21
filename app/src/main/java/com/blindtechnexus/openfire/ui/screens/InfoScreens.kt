package com.blindtechnexus.openfire.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blindtechnexus.openfire.data.Strings
import com.blindtechnexus.openfire.model.ControlMode
import com.blindtechnexus.openfire.model.Screen
import com.blindtechnexus.openfire.ui.components.ButtonType
import com.blindtechnexus.openfire.ui.components.GameButton
import com.blindtechnexus.openfire.ui.components.GameCard
import com.blindtechnexus.openfire.ui.components.HintBox
import com.blindtechnexus.openfire.ui.components.TipBox
import com.blindtechnexus.openfire.ui.theme.CardBorder
import com.blindtechnexus.openfire.ui.theme.TextAccent
import com.blindtechnexus.openfire.ui.theme.TextMuted
import com.blindtechnexus.openfire.ui.theme.WarnBright
import com.blindtechnexus.openfire.viewmodel.GameViewModel

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
            Text(text = "${viewModel.t("round")}: ${stats.round}", fontSize = 16.sp, color = WarnBright, fontWeight = FontWeight.Bold)
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
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    fun openUrl(url: String) {
        try {
            val fullUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) "https://$url" else url
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(fullUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
    }

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
                color = TextAccent,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Community and Contact links as requested
        GameButton(
            text = viewModel.t("joinTelegram"),
            onClick = { openUrl("t.me/blindtechnexus") },
            type = ButtonType.DEFAULT,
            testTag = "join_telegram_button"
        )

        Spacer(modifier = Modifier.height(12.dp))

        GameButton(
            text = viewModel.t("contactWhatsApp"),
            onClick = { openUrl("wa.me/9779741825274") },
            type = ButtonType.PRIMARY,
            testTag = "contact_whatsapp_button"
        )

        Spacer(modifier = Modifier.height(14.dp))

        GameButton(
            text = viewModel.t("back"),
            onClick = { viewModel.navigateTo(Screen.MENU) },
            type = ButtonType.DANGER,
            testTag = "about_back_button"
        )
    }
}

@Composable
fun ChangelogScreen(viewModel: GameViewModel) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        GameCard {
            Text(
                text = viewModel.t("changelogTitle"),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.semantics { heading() }
            )
            Spacer(modifier = Modifier.height(14.dp))

            // Version 1.2
            Text(
                text = "Version 1.2 (Current)",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = WarnBright
            )
            Spacer(modifier = Modifier.height(8.dp))
            val v12Notes = listOf(
                "Sound Player Mixer: Re-engineered reliable audio architecture with dedicated streaming channels for victory, defeat, parachute, airplane, and ambient music without buffer starvation.",
                "Sound Overlap Fix: Screen transition sounds (victory menu, defeat menu, parachute) immediately stop on swipe-up or navigation.",
                "Evil Character Walking & 3D Audio: Realistically approaching spatial footsteps with distance attenuation and binaural lateral movement.",
                "Combat Range & Hit Balance: Hits between 70%-100% proximity deal 30% damage; hits between 40%-70% inflict light 10% damage; hits below 40% miss. Devil and player have identical symmetrical power.",
                "Zero-HP Death Rule: Player and devil die only when health reaches exactly zero.",
                "Level-Based HP Progression: 200 HP up to level 10, 300 HP up to level 20, 500 HP up to level 25, 800 HP from level 25 to 30.",
                "Round Cycle: Completing level 30 finishes the round and begins the next round.",
                "Pistol Reload Fix: Fixed 6-bullet pistol magazine; requires reload only after all 6 rounds are discharged.",
                "Reload Audio: Integrated authentic 'gun reload.mp3' sound effect.",
                "Gloo Wall Gesture: Double swipe-up deploys Gloo Wall (100 coins).",
                "Voice Announcements: System announces 'Not enough coins' whenever coins are insufficient for reload, Gloo Wall, or medkit.",
                "Screen Reader & TTS Fix: Resolved Devanagari Hindi and Nepali pronunciation issues and cleaned up text layout semantics.",
                "Community Links: Direct Telegram channel and WhatsApp contact buttons added in About section."
            )
            v12Notes.forEach { note ->
                Text(
                    text = "• $note",
                    fontSize = 14.sp,
                    color = Color.White,
                    lineHeight = 20.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
            }

            Spacer(modifier = Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(CardBorder)
            )
            Spacer(modifier = Modifier.height(14.dp))

            // Version 1.0
            Text(
                text = "Version 1.0",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextAccent
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Initial version: Audio battle royale with accessible controls, synthetic sound backup, basic tactical guns, and screen reader guidance.",
                fontSize = 14.sp,
                color = TextMuted,
                lineHeight = 20.sp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        GameButton(
            text = viewModel.t("back"),
            onClick = { viewModel.navigateTo(Screen.MENU) },
            type = ButtonType.PRIMARY,
            testTag = "changelog_back_button"
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

        if (result.roundComplete) {
            Spacer(modifier = Modifier.height(12.dp))
            TipBox(text = "${viewModel.t("roundCompleted")} ${viewModel.t("round")}: ${result.round}")
        } else if (result.levelUp) {
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
                text = "${viewModel.t("round")}: ${stats.round}",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = WarnBright
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

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
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
import com.blindtechnexus.openfire.ui.theme.CardBg
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
    var showClearDialog by remember { mutableStateOf(false) }

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
            text = viewModel.t("clearStats"),
            onClick = {
                showClearDialog = true
                // Read the warning aloud before the user decides.
                viewModel.speak("${viewModel.t("clearStatsTitle")} ${viewModel.t("clearStatsMessage")}")
            },
            type = ButtonType.DANGER,
            testTag = "clear_stats_button"
        )

        Spacer(modifier = Modifier.height(12.dp))

        GameButton(
            text = viewModel.t("back"),
            onClick = { viewModel.navigateTo(Screen.MENU) },
            type = ButtonType.PRIMARY,
            testTag = "profile_back_button"
        )
    }

    // Confirmation is mandatory: nothing is erased until the user confirms.
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = {
                Text(
                    text = viewModel.t("clearStatsTitle"),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = viewModel.t("clearStatsMessage"),
                    fontSize = 15.sp,
                    lineHeight = 21.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDialog = false
                        viewModel.clearStats()
                    },
                    modifier = Modifier.testTag("clear_stats_confirm_button")
                ) {
                    Text(
                        text = viewModel.t("confirmClear"),
                        color = WarnBright,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showClearDialog = false },
                    modifier = Modifier.testTag("clear_stats_cancel_button")
                ) {
                    Text(text = viewModel.t("cancel"), color = TextAccent)
                }
            },
            containerColor = CardBg,
            titleContentColor = Color.White,
            textContentColor = Color.White
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

            // Version 1.3
            Text(
                text = "Version 1.3 (Current)",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = WarnBright
            )
            Spacer(modifier = Modifier.height(8.dp))
            val v13Notes = listOf(
                "Audio Volume Fix: Music, SFX and TTS now keep exactly the volume saved in Settings across screens, games, app restarts and background/foreground switches. The old double-scaling and the music level being overwritten by a lower playback value are fixed.",
                "Four-Zone Damage: In the 50 meter arena, shots under 40% proximity miss, 40% to 70% takes 10% health, 70% to 90% takes 15%, and 90% to 100% takes 20%.",
                "Second Chance: Your first zero health restores half your maximum health. If it reaches zero again you are defeated. The devil gets the same second chance.",
                "Devil Survival Items: After its second chance the devil activates a Gloo Wall, a cover and a medkit. It spends one item every time its health reaches zero and dies for good only after all three are exhausted.",
                "Continuous Enemy Fire: The devil now fires throughout the battle instead of only at point-blank range.",
                "High-Speed Flee: Below half of the health it received, the devil flees at high speed with 3D receding footsteps and a lower Doppler pitch — hits still count while it runs.",
                "Gun Damage & Recovery: Three consecutive point-blank enemy hits damage the gun in hand. A three-finger left swipe or the new Recovery button repairs every gun for 300 coins.",
                "Three-Finger Gesture Fix: Each finger is now tracked from its own touch-down point, so three-finger up, down and left swipes no longer fail or register the wrong direction.",
                "Clear My Stats: The Profile screen can wipe all stats after a confirmation warning, resetting every counter to zero and level and round to 1.",
                "Progression Fix: 10 wins now always promote one level and 30 levels always complete one round, tracked with an explicit per-level win counter."
            )
            v13Notes.forEach { note ->
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

            // Version 1.2
            Text(
                text = "Version 1.2",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = WarnBright
            )
            Spacer(modifier = Modifier.height(8.dp))
            val v12Notes = listOf(
                "Sound Player Mixer: Re-engineered reliable audio architecture with dedicated streaming channels for victory, defeat, parachute, airplane, and ambient music without buffer starvation.",
                "Never-Dropped SFX: Sound effects that are requested before they finish loading are queued and played automatically; failed loads retry and fall back to generated audio — the reload sound (gun-reload.mp3) now always plays.",
                "Money-Spend Mixer: Every coin payment (reload 30, Gloo Wall 100, medkit 100) plays the money-spend.mp3 coin sound mixed together with the action's own sound — reload now plays money-spend and gun-reload together.",
                "Cover Sound Effect: A brand-new cover-sound.mp3 effect has been added to the assets and plays together with the gloo wall sound whenever a Gloo Wall is deployed.",
                "Gloo Wall Gesture: Swiping up with two fingers deploys the Gloo Wall for 100 coins.",
                "Sound Overlap Fix: Screen transition sounds (victory menu, defeat menu, parachute) immediately stop on swipe-up or navigation.",
                "Evil Character Walking & 3D Audio: Realistically approaching spatial footsteps with distance attenuation and binaural lateral movement.",
                "Combat Range & Hit Balance (superseded by the Version 1.3 four-zone rules): 0% to 40% missed, 40% to 70% removed 10% of the enemy's health and 70% to 100% removed 20%. Devil and player used the same symmetrical rules.",
                "Zero-HP Death Rule: Player and devil die only when health reaches exactly zero — no fixed number of hits can end the fight.",
                "Level-Based HP Progression: 200 HP up to level 10, 300 HP up to level 20, 500 HP up to level 25, 800 HP from level 26 to 30.",
                "Round Cycle: Completing level 30 finishes the round and begins the next round.",
                "Six-Bullet Magazines: The pistol, assault rifle and shotgun each hold exactly 6 rounds; one round per shot; reload required only after all six are fired (stale-snapshot ammo bugs fixed).",
                "Voice Announcements: System announces 'Not enough coins' whenever coins are insufficient for reload, Gloo Wall, or medkit, and 'Magazine already full' when reloading a full gun.",
                "Screen Reader & TTS Fix: English pronunciation no longer distorted — voices are strictly matched to the selected language (mismatched Nepali/Hindi voices are never applied to English), with proper locale fallback and symbol-free announcements for all three languages.",
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

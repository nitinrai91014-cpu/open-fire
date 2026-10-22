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
import com.blindtechnexus.openfire.model.Extension
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
    val inventory by viewModel.inventory.collectAsState()
    val scrollState = rememberScrollState()
    var showClearDialog by remember { mutableStateOf(false) }

    val weapons = inventory.ownedWeapons()
    val extensions = Extension.entries.filter { inventory.extensionOwned(it) }

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
            Text(
                text = "${viewModel.t("round")}: ${stats.round}",
                fontSize = 16.sp,
                color = WarnBright,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "${viewModel.t("level")}: ${stats.level}", fontSize = 16.sp, color = Color.White)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "${viewModel.t("needWin")}: ${stats.needWinsForNext}",
                fontSize = 16.sp,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "${viewModel.t("coins")}: ${stats.coins}",
                fontSize = 16.sp,
                color = WarnBright,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "${viewModel.t("games")}: ${stats.games}", fontSize = 16.sp, color = Color.White)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "${viewModel.t("wins")}: ${stats.wins}", fontSize = 16.sp, color = Color.White)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "${viewModel.t("kills")}: ${stats.kills}", fontSize = 16.sp, color = Color.White)
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Everything the player owns is listed here, exactly as announced by
        // the spoken status.
        GameCard {
            Text(
                text = viewModel.t("profileWeapons"),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = WarnBright,
                modifier = Modifier.semantics { heading() }
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (weapons.isEmpty()) {
                    viewModel.t("noneOwned")
                } else {
                    weapons.joinToString(", ") { viewModel.t(it.nameKey) }
                },
                fontSize = 15.sp,
                color = Color.White,
                lineHeight = 21.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "${viewModel.t("lastWeapon")}: " +
                    viewModel.t(inventory.startingWeapon().nameKey),
                fontSize = 14.sp,
                color = TextMuted
            )

            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = viewModel.t("profileDefences"),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = WarnBright,
                modifier = Modifier.semantics { heading() }
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "${inventory.medikits} ${viewModel.t("medkitsPlural")}, " +
                    "${inventory.glueWalls} ${viewModel.t("glueWallsPlural")}, " +
                    "${inventory.covers} ${viewModel.t("coversPlural")}",
                fontSize = 15.sp,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = viewModel.t("profileExtensions"),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = WarnBright,
                modifier = Modifier.semantics { heading() }
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (extensions.isEmpty()) {
                    viewModel.t("noneOwned")
                } else {
                    extensions.joinToString(", ") { extension ->
                        val state = if (inventory.extensionEnabled(extension)) {
                            viewModel.t("enabledState")
                        } else {
                            viewModel.t("disabledState")
                        }
                        "${viewModel.t(extension.nameKey)} ($state)"
                    }
                },
                fontSize = 15.sp,
                color = Color.White,
                lineHeight = 21.sp
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        GameButton(
            text = viewModel.t("clearStats"),
            onClick = {
                showClearDialog = true
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
            val fullUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
                "https://$url"
            } else {
                url
            }
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(fullUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
        }
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

            Text(
                text = "Version 2.0 (Current)",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = WarnBright
            )
            Spacer(modifier = Modifier.height(8.dp))
            val v20Notes = listOf(
                "Shop: a new Shop button opens a full shop before Play Game. It sells the pistol for 200 coins, the sniper rifle for 300, the knife for 250 and the sword for 340, with a Buy button or a Remove button protected by a confirmation.",
                "Defences: medikits (200 coins), glue walls (400 coins) and covers (350 coins) can be bought more than once and every use decreases the count by exactly one. The shop speaks your totals and reminds you that defences are repeatable.",
                "Extensions: automatic reload (50), automatic medikit under 50 health (80), automatic cover (100) and automatic glue wall (100). Each one can be removed after a confirmation and switched off or on; a disabled extension never triggers.",
                "Weapon powers: assault gun 10, sniper rifle 9, knife 8, sword 12, pistol 15 and short gun 6 (10 for Robert). Distance still decides the damage: miss below 40 percent, half from 40 to 70, 80 percent from 70 to 90 and full power from 90 to 100.",
                "Barriers: a cover stops 16 hits and breaks on the 17th. A glue wall stops every bullet and weapon and only breaks after three continuous sniper rifle hits from 90 to 100 percent range.",
                "Redesigned battle screen: one-finger and three-finger gestures and the old two-finger item gestures are gone. The new quick menu runs medikit, cover, glue wall, exit and play; two fingers up or down move the selection and a single tap activates it.",
                "Missing items in battle: you hear that you do not have the item, and a second tap buys it at the shop price and activates it immediately. Not enough coins is shown as a toast and spoken aloud.",
                "Menu protection: while you are navigating the quick menu every hit costs only 2 health.",
                "Rounds: 10 wins equal a level, 10 levels equal a round and the fifth round wins the whole match. Health is 300, 400, 500, 600 and 750 for rounds one to five.",
                "Robert: his medkits, covers, glue walls and weapons grow every round, he uses a medikit first then a cover then a glue wall below 60 health, he reloads freely after six shots, and his sniper stays locked in the first two rounds unless you raised a glue wall while he is under 100 health.",
                "Robert runs far away after two continuous hits in round one, three in round three, four in round four and five in round five.",
                "Automatic reload, remembered last weapon, free reloads, new sword, knife and barrier sounds, and the walking sound now plays at 75 percent of the TTS volume.",
                "Shop inventory, weapons, defences and extensions are now shown in the profile and in the spoken status.",
                "The result screen now waits four seconds before another game can be started.",
                "Storage moved to encrypted external storage for the whole profile, with strong integrity protection against editing.",
                "Nepali and Hindi translations and the complete user guide were rewritten for every new rule."
            )
            v20Notes.forEach { note ->
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

            Text(
                text = "Version 1.3",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextAccent
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "• Four-zone damage, second chance, devil survival items, continuous enemy fire, " +
                    "high speed flee, gun damage and 300 coin recovery, three-finger gesture fix, " +
                    "clear my stats and the 10 wins per level ladder.",
                fontSize = 14.sp,
                color = TextMuted,
                lineHeight = 20.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Version 1.2",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextAccent
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "• Reliable sound mixer, never dropped sound effects, coin sound mixed with every " +
                    "purchase, 3D footsteps, six bullet magazines and stable voice volume.",
                fontSize = 14.sp,
                color = TextMuted,
                lineHeight = 20.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Version 1.0",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextAccent
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "• Initial audio battle royale with accessible controls and screen reader guidance.",
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
    val stats by viewModel.stats.collectAsState()
    val result by viewModel.resultData.collectAsState()
    val ready by viewModel.resultReady.collectAsState()
    val scrollState = rememberScrollState()

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
                    text = when {
                        result.matchWon -> viewModel.t("matchWon")
                        result.won -> viewModel.t("victory")
                        else -> viewModel.t("gameOver")
                    },
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() }
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (result.matchWon) {
                        viewModel.t("matchWonMsg")
                    } else if (result.won) {
                        viewModel.t("victoryMsg")
                    } else {
                        viewModel.t("defeatMsg")
                    },
                    fontSize = 16.sp,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
            }
        }

        if (result.matchWon) {
            Spacer(modifier = Modifier.height(12.dp))
            TipBox(text = viewModel.t("matchWonMsg"))
        } else if (result.roundComplete) {
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
                text = "${viewModel.t("devilShots")}: ${result.robertShots}",
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

        Spacer(modifier = Modifier.height(12.dp))

        if (!ready) {
            HintBox(text = viewModel.t("resultWait"))
        } else {
            HintBox(text = "${viewModel.t("readerUp")}: ${viewModel.t("playAgain")}\n" +
                "${viewModel.t("readerDown")}: ${viewModel.t("mainMenu")}")
        }

        Spacer(modifier = Modifier.height(16.dp))

        GameButton(
            text = viewModel.t("playAgain"),
            onClick = { viewModel.playAgain() },
            type = ButtonType.PRIMARY,
            testTag = "play_again_button",
            enabled = ready
        )
        Spacer(modifier = Modifier.height(12.dp))
        GameButton(
            text = viewModel.t("mainMenu"),
            onClick = { viewModel.navigateTo(Screen.MENU) },
            type = ButtonType.DANGER,
            testTag = "main_menu_button"
        )
    }
}

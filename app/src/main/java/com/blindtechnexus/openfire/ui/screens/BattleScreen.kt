package com.blindtechnexus.openfire.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blindtechnexus.openfire.model.Combat
import com.blindtechnexus.openfire.model.ControlMode
import com.blindtechnexus.openfire.model.DEFAULT_GUNS
import com.blindtechnexus.openfire.model.HitZone
import com.blindtechnexus.openfire.ui.components.AlertBox
import com.blindtechnexus.openfire.ui.components.ButtonType
import com.blindtechnexus.openfire.ui.components.GameButton
import com.blindtechnexus.openfire.ui.components.GameCard
import com.blindtechnexus.openfire.ui.components.HintBox
import com.blindtechnexus.openfire.ui.components.HpBar
import com.blindtechnexus.openfire.ui.components.LogBox
import com.blindtechnexus.openfire.ui.components.RadarBar
import com.blindtechnexus.openfire.ui.components.TipBox
import com.blindtechnexus.openfire.ui.theme.BtnDanger
import com.blindtechnexus.openfire.ui.theme.TextMuted
import com.blindtechnexus.openfire.ui.theme.WarnBright
import com.blindtechnexus.openfire.viewmodel.GameViewModel
import kotlin.math.roundToInt

@Composable
fun BattleScreen(viewModel: GameViewModel) {
    val settings by viewModel.settings.collectAsState()
    val stats by viewModel.stats.collectAsState()
    val gameState by viewModel.gameState.collectAsState()
    val scrollState = rememberScrollState()

    val gesturesAllowed = settings.mode == ControlMode.GESTURES || settings.mode == ControlMode.BOTH
    val buttonsAllowed = settings.mode == ControlMode.BUTTONS || settings.mode == ControlMode.BOTH
    val gun = DEFAULT_GUNS[gameState.gunIndex]

    val devilHpShown = maxOf(0, gameState.devilHp)
    val playerHpShown = maxOf(0, gameState.playerHp)

    // Three distance zones: 0-40% miss, 40-70% light hit (10%), 70-100% proper hit (20%).
    val proxPercent = Combat.proximityOf(gameState.distance)
    val hitZone = Combat.zoneFor(proxPercent)
    val zoneText = when (hitZone) {
        HitZone.MISS -> viewModel.t("zoneMiss")
        HitZone.LIGHT -> viewModel.t("zoneLight")
        HitZone.HEAVY -> viewModel.t("zoneHeavy")
    }
    val zoneColor = when (hitZone) {
        HitZone.MISS -> TextMuted
        HitZone.LIGHT -> WarnBright
        HitZone.HEAVY -> Color(0xFFFF6B6B)
    }

    // One line per gun: six bullets each, active gun first.
    val ammoSummary = DEFAULT_GUNS.mapIndexed { i, g ->
        val marker = if (i == gameState.gunIndex) "> " else ""
        "$marker${viewModel.t(g.nameKey)}: ${gameState.ammo[i]}/${g.mag}"
    }.joinToString("   ")
    val ammoDesc = DEFAULT_GUNS.mapIndexed { i, g ->
        val active = if (i == gameState.gunIndex) " (${viewModel.t("fire")})" else ""
        "${viewModel.t(g.nameKey)} ${gameState.ammo[i]} of ${g.mag}$active"
    }.joinToString(", ")

    val coverTxt = if (gameState.devilCoverHp > 0) {
        " | ${viewModel.t("devilCover")}: ${gameState.devilCoverHp}/${gameState.devilCoverMax}"
    } else ""

    val stateTxt = when (gameState.devilState) {
        "retreat" -> " | ${viewModel.t("devilFled")}"
        "flank" -> " | ${viewModel.t("devilFlank")}"
        else -> ""
    }

    val devilDesc = "${viewModel.t("devil")} ${viewModel.t("hp")} $devilHpShown of ${gameState.devilMaxHp}, ${viewModel.t("distance")} ${gameState.distance.roundToInt()} ${viewModel.t("meters")}$coverTxt"
    val playerDesc = "${viewModel.t("you")} ${viewModel.t("hp")} $playerHpShown of ${gameState.playerMaxHp}, ${viewModel.t("ammo")} ${gameState.ammo[gameState.gunIndex]} of ${gun.mag}, ${viewModel.t(gun.nameKey)}, ${viewModel.t("coins")} ${stats.coins}"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        if (buttonsAllowed) {
            GameButton(
                text = viewModel.t("backToMenu"),
                onClick = { viewModel.exitBattle() },
                type = ButtonType.DANGER,
                testTag = "battle_back_menu_button"
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Devil Card
        GameCard(
            backgroundColor = BtnDanger,
            borderColor = Color(0xFFFF4D4D)
        ) {
            Text(
                text = "${viewModel.t("devil")} ${viewModel.t("hp")}: $devilHpShown/${gameState.devilMaxHp} | ${viewModel.t("distance")}: ${gameState.distance.roundToInt()} ${viewModel.t("meters")}$coverTxt$stateTxt",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.semantics {
                    contentDescription = devilDesc
                }
            )
            Spacer(modifier = Modifier.height(6.dp))
            HpBar(current = devilHpShown, max = gameState.devilMaxHp, isDevil = true)
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Player Card
        GameCard {
            Text(
                text = "${viewModel.t("you")} ${viewModel.t("hp")}: $playerHpShown/${gameState.playerMaxHp} | ${viewModel.t("ammo")}: ${gameState.ammo[gameState.gunIndex]}/${gun.mag}",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.semantics {
                    contentDescription = playerDesc
                }
            )
            Spacer(modifier = Modifier.height(6.dp))
            HpBar(current = playerHpShown, max = gameState.playerMaxHp, isDevil = false)
            Spacer(modifier = Modifier.height(8.dp))
            val coverLabel = if (gameState.inCover) " | " + viewModel.t("glooWall") else ""
            Text(
                text = viewModel.t(gun.nameKey) + " | " + viewModel.t("medkits") + ": " + gameState.medkits +
                    " | " + viewModel.t("coins") + ": " + stats.coins + coverLabel,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(6.dp))
            // Magazine status for all three guns (six bullets each).
            Text(
                text = ammoSummary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFFB8D4FF),
                modifier = Modifier.semantics {
                    contentDescription = ammoDesc
                }
            )
        }

        if (gameState.ammo[gameState.gunIndex] == 0) {
            Spacer(modifier = Modifier.height(12.dp))
            AlertBox(text = "${viewModel.t("outOfAmmo")} ${viewModel.t("reload")}.")
        }

        if (gameState.playerHp <= (gameState.playerMaxHp * 0.35f).roundToInt()) {
            Spacer(modifier = Modifier.height(12.dp))
            AlertBox(text = "${viewModel.t("yourHealth")}: $playerHpShown")
        }

        Spacer(modifier = Modifier.height(12.dp))
        TipBox(text = viewModel.t("aim"))
        Spacer(modifier = Modifier.height(12.dp))
        LogBox(text = gameState.log)
        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = viewModel.t("radar"),
            fontSize = 14.sp,
            color = TextMuted
        )
        Spacer(modifier = Modifier.height(4.dp))
        RadarBar(distance = gameState.distance, extraDesc = zoneText)
        Spacer(modifier = Modifier.height(6.dp))
        // Current hit zone with its exact damage rule (0-40 miss / 40-70 = 10% / 70-100 = 20%).
        Text(
            text = zoneText,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = zoneColor,
            modifier = Modifier.semantics {
                contentDescription = zoneText
            }
        )
        Spacer(modifier = Modifier.height(14.dp))

        if (gesturesAllowed) {
            HintBox(text = viewModel.t("gestureHint"))
            Spacer(modifier = Modifier.height(14.dp))
        }

        if (buttonsAllowed) {
            GameButton(
                text = viewModel.t("fire"),
                onClick = { viewModel.fire() },
                type = ButtonType.DANGER,
                testTag = "fire_button"
            )
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                GameButton(
                    text = "${viewModel.t("reload")} (30)",
                    onClick = { viewModel.doReload() },
                    modifier = Modifier.weight(1f),
                    testTag = "reload_button"
                )
                GameButton(
                    text = "${viewModel.t("medkit")} (100)",
                    onClick = { viewModel.useMedkit() },
                    modifier = Modifier.weight(1f),
                    testTag = "medkit_button"
                )
            }
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                GameButton(
                    text = "${viewModel.t("glooWall")} (100)",
                    onClick = { viewModel.toggleCover() },
                    modifier = Modifier.weight(1f),
                    testTag = "cover_button"
                )
                GameButton(
                    text = viewModel.t("switchGun"),
                    onClick = { viewModel.switchGun(1) },
                    modifier = Modifier.weight(1f),
                    testTag = "switch_gun_button"
                )
            }
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                GameButton(
                    text = viewModel.t("yourStatus"),
                    onClick = { viewModel.speakStatus(own = true) },
                    type = ButtonType.WARN,
                    modifier = Modifier.weight(1f),
                    testTag = "your_status_button"
                )
                GameButton(
                    text = viewModel.t("enemyStatus"),
                    onClick = { viewModel.speakStatus(own = false) },
                    type = ButtonType.WARN,
                    modifier = Modifier.weight(1f),
                    testTag = "enemy_status_button"
                )
            }
            Spacer(modifier = Modifier.height(10.dp))

            GameButton(
                text = viewModel.t("exitGame"),
                onClick = { viewModel.exitBattle() },
                type = ButtonType.DANGER,
                testTag = "exit_battle_button"
            )
        }
    }
}

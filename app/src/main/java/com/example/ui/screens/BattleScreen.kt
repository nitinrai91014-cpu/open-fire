package com.example.ui.screens

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ControlMode
import com.example.model.DEFAULT_GUNS
import com.example.ui.components.AlertBox
import com.example.ui.components.ButtonType
import com.example.ui.components.GameButton
import com.example.ui.components.GameCard
import com.example.ui.components.HintBox
import com.example.ui.components.HpBar
import com.example.ui.components.LogBox
import com.example.ui.components.RadarBar
import com.example.ui.components.TipBox
import com.example.ui.theme.BtnDanger
import com.example.ui.theme.TextMuted
import com.example.viewmodel.GameViewModel
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

    val coverTxt = if (gameState.devilCoverHp > 0) {
        " | ${viewModel.t("devilCover")}: ${gameState.devilCoverHp}/${gameState.devilCoverMax}"
    } else ""

    val stateTxt = when (gameState.devilState) {
        "retreat" -> " | ${viewModel.t("devilFled")}"
        "flank" -> " | ${viewModel.t("devilFlank")}"
        else -> ""
    }

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
                text = "${viewModel.t("devil")} ${viewModel.t("hp")}: $devilHpShown/${gameState.devilMaxHp} | ${viewModel.t("distance")}: ${gameState.distance.roundToInt()}$coverTxt$stateTxt",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(6.dp))
            HpBar(current = devilHpShown, max = gameState.devilMaxHp, isDevil = true)
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Player Card
        GameCard {
            Text(
                text = "${viewModel.t("you")} ${viewModel.t("hp")}: $playerHpShown | ${viewModel.t("ammo")}: ${gameState.ammo[gameState.gunIndex]}/${gun.mag}",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(6.dp))
            HpBar(current = playerHpShown, max = gameState.playerMaxHp, isDevil = false)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "${viewModel.t(gun.nameKey)} | ${viewModel.t("medkits")}: ${gameState.medkits} | ${viewModel.t("coins")}: ${stats.coins}${if (gameState.inCover) " | ${viewModel.t("cover")}" else ""}",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
        }

        if (gameState.playerHp <= 80) {
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
        RadarBar(distance = gameState.distance)
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
                    text = "${viewModel.t("reload")} 30",
                    onClick = { viewModel.doReload() },
                    modifier = Modifier.weight(1f),
                    testTag = "reload_button"
                )
                GameButton(
                    text = "${viewModel.t("medkit")} 100",
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
                    text = "${viewModel.t("cover")} 100",
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

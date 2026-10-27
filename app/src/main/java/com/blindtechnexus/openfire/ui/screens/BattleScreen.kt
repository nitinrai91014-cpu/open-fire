package com.blindtechnexus.openfire.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blindtechnexus.openfire.model.BattleMenuItem
import com.blindtechnexus.openfire.model.Combat
import com.blindtechnexus.openfire.model.Extension
import com.blindtechnexus.openfire.model.HitZone
import com.blindtechnexus.openfire.ui.components.AlertBox
import com.blindtechnexus.openfire.ui.components.HpBar
import com.blindtechnexus.openfire.ui.components.LogBox
import com.blindtechnexus.openfire.ui.components.RadarBar
import com.blindtechnexus.openfire.ui.theme.TextAccent
import com.blindtechnexus.openfire.ui.theme.TextMuted
import com.blindtechnexus.openfire.ui.theme.WarnBright
import com.blindtechnexus.openfire.viewmodel.GameViewModel
import kotlin.math.roundToInt

/**
 * Gesture-only battle screen split into three equal portions:
 * top = hold to run forward, bottom = hold to retreat, center = tap to
 * activate the selected quick-menu item. One-finger swipes fire, reload and
 * switch weapons; two-finger swipes select menu items and read status.
 */
@Composable
fun BattleScreen(viewModel: GameViewModel) {
    val stats by viewModel.stats.collectAsState()
    val inventory by viewModel.inventory.collectAsState()
    val gameState by viewModel.gameState.collectAsState()

    val weapon = gameState.activeWeapon
    val enemyHp = maxOf(0, gameState.robertHp)
    val playerHp = maxOf(0, gameState.playerHp)
    val proxPercent = Combat.proximityOf(gameState.distance)
    val hitZone = Combat.zoneFor(proxPercent)
    val zoneText = when (hitZone) {
        HitZone.MISS -> viewModel.t("zoneMiss")
        HitZone.LIGHT -> viewModel.t("zoneLight")
        HitZone.STRONG -> viewModel.t("zoneStrong")
        HitZone.HEAVY -> viewModel.t("zoneHeavy")
    }
    val zoneColor = when (hitZone) {
        HitZone.MISS -> TextMuted
        HitZone.LIGHT -> Color(0xFF9FD4FF)
        HitZone.STRONG -> WarnBright
        HitZone.HEAVY -> Color(0xFFFF6B6B)
    }

    val menuItem = gameState.menuItem
    val menuCount = menuItem.defence?.let { inventory.count(it) } ?: 0
    val menuLabel = viewModel.t(menuItem.nameKey)
    val ammoStatus = if (weapon.needsReload) {
        "${viewModel.t("ammo")}: ${gameState.activeAmmo}"
    } else {
        "${viewModel.t("ammo")}: ${viewModel.t("noAmmoNeeded")}"
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // --------------------------------------------------------------
        // Top third: long press to run toward Robert.
        // --------------------------------------------------------------
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color(0xFF10251A))
                .testTag("move_forward_zone")
                .semantics {
                    contentDescription = "${viewModel.t("moveForward")}. ${viewModel.t("forwardHint")}"
                }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "▲ ${viewModel.t("moveForward")}",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF81C784)
                )
                Text(
                    text = viewModel.t("forwardHint"),
                    fontSize = 13.sp,
                    color = TextMuted,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "${viewModel.t("devil")} ${viewModel.t("hp")}: $enemyHp/" +
                        "${gameState.robertMaxHp}  |  ${viewModel.t("round")} ${gameState.round}, " +
                        "${viewModel.t("level")} ${gameState.level}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(5.dp))
                HpBar(current = enemyHp, max = gameState.robertMaxHp, isDevil = true)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "${viewModel.t("distance")}: ${gameState.distance.roundToInt()} " +
                        "${viewModel.t("meters")} | $zoneText",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = zoneColor,
                    textAlign = TextAlign.Center
                )
            }
        }

        // --------------------------------------------------------------
        // Center third: status and quick-menu activation by a single tap.
        // --------------------------------------------------------------
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color(0xFF0A0E1A))
                .testTag("menu_activate_gesture_zone")
                .semantics { contentDescription = viewModel.t("centerHint") }
                .padding(horizontal = 14.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "${viewModel.t("you")} ${viewModel.t("hp")}: $playerHp/" +
                        "${gameState.playerMaxHp} | $ammoStatus | ${viewModel.t("coins")}: ${stats.coins}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                HpBar(current = playerHp, max = gameState.playerMaxHp, isDevil = false)
                Spacer(modifier = Modifier.height(5.dp))
                Text(
                    text = viewModel.t(weapon.nameKey) +
                        if (gameState.activeWeaponDamaged) " (${viewModel.t("damaged")})" else "",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (gameState.activeWeaponDamaged) WarnBright else TextAccent
                )
                Spacer(modifier = Modifier.height(7.dp))
                Text(
                    text = "${viewModel.t("menuNow")}: " +
                        if (menuItem.defence != null) "$menuLabel ($menuCount)" else menuLabel,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = WarnBright,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = viewModel.t("centerHint"),
                    fontSize = 12.sp,
                    color = TextMuted,
                    textAlign = TextAlign.Center
                )
                if (gameState.pendingPurchaseId != null) {
                    Spacer(modifier = Modifier.height(5.dp))
                    AlertBox(
                        text = "${viewModel.t("itemMissingPrompt")} $menuLabel. " +
                            viewModel.t("itemMissingTail")
                    )
                }
                Spacer(modifier = Modifier.height(7.dp))
                LogBox(text = gameState.log)
                Spacer(modifier = Modifier.height(5.dp))
                RadarBar(distance = gameState.distance, extraDesc = zoneText)
            }
        }

        // --------------------------------------------------------------
        // Bottom third: long press to run away from Robert.
        // --------------------------------------------------------------
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color(0xFF101D30))
                .testTag("move_backward_zone")
                .semantics {
                    contentDescription = "${viewModel.t("moveBackward")}. ${viewModel.t("backwardHint")}"
                }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "${viewModel.t("medkits")}: ${inventory.medikits} | " +
                        "${viewModel.t("glooWall")}: ${inventory.glueWalls} | " +
                        "${viewModel.t("cover")}: ${inventory.covers} | " +
                        "${viewModel.t("antiMissile")}: ${inventory.antiMissiles}",
                    fontSize = 14.sp,
                    color = TextAccent,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(5.dp))
                Text(
                    text = BattleMenuItem.entries.joinToString("  ") { item ->
                        viewModel.t(item.nameKey)
                    },
                    fontSize = 12.sp,
                    color = TextMuted,
                    textAlign = TextAlign.Center,
                    lineHeight = 17.sp
                )
                val activeExtensions = Extension.entries.filter {
                    inventory.extensionEnabled(it)
                }
                if (activeExtensions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(5.dp))
                    Text(
                        text = "${viewModel.t("upgrades")}: " +
                            activeExtensions.joinToString(", ") { viewModel.t(it.nameKey) },
                        fontSize = 12.sp,
                        color = TextMuted,
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = viewModel.t("menuHint"),
                    fontSize = 12.sp,
                    color = TextMuted,
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "▼ ${viewModel.t("moveBackward")}",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF64B5F6)
                )
                Text(
                    text = viewModel.t("backwardHint"),
                    fontSize = 13.sp,
                    color = TextMuted,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

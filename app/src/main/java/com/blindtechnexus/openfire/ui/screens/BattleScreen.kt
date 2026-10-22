package com.blindtechnexus.openfire.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blindtechnexus.openfire.model.BattleMenuItem
import com.blindtechnexus.openfire.model.Combat
import com.blindtechnexus.openfire.model.Extension
import com.blindtechnexus.openfire.model.HitZone
import com.blindtechnexus.openfire.model.Weapon
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
import com.blindtechnexus.openfire.ui.theme.CardBorder
import com.blindtechnexus.openfire.ui.theme.TextAccent
import com.blindtechnexus.openfire.ui.theme.TextMuted
import com.blindtechnexus.openfire.ui.theme.WarnBright
import com.blindtechnexus.openfire.viewmodel.GameViewModel
import kotlin.math.roundToInt

/**
 * Redesigned battle screen.
 *
 * The quick menu (medikit, cover, glue wall, exit, play) is the primary
 * control. Two-finger up/down moves the selection, a single tap activates it,
 * and the same actions are available as buttons for touch exploration.
 */
@Composable
fun BattleScreen(viewModel: GameViewModel) {
    val stats by viewModel.stats.collectAsState()
    val inventory by viewModel.inventory.collectAsState()
    val gameState by viewModel.gameState.collectAsState()
    val scrollState = rememberScrollState()

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
    val menuCount = when (menuItem) {
        BattleMenuItem.MEDKIT -> inventory.medikits
        BattleMenuItem.COVER -> inventory.covers
        BattleMenuItem.GLUE_WALL -> inventory.glueWalls
        else -> 0
    }

    // Firearms show their magazine; knife and sword simply need no ammo.
    val ammoStatus = if (weapon.needsReload) {
        "${viewModel.t("ammo")}: ${gameState.activeAmmo}"
    } else {
        "${viewModel.t("ammo")}: ${viewModel.t("noAmmoNeeded")}"
    }

    val weaponsSummary = gameState.weapons.mapNotNull { id ->
        val w = Weapon.fromId(id) ?: return@mapNotNull null
        val index = gameState.weapons.indexOf(id)
        val marker = if (index == gameState.weaponIndex) "> " else ""
        val damaged = if (gameState.damaged.getOrElse(index) { false }) {
            " (${viewModel.t("damaged")})"
        } else {
            ""
        }
        if (w.needsReload) {
            "$marker${viewModel.t(w.nameKey)} ${gameState.ammo.getOrElse(index) { 0 }}"
        } else {
            "$marker${viewModel.t(w.nameKey)}"
        }
    }.joinToString("   ")

    val enemyCover = when {
        gameState.robertGlueWallActive ->
            " | ${viewModel.t("glooWall")}: ${gameState.robertGlueWallProgress}/" +
                "${Combat.GLUE_WALL_BREAK_HITS}"
        gameState.robertCoverActive ->
            " | ${viewModel.t("cover")}: " +
                "${Combat.COVER_MAX_HITS - gameState.robertCoverHits}"
        else -> ""
    }
    val enemyState = when (gameState.robertState) {
        "retreat" -> " | ${viewModel.t("robertFled")}"
        "flank" -> " | ${viewModel.t("devilFlank")}"
        else -> ""
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        GameButton(
            text = viewModel.t("backToMenu"),
            onClick = { viewModel.exitBattle() },
            type = ButtonType.DANGER,
            testTag = "battle_back_menu_button"
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Top Portion (Upper section): Move Forward
        GameCard(
            borderColor = Color(0xFF4CAF50),
            backgroundColor = Color(0xFF1B2E1E),
            modifier = Modifier.semantics {
                contentDescription = "${viewModel.t("moveForward")}. ${viewModel.t("forwardHint")}"
            }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "▲ ${viewModel.t("moveForward")}",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF81C784)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = viewModel.t("forwardHint"),
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                }
                GameButton(
                    text = "▲",
                    onClick = {
                        viewModel.startPlayerMove(forward = true)
                    },
                    type = ButtonType.PRIMARY,
                    testTag = "move_forward_zone"
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Enemy card
        GameCard(
            backgroundColor = BtnDanger,
            borderColor = Color(0xFFFF4D4D)
        ) {
            Text(
                text = "${viewModel.t("devil")} ${viewModel.t("hp")}: $enemyHp/" +
                    "${gameState.robertMaxHp} | ${viewModel.t("distance")}: " +
                    "${gameState.distance.roundToInt()} ${viewModel.t("meters")}" +
                    "$enemyCover$enemyState",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.semantics {
                    contentDescription = "${viewModel.t("devil")} ${viewModel.t("hp")} " +
                        "$enemyHp of ${gameState.robertMaxHp}"
                }
            )
            Spacer(modifier = Modifier.height(6.dp))
            HpBar(current = enemyHp, max = gameState.robertMaxHp, isDevil = true)
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Player card
        GameCard {
            Text(
                text = "${viewModel.t("you")} ${viewModel.t("hp")}: $playerHp/" +
                    "${gameState.playerMaxHp} | $ammoStatus",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.semantics {
                    contentDescription = "${viewModel.t("you")} ${viewModel.t("hp")} " +
                        "$playerHp of ${gameState.playerMaxHp}"
                }
            )
            Spacer(modifier = Modifier.height(6.dp))
            HpBar(current = playerHp, max = gameState.playerMaxHp, isDevil = false)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = viewModel.t(weapon.nameKey) +
                    (if (gameState.activeWeaponDamaged) " (${viewModel.t("damaged")})" else "") +
                    " | ${viewModel.t("coins")}: ${stats.coins}",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = weaponsSummary,
                fontSize = 13.sp,
                color = TextAccent,
                lineHeight = 19.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "${viewModel.t("medkits")}: ${inventory.medikits} | " +
                    "${viewModel.t("glooWall")}: ${inventory.glueWalls} | " +
                    "${viewModel.t("cover")}: ${inventory.covers}",
                fontSize = 14.sp,
                color = TextAccent
            )
            val wallStatus = buildString {
                if (gameState.glueWallActive) {
                    append("${viewModel.t("glooWall")}: ")
                    append(Combat.GLUE_WALL_BREAK_HITS - gameState.glueWallProgress)
                }
                if (gameState.coverActive) {
                    if (isNotEmpty()) append(" | ")
                    append("${viewModel.t("coverHitsLeft")}: ")
                    append(Combat.COVER_MAX_HITS - gameState.coverHits)
                }
            }
            if (wallStatus.isNotEmpty()) {
                Text(
                    text = wallStatus,
                    fontSize = 14.sp,
                    color = WarnBright,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Active quick menu
        Spacer(modifier = Modifier.height(12.dp))
        GameCard(borderColor = WarnBright) {
            Text(
                text = viewModel.t("menuNow"),
                fontSize = 13.sp,
                color = TextMuted,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            val menuLabel = when (menuItem) {
                BattleMenuItem.MEDKIT -> viewModel.t("medkit")
                BattleMenuItem.COVER -> viewModel.t("cover")
                BattleMenuItem.GLUE_WALL -> viewModel.t("glooWall")
                BattleMenuItem.EXIT -> viewModel.t("exitGame")
                BattleMenuItem.RECOVER -> viewModel.t("recoverWeapon")
            }
            Text(
                text = if (menuItem.defence != null) "$menuLabel ($menuCount)" else menuLabel,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
                color = WarnBright,
                modifier = Modifier.semantics { heading() }
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = BattleMenuItem.entries.joinToString("  ") { item ->
                    when (item) {
                        BattleMenuItem.MEDKIT -> viewModel.t("medkit")
                        BattleMenuItem.COVER -> viewModel.t("cover")
                        BattleMenuItem.GLUE_WALL -> viewModel.t("glooWall")
                        BattleMenuItem.EXIT -> viewModel.t("exitGame")
                        BattleMenuItem.RECOVER -> viewModel.t("recoverWeapon")
                    }
                },
                fontSize = 13.sp,
                color = TextMuted
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GameButton(
                    text = "◀",
                    onClick = { viewModel.cycleMenu(-1) },
                    type = ButtonType.WARN,
                    modifier = Modifier.weight(1f),
                    testTag = "menu_previous_button"
                )
                GameButton(
                    text = viewModel.t("menuNow"),
                    onClick = { viewModel.activateMenuItem() },
                    type = ButtonType.PRIMARY,
                    modifier = Modifier.weight(2f),
                    testTag = "menu_activate_button"
                )
                GameButton(
                    text = "▶",
                    onClick = { viewModel.cycleMenu(1) },
                    type = ButtonType.WARN,
                    modifier = Modifier.weight(1f),
                    testTag = "menu_next_button"
                )
            }
            if (gameState.pendingPurchaseId != null) {
                Spacer(modifier = Modifier.height(8.dp))
                AlertBox(
                    text = "${viewModel.t("itemMissingPrompt")} " +
                        "${viewModel.t(menuItem.defence?.nameKey ?: "play")}. " +
                        viewModel.t("itemMissingTail")
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        HintBox(text = viewModel.t("menuHint"))
        Spacer(modifier = Modifier.height(10.dp))

        if (gameState.activeWeaponDamaged) {
            AlertBox(
                text = "${viewModel.t("gunDamaged")} ${viewModel.t("recovery")}. " +
                    "${viewModel.t("coins")}: ${Combat.GUN_RECOVERY_COST}."
            )
            Spacer(modifier = Modifier.height(10.dp))
        }

        if (weapon.needsReload && gameState.activeAmmo == 0) {
            AlertBox(text = "${viewModel.t("outOfAmmo")} ${viewModel.t("reload")}.")
            Spacer(modifier = Modifier.height(10.dp))
        }

        GameButton(
            text = viewModel.t("fire"),
            onClick = { viewModel.fire() },
            type = ButtonType.DANGER,
            testTag = "fire_button"
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GameButton(
                text = viewModel.t("switchGun"),
                onClick = { viewModel.switchWeapon(1) },
                type = ButtonType.DEFAULT,
                modifier = Modifier.weight(1f),
                testTag = "switch_weapon_button"
            )
            GameButton(
                text = viewModel.t("reload"),
                onClick = { viewModel.doReload() },
                type = ButtonType.WARN,
                modifier = Modifier.weight(1f),
                testTag = "reload_button"
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        GameButton(
            text = viewModel.t("recovery"),
            onClick = { viewModel.recoverGun() },
            type = ButtonType.WARN,
            testTag = "recovery_button"
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GameButton(
                text = viewModel.t("yourStatus"),
                onClick = { viewModel.speakStatus(own = true) },
                type = ButtonType.DEFAULT,
                modifier = Modifier.weight(1f),
                testTag = "your_status_button"
            )
            GameButton(
                text = viewModel.t("enemyStatus"),
                onClick = { viewModel.speakStatus(own = false) },
                type = ButtonType.DEFAULT,
                modifier = Modifier.weight(1f),
                testTag = "enemy_status_button"
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        GameButton(
            text = viewModel.t("exitGame"),
            onClick = { viewModel.exitBattle() },
            type = ButtonType.DANGER,
            testTag = "exit_battle_button"
        )

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
        Text(
            text = zoneText,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = zoneColor
        )

        // Enabled automatic helpers are listed so the player always knows
        // which ones will fire by themselves.
        val activeExtensions = Extension.entries.filter { inventory.extensionEnabled(it) }
        if (activeExtensions.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "${viewModel.t("upgrades")}: " +
                    activeExtensions.joinToString(", ") { viewModel.t(it.nameKey) },
                fontSize = 13.sp,
                color = TextMuted,
                lineHeight = 18.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF101820), RoundedCornerShape(8.dp))
                    .border(1.dp, CardBorder, RoundedCornerShape(8.dp))
                    .padding(10.dp)
            )
        }

        // Bottom Portion (Lower section): Move Backward / Retreat
        Spacer(modifier = Modifier.height(14.dp))
        GameCard(
            borderColor = Color(0xFF2196F3),
            backgroundColor = Color(0xFF142436),
            modifier = Modifier.semantics {
                contentDescription = "${viewModel.t("moveBackward")}. ${viewModel.t("backwardHint")}"
            }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "▼ ${viewModel.t("moveBackward")}",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF64B5F6)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = viewModel.t("backwardHint"),
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                }
                GameButton(
                    text = "▼",
                    onClick = {
                        viewModel.startPlayerMove(forward = false)
                    },
                    type = ButtonType.PRIMARY,
                    testTag = "move_backward_zone"
                )
            }
        }
    }
}

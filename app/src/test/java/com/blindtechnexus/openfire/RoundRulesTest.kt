package com.blindtechnexus.openfire

import com.blindtechnexus.openfire.audio.AudioManager
import com.blindtechnexus.openfire.model.GameState
import com.blindtechnexus.openfire.model.Progression
import com.blindtechnexus.openfire.model.RobertRules
import com.blindtechnexus.openfire.model.Weapon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Keeps round scaling and the current enemy-fire rhythm consistent. */
class RoundRulesTest {

    @Test
    fun enemyLoadoutsOnlyContainSupportedFiniteAmmoWeapons() {
        for (round in 1..Progression.ROUNDS_PER_MATCH) {
            val profile = RobertRules.profile(round)
            assertEquals(round, profile.round)
            assertTrue(profile.weapons.isNotEmpty())
            assertTrue(profile.weapons.all { it in Weapon.entries })
            for (weapon in profile.weapons.filter { it.needsReload }) {
                assertEquals(6, weapon.magazine)
            }
        }
        assertEquals(Weapon.entries, RobertRules.profile(5).weapons)
    }

    @Test
    fun normalEnemyFireStaysBetweenOneAndAHalfAndTwoSeconds() {
        val random = Random(17)
        repeat(1000) {
            for (playerHasDefence in listOf(false, true)) {
                val interval = RobertRules.fireInterval(
                    playerWeaponDamaged = false,
                    enemyHasDefence = false,
                    playerHasDefence = playerHasDefence,
                    random = random
                )
                assertTrue("Unexpected fire interval: $interval", interval in 1500L..2000L)
            }
        }
    }

    @Test
    fun anEnemyDefenceAdvantageUsesOneSecondFire() {
        assertEquals(
            1000L,
            RobertRules.fireInterval(false, enemyHasDefence = true, playerHasDefence = false, random = Random(1))
        )
        val interval = RobertRules.fireInterval(false, enemyHasDefence = true, playerHasDefence = true, random = Random(1))
        assertTrue(interval in 1500L..2000L)
    }

    @Test
    fun aDamagedPlayerWeaponAlwaysTriggersOneSecondPressure() {
        for (enemyHasDefence in listOf(false, true)) {
            for (playerHasDefence in listOf(false, true)) {
                assertEquals(
                    1000L,
                    RobertRules.fireInterval(true, enemyHasDefence, playerHasDefence, Random(2))
                )
            }
        }
    }

    @Test
    fun aFreshBattleHasNotSpentAnyRoundFourMissilesOrInterceptors() {
        val state = GameState(round = 4)
        assertFalse(state.robertMissileUsed)
        assertEquals(0, state.robertAntiMissilesUsed)
        assertTrue(state.copy(robertMissileUsed = true).robertMissileUsed)
        assertEquals(2, state.copy(robertAntiMissilesUsed = 2).robertAntiMissilesUsed)
    }

    @Test
    fun finalRoundLimitHelperIsSevenMinutesAndOnlyAppliesToTheLastRound() {
        assertEquals(7L * 60L * 1000L, Progression.FINAL_ROUND_TIME_LIMIT_MS)
        for (round in 1 until Progression.ROUNDS_PER_MATCH) {
            assertEquals(0L, Progression.roundTimeLimitMs(round))
        }
        assertEquals(Progression.FINAL_ROUND_TIME_LIMIT_MS, Progression.roundTimeLimitMs(5))
    }

    @Test
    fun defeatPenaltyHelperNeverOverdrawsThePlayersCoins() {
        assertEquals(20, Progression.DEFEAT_COIN_PENALTY)
        assertEquals(20, Progression.coinsLostOnDefeat(100))
        assertEquals(20, Progression.coinsLostOnDefeat(20))
        assertEquals(5, Progression.coinsLostOnDefeat(5))
        assertEquals(0, Progression.coinsLostOnDefeat(0))
        assertEquals(0, Progression.coinsLostOnDefeat(-30))
    }

    @Test
    fun onlyOneWeaponSoundCanPlayAndFootstepsStaySeparate() {
        val keys = AudioManager.WEAPON_SOUND_KEYS
        for (weapon in Weapon.entries) {
            assertTrue("${weapon.id} must use the exclusive weapon channel", weapon.fireSoundKey in keys)
        }
        for (other in listOf("playerWalk", "enemyWalk", "walk", "gunHit", "enemyHit", "explosion")) {
            assertFalse("$other must not cut a weapon sound", other in keys)
        }
    }
}

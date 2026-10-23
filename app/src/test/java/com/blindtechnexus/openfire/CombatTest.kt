package com.blindtechnexus.openfire

import com.blindtechnexus.openfire.model.Combat
import com.blindtechnexus.openfire.model.DefenceItem
import com.blindtechnexus.openfire.model.FireRange
import com.blindtechnexus.openfire.model.HitZone
import com.blindtechnexus.openfire.model.RobertRules
import com.blindtechnexus.openfire.model.Weapon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifies proximity zones, the new power tables and defence rules. */
class CombatTest {

    @Test
    fun zoneBoundaries() {
        assertEquals(HitZone.MISS, Combat.zoneFor(0f))
        assertEquals(HitZone.MISS, Combat.zoneFor(39.9f))
        assertEquals(HitZone.LIGHT, Combat.zoneFor(40f))
        assertEquals(HitZone.LIGHT, Combat.zoneFor(69.9f))
        assertEquals(HitZone.STRONG, Combat.zoneFor(70f))
        assertEquals(HitZone.STRONG, Combat.zoneFor(89.9f))
        assertEquals(HitZone.HEAVY, Combat.zoneFor(90f))
        assertEquals(HitZone.HEAVY, Combat.zoneFor(100f))
    }

    @Test
    fun missesDealNoDamageAndConnectedHitsNeverDealZero() {
        assertEquals(0, Combat.damageFor(20, 0f, 300))
        assertEquals(0, Combat.damageFor(20, 39.9f, 300))
        assertEquals(1, Combat.damageFor(1, 50f, 100))
        assertEquals(1, Combat.damageFor(1, 75f, 100))
        assertEquals(0, Combat.damageFor(20, 100f, 0))
    }

    @Test
    fun standardZoneMultipliersRemainFiftyAndEightyPercent() {
        assertEquals(50, Combat.damageFor(20, 40f, 500))
        assertEquals(80, Combat.damageFor(20, 70f, 500))
        assertEquals(100, Combat.damageFor(20, 90f, 500))
        assertEquals(100, Combat.damageFor(20, 100f, 500))
    }

    @Test
    fun weaponPowersMatchTheNewBaseAndCloseRangeTable() {
        val expected = mapOf(
            Weapon.KNIFE to (10 to 10),
            Weapon.SHORT_GUN to (10 to 12),
            Weapon.ASSAULT_GUN to (13 to 14),
            Weapon.PISTOL to (14 to 15),
            Weapon.SNIPER to (15 to 16),
            Weapon.SWORD to (15 to 16)
        )
        for ((weapon, powers) in expected) {
            assertEquals("${weapon.id} base", powers.first, weapon.powerPercent)
            assertEquals("${weapon.id} close", powers.second, weapon.closeRangePowerPercent)
            assertEquals(powers.first, Combat.powerPercentOf(weapon, 50f))
            assertEquals(powers.first, Combat.powerPercentOf(weapon, 80f))
            assertEquals(powers.second, Combat.powerPercentOf(weapon, 90f))
            assertEquals(powers.second, Combat.powerPercentOf(weapon, 100f))
        }
    }

    @Test
    fun playerWeaponDamageUsesBasePowerThenCloseRangePower() {
        // A 1000 HP victim makes the expected percentages easy to verify.
        assertEquals(100, Combat.weaponDamage(Weapon.KNIFE, 100f, 1000))
        assertEquals(120, Combat.weaponDamage(Weapon.SHORT_GUN, 100f, 1000))
        assertEquals(140, Combat.weaponDamage(Weapon.ASSAULT_GUN, 100f, 1000))
        assertEquals(150, Combat.weaponDamage(Weapon.PISTOL, 100f, 1000))
        assertEquals(160, Combat.weaponDamage(Weapon.SNIPER, 100f, 1000))
        assertEquals(160, Combat.weaponDamage(Weapon.SWORD, 100f, 1000))

        assertEquals(50, Combat.weaponDamage(Weapon.SHORT_GUN, 50f, 1000))
        assertEquals(80, Combat.weaponDamage(Weapon.SHORT_GUN, 80f, 1000))
        assertEquals(65, Combat.weaponDamage(Weapon.ASSAULT_GUN, 50f, 1000))
        assertEquals(104, Combat.weaponDamage(Weapon.ASSAULT_GUN, 80f, 1000))
        assertEquals(0, Combat.weaponDamage(Weapon.SWORD, 20f, 1000))
    }

    @Test
    fun robertAssaultRifleUsesHisExactZoneTable() {
        assertEquals(0, RobertRules.powerFor(Weapon.ASSAULT_GUN, 20f))
        assertEquals(13, RobertRules.powerFor(Weapon.ASSAULT_GUN, 50f))
        assertEquals(15, RobertRules.powerFor(Weapon.ASSAULT_GUN, 80f))
        assertEquals(16, RobertRules.powerFor(Weapon.ASSAULT_GUN, 95f))
        assertEquals(0, RobertRules.weaponDamage(Weapon.ASSAULT_GUN, 20f, 1000))
        assertEquals(130, RobertRules.weaponDamage(Weapon.ASSAULT_GUN, 50f, 1000))
        assertEquals(150, RobertRules.weaponDamage(Weapon.ASSAULT_GUN, 80f, 1000))
        assertEquals(160, RobertRules.weaponDamage(Weapon.ASSAULT_GUN, 95f, 1000))
    }

    @Test
    fun robertShotgunUsesHisExactZoneTableWithoutDoubleScaling() {
        assertEquals(0, RobertRules.powerFor(Weapon.SHORT_GUN, 20f))
        assertEquals(10, RobertRules.powerFor(Weapon.SHORT_GUN, 50f))
        assertEquals(13, RobertRules.powerFor(Weapon.SHORT_GUN, 80f))
        assertEquals(14, RobertRules.powerFor(Weapon.SHORT_GUN, 95f))
        assertEquals(0, RobertRules.weaponDamage(Weapon.SHORT_GUN, 20f, 1000))
        assertEquals(100, RobertRules.weaponDamage(Weapon.SHORT_GUN, 50f, 1000))
        assertEquals(130, RobertRules.weaponDamage(Weapon.SHORT_GUN, 80f, 1000))
        assertEquals(140, RobertRules.weaponDamage(Weapon.SHORT_GUN, 95f, 1000))
    }

    @Test
    fun robertOtherWeaponsUseTheStandardTable() {
        for (weapon in listOf(Weapon.KNIFE, Weapon.SWORD, Weapon.PISTOL, Weapon.SNIPER)) {
            for (proximity in listOf(10f, 50f, 80f, 95f)) {
                assertEquals(
                    Combat.weaponDamage(weapon, proximity, 1000),
                    RobertRules.weaponDamage(weapon, proximity, 1000)
                )
            }
        }
    }

    @Test
    fun proximityUsesTheOneHundredMeterArena() {
        assertEquals(0f, Combat.proximityOf(100f), 0.001f)
        assertEquals(40f, Combat.proximityOf(60f), 0.001f)
        assertEquals(70f, Combat.proximityOf(30f), 0.001f)
        assertEquals(90f, Combat.proximityOf(10f), 0.001f)
        assertEquals(100f, Combat.proximityOf(0f), 0.001f)
        assertEquals(0f, Combat.proximityOf(150f), 0.001f)
        assertEquals(100f, Combat.proximityOf(-20f), 0.001f)
    }

    @Test
    fun distanceAndProximityAreInverseOperations() {
        for (proximity in listOf(0f, 15f, 40f, 55f, 70f, 95f, 100f)) {
            assertEquals(proximity, Combat.proximityOf(Combat.distanceFor(proximity)), 0.001f)
        }
        assertEquals(80f, FireRange.FAR.targetDistance, 0.001f)
        assertEquals(45f, FireRange.MID.targetDistance, 0.001f)
        assertEquals(20f, FireRange.STRONG.targetDistance, 0.001f)
        assertEquals(5f, FireRange.CLOSE.targetDistance, 0.001f)
    }

    @Test
    fun secondChanceRestoresExactlyHalfHealth() {
        assertEquals(100, Combat.secondChanceHealth(200))
        assertEquals(450, Combat.secondChanceHealth(900))
        assertEquals(1300, Combat.secondChanceHealth(2600))
        assertEquals(1, Combat.secondChanceHealth(1))
    }

    @Test
    fun onlySniperShotsAtPointBlankCanBreakAGlueWall() {
        assertTrue(Combat.canBreakGlueWall(Weapon.SNIPER, 90f))
        assertTrue(Combat.canBreakGlueWall(Weapon.SNIPER, 100f))
        assertFalse(Combat.canBreakGlueWall(Weapon.SNIPER, 89.9f))
        assertFalse(Combat.canBreakGlueWall(Weapon.ASSAULT_GUN, 100f))
        assertEquals(3, Combat.GLUE_WALL_BREAK_HITS)
    }

    @Test
    fun coverAndRecoveryRulesAreStable() {
        assertEquals(11, Combat.COVER_MAX_HITS)
        assertEquals(100, Combat.GUN_RECOVERY_COST)
        assertEquals(200, DefenceItem.MEDKIT.price)
        assertEquals(400, DefenceItem.GLUE_WALL.price)
        assertEquals(350, DefenceItem.COVER.price)
    }

    @Test
    fun threeConsecutivePointBlankHitsDamageTheWeapon() {
        var streak = 0
        streak = Combat.nextHeavyHitStreak(streak, 95f)
        assertEquals(1, streak)
        assertFalse(Combat.gunBreaks(streak))
        streak = Combat.nextHeavyHitStreak(streak, 90f)
        streak = Combat.nextHeavyHitStreak(streak, 100f)
        assertTrue(Combat.gunBreaks(streak))
        assertEquals(0, Combat.nextHeavyHitStreak(streak, 80f))
    }
}

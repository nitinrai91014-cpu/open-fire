package com.blindtechnexus.openfire

import com.blindtechnexus.openfire.model.Combat
import com.blindtechnexus.openfire.model.DefenceItem
import com.blindtechnexus.openfire.model.Extension
import com.blindtechnexus.openfire.model.Weapon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The current finite-ammo weapons replace the old tracking projectiles. */
class WeaponCatalogTest {

    @Test
    fun catalogContainsOnlyTheSupportedWeaponsAndDefences() {
        assertEquals(
            setOf("shortGun", "assaultGun", "pistol", "sniper", "knife", "sword", "atomicBomb", "missile"),
            Weapon.entries.map { it.id }.toSet()
        )
        assertEquals(setOf("medkit", "glueWall", "cover", "antiMissile"), DefenceItem.entries.map { it.id }.toSet())
        assertFalse(Extension.entries.any { it.id in setOf("missilePowers", "bombPowers") })
        for (removedId in listOf("grenade", "atomBomb", "timerBomb", "mp40", "awm", "machineGun")) {
            assertNull(Weapon.fromId(removedId))
        }
        assertNull(DefenceItem.fromId("antiBomb"))
    }

    @Test
    fun atomicBombAndMissileUseSixRoundMagazinesAndNormalReloads() {
        for (weapon in listOf(Weapon.ATOMIC_BOMB, Weapon.MISSILE)) {
            assertFalse(weapon.melee)
            assertTrue(weapon.needsReload)
            assertEquals(6, weapon.magazine)
            assertEquals("gunReload", weapon.switchSoundKey)
        }
        assertEquals(1, Weapon.ATOMIC_BOMB.unlockAfterRound)
        assertEquals(3, Weapon.MISSILE.unlockAfterRound)
    }

    @Test
    fun explosiveNamedWeaponsUseDirectDamageInsteadOfGuaranteedKills() {
        assertEquals(40, Weapon.ATOMIC_BOMB.powerPercent)
        assertEquals(45, Weapon.MISSILE.powerPercent)
        for (health in listOf(300, 400, 500, 600, 750)) {
            for (weapon in listOf(Weapon.ATOMIC_BOMB, Weapon.MISSILE)) {
                assertEquals(0, Combat.weaponDamage(weapon, 20f, health))
                assertEquals(weapon.powerPercent, Combat.weaponDamage(weapon, 95f, health))
                assertTrue(Combat.weaponDamage(weapon, 95f, health) < health)
            }
        }
    }
}

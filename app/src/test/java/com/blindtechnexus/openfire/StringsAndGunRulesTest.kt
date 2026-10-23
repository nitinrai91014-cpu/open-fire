package com.blindtechnexus.openfire

import com.blindtechnexus.openfire.data.Strings
import com.blindtechnexus.openfire.model.DefenceItem
import com.blindtechnexus.openfire.model.Extension
import com.blindtechnexus.openfire.model.Weapon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Keeps translations, weapon rules and shop prices standardised. */
class StringsAndGunRulesTest {

    @Test
    fun everyEnglishKeyExistsInHindiAndNepali() {
        val keys = Strings.availableKeys()
        assertTrue("English table should define keys", keys.isNotEmpty())
        for (key in keys) {
            assertTrue("Missing Hindi translation for '$key'", Strings.hasTranslation(key, "hi"))
            assertTrue("Missing Nepali translation for '$key'", Strings.hasTranslation(key, "ne"))
        }
    }

    @Test
    fun lookupsNeverLeakTheRawKey() {
        for (key in Strings.availableKeys()) {
            for (lang in listOf("en", "hi", "ne")) {
                val value = Strings.get(key, lang)
                assertTrue("Blank translation for '$key' in '$lang'", value.isNotBlank())
                if (lang != "en") {
                    assertTrue("Raw key '$key' leaked into '$lang'", value != key)
                }
            }
        }
    }

    @Test
    fun firearmsHoldExactlySixRoundsAndMeleeNeverReloads() {
        val firearms = Weapon.entries.filter { !it.melee }
        assertEquals(4, firearms.size)
        for (weapon in firearms) {
            assertEquals("Weapon '${weapon.id}' must hold six rounds", 6, weapon.magazine)
            assertTrue("Firearms need a reload", weapon.needsReload)
        }
        for (weapon in Weapon.entries.filter { it.melee }) {
            assertEquals(0, weapon.magazine)
            assertTrue("Melee weapons never reload", !weapon.needsReload)
        }
    }

    @Test
    fun shopCatalogMatchesTheRequestedPrices() {
        assertEquals(300, Weapon.KNIFE.price)
        assertEquals(405, Weapon.SWORD.price)
        assertEquals(499, Weapon.PISTOL.price)
        assertEquals(549, Weapon.SNIPER.price)
        assertEquals(200, DefenceItem.MEDKIT.price)
        assertEquals(400, DefenceItem.GLUE_WALL.price)
        assertEquals(350, DefenceItem.COVER.price)
        assertEquals(
            setOf(50, 80, 100, 500),
            Extension.entries.map { it.price }.toSet()
        )
    }

    @Test
    fun guidesExistForEveryLanguageAndAreDetailed() {
        assertEquals(22, Strings.getGuide("en").size)
        assertEquals(22, Strings.getGuide("hi").size)
        assertEquals(22, Strings.getGuide("ne").size)
        for (lang in listOf("en", "hi", "ne")) {
            val guide = Strings.getGuide(lang)
            assertTrue("Guide must be detailed", guide.joinToString(" ").length > 2500)
        }
    }

    @Test
    fun guideDocumentsTheNewRules() {
        val en = Strings.getGuide("en").joinToString(" ")
        assertTrue("Guide must document the five rounds", en.contains("five rounds"))
        assertTrue("Guide must document 10 wins per level", en.contains("10 wins"))
        assertTrue("Guide must document five levels per round", en.contains("five levels complete one round"))
        assertTrue("Guide must document level health", en.contains("200 health"))
        assertTrue("Guide must document the next round's health", en.contains("level 1 has 700"))
        assertTrue("Guide must document the glue wall", en.contains("glue wall"))
        assertTrue("Guide must document the cover", en.contains("Cover costs 350 coins"))
        assertTrue("Guide must document the quick menu", en.contains("quick menu"))
        assertTrue("Guide must document the extensions", en.contains("automatic reload for 50 coins"))
        assertTrue("Guide must document the four second wait", en.contains("four seconds"))
        assertTrue("Guide must document the new power table", en.contains("knife 10 and 10"))
        assertTrue("Guide must document the shotgun boost", en.contains("shotgun 10 and 12"))
        assertTrue("Guide must document Robert's custom powers", en.contains("shotgun zone powers"))
        assertTrue("Guide must document the new prices", en.contains("knife 300 coins"))
        assertTrue("Guide must document the Missile", en.contains("Missile costs 1500 coins"))
        assertTrue("Guide must document Missile Powers", en.contains("Missile Powers for 500 coins"))
        assertTrue("Guide must document the 11 hit cover", en.contains("stops 11 hits"))
        assertTrue("Guide must document the 100 coin recovery", en.contains("100 coins for Recovery"))
        assertTrue("Guide must document the recover menu item", en.contains("exit and recover"))
        assertTrue("Guide must document three screen portions", en.contains("divided into three portions"))
        assertTrue("Guide must document the enemy firing bands", en.contains("40 to 70 percent"))
        assertTrue("Guide must document gesture-only gameplay", en.contains("no button controls"))
    }

    @Test
    fun theOldPlayItemAndItsTextsAreGone() {
        // The quick menu item "play" was replaced by Recovery.
        assertFalse(Strings.availableKeys().contains("playState"))
        assertFalse(Strings.availableKeys().contains("alreadyPlaying"))
        for (lang in listOf("en", "hi", "ne")) {
            assertTrue(Strings.get("recovery", lang).contains("100") || Strings.get("recovery", lang).contains("१००"))
            assertTrue(Strings.get("defenceCoverInfo", lang).contains("11") || Strings.get("defenceCoverInfo", lang).contains("११"))
        }
    }

    @Test
    fun theGuideNeverMentionsTechnologyOrArchitecture() {
        val en = Strings.getGuide("en").joinToString(" ").lowercase()
        for (forbidden in listOf("aes", "encryption", "sqlite", "database", "kotlin", "api", "keystore")) {
            assertTrue("Guide must not mention '$forbidden'", !en.contains(forbidden))
        }
    }

    @Test
    fun newShopAndBattleStringsAreTranslated() {
        for (lang in listOf("en", "hi", "ne")) {
            assertTrue(Strings.get("shop", lang).isNotBlank())
            assertTrue(Strings.get("shopSubtitle", lang).isNotBlank())
            assertTrue(Strings.get("weapons", lang).isNotBlank())
            assertTrue(Strings.get("defences", lang).isNotBlank())
            assertTrue(Strings.get("extensions", lang).isNotBlank())
            assertTrue(Strings.get("notEnoughCoins", lang).isNotBlank())
            assertTrue(Strings.get("recoverWeapon", lang).isNotBlank())
            assertTrue(Strings.get("robertPressing", lang).isNotBlank())
            assertTrue(Strings.get("robertPressureOver", lang).isNotBlank())
            assertTrue(Strings.get("specialSupport", lang).isNotBlank())
            assertTrue(Strings.get("specialSupportName", lang).isNotBlank())
            assertTrue(Strings.get("builtInNepal", lang).isNotBlank())
            assertTrue(Strings.get("itemMissingTail", lang).isNotBlank())
            assertTrue(Strings.get("robertFled", lang).isNotBlank())
            assertTrue(Strings.get("matchWon", lang).isNotBlank())
            assertTrue(Strings.get("version", lang).contains("2.1") || Strings.get("version", lang).contains("२.१"))
        }
    }

    @Test
    fun everyDefenceAndExtensionHasADescription() {
        for (item in DefenceItem.entries) {
            for (lang in listOf("en", "hi", "ne")) {
                assertTrue(Strings.get(item.descriptionKey, lang).isNotBlank())
            }
        }
        for (extension in Extension.entries) {
            for (lang in listOf("en", "hi", "ne")) {
                assertTrue(Strings.get(extension.nameKey, lang).isNotBlank())
                assertTrue(Strings.get(extension.descriptionKey, lang).isNotBlank())
            }
        }
    }
}

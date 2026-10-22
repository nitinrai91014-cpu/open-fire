package com.blindtechnexus.openfire

import com.blindtechnexus.openfire.data.Strings
import com.blindtechnexus.openfire.model.DefenceItem
import com.blindtechnexus.openfire.model.Extension
import com.blindtechnexus.openfire.model.Weapon
import org.junit.Assert.assertEquals
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
        assertEquals(200, Weapon.PISTOL.price)
        assertEquals(300, Weapon.SNIPER.price)
        assertEquals(250, Weapon.KNIFE.price)
        assertEquals(340, Weapon.SWORD.price)
        assertEquals(200, DefenceItem.MEDKIT.price)
        assertEquals(400, DefenceItem.GLUE_WALL.price)
        assertEquals(350, DefenceItem.COVER.price)
        assertEquals(
            setOf(50, 80, 100, 100),
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
        assertTrue("Guide must document the round health", en.contains("300 health"))
        assertTrue("Guide must document the glue wall", en.contains("glue wall"))
        assertTrue("Guide must document the cover", en.contains("Cover costs 350 coins"))
        assertTrue("Guide must document the quick menu", en.contains("quick menu"))
        assertTrue("Guide must document the extensions", en.contains("automatic reload for 50 coins"))
        assertTrue("Guide must document the four second wait", en.contains("four seconds"))
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
            assertTrue(Strings.get("playState", lang).isNotBlank())
            assertTrue(Strings.get("alreadyPlaying", lang).isNotBlank())
            assertTrue(Strings.get("itemMissingTail", lang).isNotBlank())
            assertTrue(Strings.get("robertFled", lang).isNotBlank())
            assertTrue(Strings.get("matchWon", lang).isNotBlank())
            assertTrue(Strings.get("version", lang).contains("2.0") || Strings.get("version", lang).contains("२.०"))
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

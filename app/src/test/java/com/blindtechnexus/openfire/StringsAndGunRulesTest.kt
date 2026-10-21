package com.blindtechnexus.openfire

import com.blindtechnexus.openfire.data.Strings
import com.blindtechnexus.openfire.model.DEFAULT_GUNS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Keeps translations and weapon rules standardised across the app.
 */
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
                // Indic tables are always Devanagari, so a raw ASCII key leaking
                // through would mean the translation is missing at runtime.
                if (lang != "en") {
                    assertTrue("Raw key '$key' leaked into '$lang'", value != key)
                }
            }
        }
    }

    @Test
    fun everyGunHoldsExactlySixRounds() {
        assertEquals(3, DEFAULT_GUNS.size)
        for (gun in DEFAULT_GUNS) {
            assertEquals("Gun '${gun.nameKey}' must hold six rounds", 6, gun.mag)
        }
    }

    @Test
    fun guidesExistForEveryLanguage() {
        assertEquals(12, Strings.getGuide("en").size)
        assertTrue(Strings.getGuide("hi").size >= 10)
        assertTrue(Strings.getGuide("ne").size >= 10)
    }

    @Test
    fun guideDocumentsUpdatedCombatRules() {
        val en = Strings.getGuide("en").joinToString(" ")
        assertTrue("Guide must document the 20% proper-hit rule", en.contains("20%"))
        assertTrue("Guide must document the 10% light-hit rule", en.contains("10%"))
        assertTrue("Guide must document six-bullet magazines", en.contains("6 bullets"))
    }
}

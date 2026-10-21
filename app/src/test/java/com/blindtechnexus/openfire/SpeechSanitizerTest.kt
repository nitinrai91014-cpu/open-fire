package com.blindtechnexus.openfire

import com.blindtechnexus.openfire.audio.SpeechSanitizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies that game text is cleaned before it reaches the TTS engine so
 * English announcements are not distorted by raw symbols, and Indic
 * languages keep their natural Devanagari pronunciation.
 */
class SpeechSanitizerTest {

    @Test
    fun englishStopsPronouncingPunctuation() {
        val clean = SpeechSanitizer.sanitize("HP: 200/200 | Distance: 45 meters", "en")
        assertEquals("Health 200 out of 200, Distance 45 meters", clean)
        assertFalse(clean.contains("|"))
        assertFalse(clean.contains(":"))
        assertFalse(clean.contains("/"))
    }

    @Test
    fun englishExpandsHpAndPercent() {
        assertEquals("Health 100", SpeechSanitizer.sanitize("HP 100", "en"))
        assertEquals("Health 100 out of 200", SpeechSanitizer.sanitize("hp 100/200", "en"))
        assertEquals("10 percent health", SpeechSanitizer.sanitize("10% health", "en"))
    }

    @Test
    fun englishNormalizesWhitespace() {
        assertEquals("Reloaded, Coins spent 30", SpeechSanitizer.sanitize("Reloaded |   Coins spent:  30", "en"))
    }

    @Test
    fun hindiUsesHindiWords() {
        val clean = SpeechSanitizer.sanitize("HP: 200/200", "hi")
        assertTrue(clean.contains("हेल्थ"))
        assertTrue(clean.contains("में से"))
        assertFalse(clean.contains("/"))
        assertFalse(clean.contains(":"))
    }

    @Test
    fun nepaliUsesDevanagariWords() {
        val clean = SpeechSanitizer.sanitize("HP 180/200", "ne")
        assertTrue(clean.contains("हेल्थ"))
        assertTrue(clean.contains("में से"))
    }

    @Test
    fun unknownLanguageFallsBackToEnglishRules() {
        assertEquals("Health 50 out of 100", SpeechSanitizer.sanitize("HP 50/100", "xx"))
    }

    @Test
    fun emptyInputStaysEmpty() {
        assertEquals("", SpeechSanitizer.sanitize("   ", "en"))
    }
}

package com.blindtechnexus.openfire.audio

/**
 * Cleans game strings before they are handed to a TTS engine so screen-reader
 * announcements stay natural in every supported language.
 *
 * Raw game text can contain UI punctuation ("HP: 200/200 | Reloaded"), percent
 * signs and abbreviations. Spoken naively, English engines read the symbols
 * aloud ("H P colon two hundred slash two hundred pipe..."), which distorts the
 * pronunciation. Indic engines get the same cleanup with language-appropriate
 * words so Devanagari text keeps its natural pronunciation.
 */
object SpeechSanitizer {

    fun sanitize(raw: String, language: String): String {
        var clean = raw
            .replace("|", ", ")
            .replace("_", " ")
            .replace(":", " ")
            .replace("*", " ")
            .replace("#", " ")

        when (language) {
            "hi", "ne" -> {
                clean = clean
                    .replace(Regex("(?i)\\bHP\\b"), "हेल्थ")
                    .replace("/", " में से ")
                    .replace("%", " प्रतिशत ")
            }
            else -> {
                clean = clean
                    .replace(Regex("(?i)\\bHP\\b"), "Health")
                    .replace("/", " out of ")
                    .replace("%", " percent ")
            }
        }

        // Normalize whitespace and tidy punctuation spacing ("word ," -> "word,")
        return clean
            .replace(Regex("\\s+"), " ")
            .replace(" ,", ",")
            .trim()
    }
}

package com.blindtechnexus.openfire.audio

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import com.blindtechnexus.openfire.model.TtsEngineInfo
import com.blindtechnexus.openfire.model.TtsVoiceInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class TtsManager(private val context: Context) {
    companion object {
        private const val TAG = "OpenFireTts"
    }

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    private val _availableEngines = MutableStateFlow<List<TtsEngineInfo>>(emptyList())
    val availableEngines: StateFlow<List<TtsEngineInfo>> = _availableEngines.asStateFlow()

    private val _availableVoices = MutableStateFlow<List<TtsVoiceInfo>>(emptyList())
    val availableVoices: StateFlow<List<TtsVoiceInfo>> = _availableVoices.asStateFlow()

    var speechRate: Float = 1.0f
    var speechVolume: Float = 1.0f
    var currentVoiceName: String = ""
    var currentEnginePackage: String = ""
    var currentLanguage: String = "en"

    init {
        initTts(null)
    }

    fun initTts(enginePackage: String?) {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {}

        isInitialized = false
        currentEnginePackage = enginePackage ?: ""

        tts = TextToSpeech(context, { status ->
            if (status == TextToSpeech.SUCCESS) {
                isInitialized = true
                tts?.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {}
                    override fun onError(utteranceId: String?) {}
                })
                refreshEngines()
                refreshVoices()
                applyVoiceSettings()
            } else {
                Log.e(TAG, "TTS initialization failed with status $status")
            }
        }, enginePackage)
    }

    fun refreshEngines() {
        val enginesList = mutableListOf<TtsEngineInfo>()
        try {
            tts?.engines?.forEach { engine ->
                enginesList.add(TtsEngineInfo(engine.name, engine.label))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching TTS engines", e)
        }
        _availableEngines.value = enginesList
    }

    fun refreshVoices() {
        val voiceList = mutableListOf<TtsVoiceInfo>()
        try {
            tts?.voices?.forEach { voice ->
                val langStr = voice.locale.displayLanguage
                val countryStr = if (voice.locale.country.isNotEmpty()) " (${voice.locale.country})" else ""
                val disp = "${voice.name} - $langStr$countryStr"
                voiceList.add(TtsVoiceInfo(voice.name, disp, voice.locale.language))
            }
            voiceList.sortBy { it.displayName }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching TTS voices", e)
        }

        // Only offer voices that can actually speak the selected app language.
        // Picking a voice from another language family is what garbles English
        // pronunciation (and vice versa).
        val wanted = when (currentLanguage) {
            "hi" -> setOf("hi")
            "ne" -> setOf("ne", "hi") // Hindi voices read Devanagari as fallback
            else -> setOf("en")
        }
        val filtered = voiceList.filter { it.language.lowercase() in wanted }
        _availableVoices.value = if (filtered.isNotEmpty()) filtered else voiceList
    }

    /**
     * Resolves the locale that should speak the current app language.
     * If Nepali ("ne_NP") is missing from the installed TTS engine (common on
     * Android), automatically fall back to Hindi ("hi_IN") which shares the
     * Devanagari script, so English phonetics never attempt to spell
     * Devanagari characters.
     */
    private fun resolveTargetLocale(currentTts: TextToSpeech): Locale {
        var targetLocale = when (currentLanguage) {
            "hi" -> Locale("hi", "IN")
            "ne" -> Locale("ne", "NP")
            else -> Locale.US
        }
        val avail = currentTts.isLanguageAvailable(targetLocale)
        if (currentLanguage == "ne" &&
            (avail == TextToSpeech.LANG_MISSING_DATA || avail == TextToSpeech.LANG_NOT_SUPPORTED)
        ) {
            targetLocale = Locale("hi", "IN")
        }
        return targetLocale
    }

    /**
     * Applies voice and language settings.
     *
     * English-specific fix: a persisted voice from another language (e.g. a
     * Nepali/Hindi voice saved earlier) is never applied to English text, and
     * the engine never silently falls back to the device locale — both were
     * distorting English pronunciation while Devanagari languages sounded fine.
     */
    fun applyVoiceSettings() {
        if (!isInitialized || tts == null) return
        val currentTts = tts ?: return

        currentTts.setSpeechRate(speechRate.coerceIn(0.5f, 2.0f))

        val targetLocale = resolveTargetLocale(currentTts)
        val avail = currentTts.isLanguageAvailable(targetLocale)
        if (avail >= TextToSpeech.LANG_AVAILABLE) {
            currentTts.language = targetLocale
        } else {
            // Keep the requested locale anyway: falling back to the device locale
            // can switch the whole language (e.g. device set to Nepali while the
            // app is in English) which garbles every English announcement.
            try {
                currentTts.language = targetLocale
            } catch (e: Exception) {
                Log.e(TAG, "Failed setting locale $targetLocale", e)
            }
        }

        applyBestVoice(currentTts, targetLocale)
    }

    /**
     * Picks the best voice that can speak [target]:
     *  1. the user-selected voice, but only when it speaks the target language;
     *  2. the engine default voice when it matches;
     *  3. otherwise the highest-quality matching voice (same country preferred).
     */
    private fun applyBestVoice(currentTts: TextToSpeech, target: Locale) {
        val voices = try {
            currentTts.voices
        } catch (e: Exception) {
            Log.e(TAG, "Error reading voices", e)
            null
        } ?: return

        fun speaksTarget(voice: Voice): Boolean =
            voice.locale.language.equals(target.language, ignoreCase = true)

        // 1. Explicitly chosen voice — honored only if it speaks the current language.
        if (currentVoiceName.isNotEmpty()) {
            val selected = voices.find { it.name == currentVoiceName }
            if (selected != null && speaksTarget(selected)) {
                setVoiceIfChanged(currentTts, selected)
                return
            }
        }

        // 2. Engine default voice is usually the best match for its own language.
        val default = try {
            currentTts.defaultVoice
        } catch (e: Exception) {
            null
        }
        if (default != null && speaksTarget(default)) {
            setVoiceIfChanged(currentTts, default)
            return
        }

        // 3. Best remaining match: same country first, then highest quality.
        val candidates = voices.filter { speaksTarget(it) }
        val sameCountry = candidates.filter {
            it.locale.country.isNotEmpty() && it.locale.country.equals(target.country, ignoreCase = true)
        }
        val pool = if (sameCountry.isNotEmpty()) sameCountry else candidates
        val best = pool.sortedWith(
            compareByDescending<Voice> { it.quality }.thenBy { it.name }
        ).firstOrNull()
        if (best != null) {
            setVoiceIfChanged(currentTts, best)
        }
    }

    private fun setVoiceIfChanged(currentTts: TextToSpeech, voice: Voice) {
        val current = try {
            currentTts.voice
        } catch (e: Exception) {
            null
        }
        if (current?.name != voice.name) {
            try {
                currentTts.voice = voice
            } catch (e: Exception) {
                Log.e(TAG, "Failed setting voice ${voice.name}", e)
            }
        }
    }

    /**
     * Sanitizes game strings before sending them to speech synthesis so TTS
     * engines never pronounce raw punctuation, pipes, slashes or abbreviations.
     */
    private fun sanitizeTextForSpeech(raw: String): String =
        SpeechSanitizer.sanitize(raw, currentLanguage)

    fun speak(text: String, interrupt: Boolean = true) {
        if (!isInitialized || tts == null || text.isBlank()) return
        applyVoiceSettings()

        val sanitized = sanitizeTextForSpeech(text)
        if (sanitized.isBlank()) return

        val queueMode = if (interrupt) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, speechVolume.coerceIn(0.05f, 1.0f))
        }
        val utteranceId = "utterance_${System.currentTimeMillis()}"
        tts?.speak(sanitized, queueMode, params, utteranceId)
    }

    fun stop() {
        try {
            tts?.stop()
        } catch (_: Exception) {}
    }

    fun previewVoice(text: String, rate: Float, voiceName: String) {
        if (!isInitialized || tts == null) return
        val currentTts = tts ?: return
        currentTts.stop()
        currentTts.setSpeechRate(rate.coerceIn(0.5f, 2.0f))

        val target = resolveTargetLocale(currentTts)
        if (currentTts.isLanguageAvailable(target) >= TextToSpeech.LANG_AVAILABLE) {
            currentTts.language = target
        }
        if (voiceName.isNotEmpty()) {
            val v = try {
                currentTts.voices?.find { it.name == voiceName }
            } catch (e: Exception) {
                null
            }
            if (v != null && v.locale.language.equals(target.language, ignoreCase = true)) {
                setVoiceIfChanged(currentTts, v)
            } else {
                applyBestVoice(currentTts, target)
            }
        } else {
            applyBestVoice(currentTts, target)
        }

        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, speechVolume.coerceIn(0.05f, 1.0f))
        }
        val sanitized = sanitizeTextForSpeech(text)
        currentTts.speak(sanitized, TextToSpeech.QUEUE_FLUSH, params, "preview")
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
            tts = null
        } catch (_: Exception) {}
    }
}

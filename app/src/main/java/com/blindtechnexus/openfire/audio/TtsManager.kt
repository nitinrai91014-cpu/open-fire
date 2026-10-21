package com.blindtechnexus.openfire.audio

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
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
            Log.e(TAG, "Error fetching voices", e)
        }
        _availableVoices.value = voiceList
    }

    /**
     * Applies voice and language settings.
     * Crucial Fix: If Nepali ("ne_NP") is missing from the installed TTS engine (common on Android),
     * automatically fallback to Hindi ("hi_IN") which shares the Devanagari script.
     * This eliminates the severe pronunciation corruption where English phonetics attempted to spell Devanagari characters.
     */
    fun applyVoiceSettings() {
        if (!isInitialized || tts == null) return
        val currentTts = tts ?: return

        currentTts.setSpeechRate(speechRate.coerceIn(0.5f, 2.0f))

        var targetLocale = when (currentLanguage) {
            "hi" -> Locale("hi", "IN")
            "ne" -> Locale("ne", "NP")
            else -> Locale.US
        }

        var avail = currentTts.isLanguageAvailable(targetLocale)
        if (currentLanguage == "ne" && (avail == TextToSpeech.LANG_MISSING_DATA || avail == TextToSpeech.LANG_NOT_SUPPORTED)) {
            // Fallback to Hindi locale for accurate Devanagari speech
            targetLocale = Locale("hi", "IN")
            avail = currentTts.isLanguageAvailable(targetLocale)
        }

        if (avail >= TextToSpeech.LANG_AVAILABLE) {
            currentTts.language = targetLocale
        } else {
            currentTts.language = Locale.getDefault()
        }

        // Apply custom voice if selected
        if (currentVoiceName.isNotEmpty()) {
            try {
                val match = currentTts.voices?.find { it.name == currentVoiceName }
                if (match != null) {
                    currentTts.voice = match
                    return
                }
            } catch (_: Exception) {}
        }

        // Auto select best matching voice for current locale
        try {
            val voices = currentTts.voices ?: return
            val match = voices.find {
                it.locale.language.equals(targetLocale.language, ignoreCase = true)
            }
            if (match != null) {
                currentTts.voice = match
            }
        } catch (_: Exception) {}
    }

    /**
     * Sanitizes game strings before sending to speech synthesis to prevent TTS engines
     * from pronouncing raw punctuation, pipes, slashes, or English abbreviations in Indic contexts.
     */
    private fun sanitizeTextForSpeech(raw: String): String {
        var clean = raw
            .replace("|", ", ")
            .replace("_", " ")
            .replace(":", " ")
            .replace("*", " ")
            .replace("#", " ")

        when (currentLanguage) {
            "hi", "ne" -> {
                clean = clean
                    .replace(Regex("(?i)\\bHP\\b"), "हेल्थ")
                    .replace("/", " में से ")
            }
            else -> {
                clean = clean
                    .replace(Regex("(?i)\\bHP\\b"), "Health")
                    .replace("/", " out of ")
            }
        }

        // Normalize spaces
        return clean.replace(Regex("\\s+"), " ").trim()
    }

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
        tts?.stop()
        tts?.setSpeechRate(rate.coerceIn(0.5f, 2.0f))
        if (voiceName.isNotEmpty()) {
            val v = tts?.voices?.find { it.name == voiceName }
            if (v != null) tts?.voice = v
        }
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, speechVolume.coerceIn(0.05f, 1.0f))
        }
        val sanitized = sanitizeTextForSpeech(text)
        tts?.speak(sanitized, TextToSpeech.QUEUE_FLUSH, params, "preview")
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
            tts = null
        } catch (_: Exception) {}
    }
}

package com.blindtechnexus.openfire.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.blindtechnexus.openfire.audio.AudioManager
import com.blindtechnexus.openfire.audio.TtsManager
import com.blindtechnexus.openfire.data.PreferencesManager
import com.blindtechnexus.openfire.data.Strings
import com.blindtechnexus.openfire.model.Combat
import com.blindtechnexus.openfire.model.ControlMode
import com.blindtechnexus.openfire.model.DEFAULT_GUNS
import com.blindtechnexus.openfire.model.GameAction
import com.blindtechnexus.openfire.model.GameSettings
import com.blindtechnexus.openfire.model.GameState
import com.blindtechnexus.openfire.model.GameStats
import com.blindtechnexus.openfire.model.GestureRouter
import com.blindtechnexus.openfire.model.HitZone
import com.blindtechnexus.openfire.model.Progression
import com.blindtechnexus.openfire.model.ResultData
import com.blindtechnexus.openfire.model.Revive
import com.blindtechnexus.openfire.model.Screen
import com.blindtechnexus.openfire.model.getHpForLevel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

class GameViewModel(application: Application) : AndroidViewModel(application) {
    val audioManager = AudioManager(application)
    val ttsManager = TtsManager(application)
    private val prefs = PreferencesManager(application)

    private val _settings = MutableStateFlow(prefs.loadSettings())
    val settings: StateFlow<GameSettings> = _settings.asStateFlow()

    private val _stats = MutableStateFlow(Progression.normalize(prefs.loadStats()))
    val stats: StateFlow<GameStats> = _stats.asStateFlow()

    private val _currentScreen = MutableStateFlow(
        if (_settings.value.name.isNotBlank()) Screen.MENU else Screen.WELCOME
    )
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    private val _gameState = MutableStateFlow(GameState())
    val gameState: StateFlow<GameState> = _gameState.asStateFlow()

    private val _resultData = MutableStateFlow(ResultData())
    val resultData: StateFlow<ResultData> = _resultData.asStateFlow()

    private var stepJob: Job? = null
    private var counterJob: Job? = null
    private var devilFireJob: Job? = null
    private var stepCount = 0
    private var foreground = true

    /** Last voice-related settings applied, to avoid resetting TTS while dragging volume. */
    private var syncedVoiceSignature: String? = null

    /** Debounce so one physical tap / swipe can never fire (and spend) more than one bullet. */
    private var lastFireAt = 0L

    private companion object {
        /** The devil holds this distance once it reaches the player. */
        const val ENGAGE_DISTANCE = 2f

        /** Fast retreat: metres gained per flee tick. */
        const val FLEE_STEP = 6f
        const val FLEE_TICK_MS = 160L

        /** Advance tick length. */
        const val ADVANCE_TICK_MS = 550L

        /** Chance that a hit provokes an immediate counter shot. */
        const val COUNTER_FIRE_CHANCE = 0.25f
    }

    init {
        syncSettingsToManagers(_settings.value)
        // Make sure the welcome / menu screen starts with music at the saved volume.
        updateAudioForScreen(_currentScreen.value)
    }

    // ---------------------------------------------------------------------
    // Settings, volume and TTS lifecycle
    // ---------------------------------------------------------------------

    private fun syncSettingsToManagers(s: GameSettings) {
        // These are plain user settings; the AudioManager/TtsManager apply
        // them without ever overwriting them with screen-scaled values.
        audioManager.sfxVolume = s.sfxVolume
        audioManager.musicVolume = s.musicVolume
        audioManager.soundTheme = s.soundTheme
        audioManager.hapticsEnabled = s.hapticsEnabled

        ttsManager.speechRate = s.ttsRate
        ttsManager.speechVolume = s.ttsVolume
        ttsManager.currentLanguage = s.lang
        ttsManager.currentVoiceName = s.voiceName
        if (s.ttsEnginePackage != ttsManager.currentEnginePackage) {
            ttsManager.initTts(s.ttsEnginePackage.ifEmpty { null })
            syncedVoiceSignature = null
        } else {
            // Volume drags update on every frame. Re-applying the voice and
            // speech rate each time can interrupt the current utterance and
            // make the TTS level appear to jump, so only do it when a
            // voice-related setting actually changed.
            val signature = "${s.lang}|${s.voiceName}|${s.ttsRate}|${s.ttsEnginePackage}"
            if (signature != syncedVoiceSignature) {
                ttsManager.applyVoiceSettings()
                syncedVoiceSignature = signature
            }
        }
    }

    fun t(key: String): String = Strings.get(key, _settings.value.lang)

    fun msg(text: String) {
        _gameState.update { it.copy(log = text) }
        ttsManager.speak(text, interrupt = true)
    }

    fun speak(text: String, interrupt: Boolean = true) {
        ttsManager.speak(text, interrupt)
    }

    fun playSound(key: String, volume: Float = 1.0f, posX: Float = 0f, posY: Float = 0f, posZ: Float = 0f) {
        audioManager.playSound(key, volume = volume, posX = posX, posY = posY, posZ = posZ)
    }

    fun submitName(inputName: String) {
        val trimmed = inputName.trim()
        val defaultName = when (_settings.value.lang) {
            "hi" -> "खिलाड़ी"
            "ne" -> "खेलाडी"
            else -> "Player"
        }
        val finalName = if (trimmed.isNotEmpty()) trimmed else defaultName
        val updated = _settings.value.copy(name = finalName)
        _settings.value = updated
        prefs.saveSettings(updated)
        playSound("click")
        _currentScreen.value = Screen.MENU
        speak("${t("welcome")}, $finalName")
        updateAudioForScreen(Screen.MENU)
    }

    fun updateSettings(newSettings: GameSettings) {
        _settings.value = newSettings
        prefs.saveSettings(newSettings)
        syncSettingsToManagers(newSettings)
    }

    /**
     * Volume sliders write straight through to the persisted settings, so the
     * chosen level is used by the very next sound, the next game and after a
     * restart — it can never fall back to an older, lower value.
     */
    fun setMusicVolume(volume: Float) {
        updateSettings(_settings.value.copy(musicVolume = volume.coerceIn(0f, 1f)))
    }

    fun setSfxVolume(volume: Float) {
        updateSettings(_settings.value.copy(sfxVolume = volume.coerceIn(0f, 1f)))
    }

    fun setTtsVolume(volume: Float) {
        updateSettings(_settings.value.copy(ttsVolume = volume.coerceIn(0f, 1f)))
    }

    fun setLanguage(lang: String) {
        val updated = _settings.value.copy(lang = lang)
        updateSettings(updated)
        // Keep the TTS voice list aligned with the newly selected language so
        // only voices that can actually speak it are offered.
        ttsManager.refreshVoices()
    }

    // ---------------------------------------------------------------------
    // Navigation and screen audio
    // ---------------------------------------------------------------------

    fun navigateTo(screen: Screen) {
        stopTimers()
        ttsManager.stop()
        audioManager.stopScreenAudio()
        playSound("click")
        _currentScreen.value = screen
        updateAudioForScreen(screen)

        when (screen) {
            Screen.SETTINGS -> {
                ttsManager.refreshEngines()
                ttsManager.refreshVoices()
                speak(t("settings"))
            }
            Screen.GUIDE -> speak(t("guideTitle"))
            Screen.PROFILE -> speak(
                "${t("profileTitle")}. ${t("round")} ${_stats.value.round}. " +
                    "${t("level")} ${_stats.value.level}. ${t("needWin")} ${_stats.value.needWinsForNext}. " +
                    "${t("coins")} ${_stats.value.coins}."
            )
            Screen.ABOUT -> speak("${t("aboutTitle")}. ${t("developed")}. ${t("version")}")
            Screen.CHANGELOG -> speak(t("changelogTitle"))
            Screen.MENU -> speak(t("mainMenu"))
            Screen.READER -> speak("${t("readerMsg")} ${t("readerUp")}. ${t("readerDown")}.")
            else -> {}
        }
    }

    private fun updateAudioForScreen(screen: Screen) {
        when (screen) {
            Screen.WELCOME, Screen.MENU, Screen.SETTINGS, Screen.GUIDE, Screen.PROFILE,
            Screen.ABOUT, Screen.CHANGELOG -> {
                audioManager.stopScreenAudio()
                audioManager.startMusic()
            }
            Screen.JUMP -> {
                audioManager.stopMusic()
                audioManager.stopScreenAudio()
                // 0.45 is the clip's own base level; the SFX setting is applied once inside AudioManager.
                audioManager.playLoop("airplane", volume = 0.45f)
            }
            Screen.PARA -> {
                audioManager.stopMusic()
                audioManager.stopSound("airplane")
                audioManager.playSound("parachute")
            }
            Screen.LAND -> {
                audioManager.stopMusic()
                audioManager.stopSound("parachute")
            }
            Screen.BATTLE -> {
                audioManager.stopMusic()
                audioManager.stopScreenAudio()
                updateWalkAudio(_gameState.value.distance, _gameState.value.devilFleeing)
            }
            Screen.RESULT -> {
                audioManager.stopMusic()
                audioManager.stopSound("walk")
            }
            else -> {
                audioManager.stopScreenAudio()
            }
        }
    }

    /** Called by the Activity when the app returns to the foreground. */
    fun onAppResumed() {
        foreground = true
        when (_currentScreen.value) {
            Screen.WELCOME, Screen.MENU, Screen.SETTINGS, Screen.GUIDE, Screen.PROFILE,
            Screen.ABOUT, Screen.CHANGELOG -> audioManager.startMusic()
            Screen.JUMP -> audioManager.playLoop("airplane", volume = 0.45f)
            Screen.BATTLE -> {
                val state = _gameState.value
                updateWalkAudio(state.distance, state.devilFleeing)
                if (state.playerHp > 0 && state.devilHp > 0) {
                    // The battle was frozen while hidden: restart both the
                    // continuous fire loop and the movement state machine.
                    startDevilFireLoop(1200L)
                    val needsMovement = state.devilState == "retreat" ||
                        state.devilState == "flank" ||
                        (state.devilState == "advance" && state.distance > ENGAGE_DISTANCE)
                    if (needsMovement) {
                        planDevilStep(400L)
                    }
                }
            }
            else -> {}
        }
    }

    /** Called by the Activity when the app goes to the background. */
    fun onAppPaused() {
        foreground = false
        // No shooting, walking or TTS may continue while the app is hidden.
        stopTimers()
        audioManager.stopAllSounds()
        ttsManager.stop()
    }

    // ---------------------------------------------------------------------
    // Game flow
    // ---------------------------------------------------------------------

    fun startNewGame() {
        resetBattleState()
        navigateTo(Screen.READER)
    }

    fun continueToJump() {
        stopTimers()
        ttsManager.stop()
        audioManager.stopScreenAudio()
        playSound("click")
        _gameState.update { it.copy(log = t("jumpMsg")) }
        _currentScreen.value = Screen.JUMP
        updateAudioForScreen(Screen.JUMP)
        speak(t("jumpMsg"))
    }

    fun doJump() {
        stopTimers()
        audioManager.stopSound("airplane")
        ttsManager.stop()
        playSound("jump")
        _gameState.update { it.copy(log = t("paraMsg")) }
        _currentScreen.value = Screen.PARA
        updateAudioForScreen(Screen.PARA)
        speak(t("paraMsg"))
    }

    fun doPara() {
        stopTimers()
        audioManager.stopSound("parachute")
        ttsManager.stop()
        _gameState.update { it.copy(log = t("landMsg")) }
        _currentScreen.value = Screen.LAND
        updateAudioForScreen(Screen.LAND)
        speak(t("landMsg"))
    }

    fun doLand() {
        stopTimers()
        ttsManager.stop()
        playSound("land")
        _gameState.update { it.copy(log = t("aim")) }
        _currentScreen.value = Screen.BATTLE
        updateAudioForScreen(Screen.BATTLE)
        speak(t("aim"))
        planDevilStep(1300L)
        startDevilFireLoop(2600L)
    }

    private fun devilLoadout(): Pair<Int, Int> {
        val lv = _stats.value.level
        return when {
            lv >= 25 -> Pair(4, 5)
            lv >= 20 -> Pair(3, 4)
            lv >= 10 -> Pair(2, 2)
            else -> Pair(1, 1)
        }
    }

    private fun currentMaxHp(): Int = getHpForLevel(_stats.value.level)

    private fun resetBattleState() {
        stopTimers()
        audioManager.stopScreenAudio()
        val (meds, covers) = devilLoadout()
        val equalHp = currentMaxHp()
        stepCount = 0
        lastFireAt = 0L

        _gameState.value = GameState(
            playerHp = equalHp,
            playerMaxHp = equalHp,
            devilHp = equalHp,
            devilMaxHp = equalHp,
            gunIndex = 0, // Starts with Pistol
            ammo = DEFAULT_GUNS.map { it.mag }, // All three guns start with a full six-bullet magazine
            medkits = 3,
            inCover = false,
            distance = Combat.MAX_RANGE,
            devilCoverHp = 0,
            devilCoverMax = 4,
            devilCoversLeft = covers,
            devilMedkits = meds,
            devilState = "advance",
            devilHitsTaken = 0,
            devilReactAt = 4 + if (Random.nextFloat() < 0.6f) 0 else 1,
            devilRetreats = 0,
            log = t("ready"),
            playerSecondChanceUsed = false,
            devilSecondChanceUsed = false,
            devilItemsLeft = 0,
            devilFleeArmed = true,
            damagedGuns = List(DEFAULT_GUNS.size) { false },
            consecutiveHeavyHits = 0
        )
    }

    // ---------------------------------------------------------------------
    // Devil movement, 3D footsteps, continuous fire
    // ---------------------------------------------------------------------

    /**
     * 3D walking sound. Distance is measured in the 50 m arena; footsteps
     * alternate between the ears, swell as the devil approaches and fade with
     * a lower pitch while it flees into the distance.
     */
    private fun updateWalkAudio(distance: Float, fleeing: Boolean = false) {
        if (_currentScreen.value != Screen.BATTLE) {
            audioManager.stopSound("walk")
            return
        }
        stepCount++
        val lateral = if (stepCount % 2 == 0) -12f else 12f
        audioManager.updateWalk3dAudio(
            distance = distance,
            lateralOffset = lateral,
            fleeing = fleeing
        )
    }

    private fun planDevilStep(delayMs: Long) {
        stepJob?.cancel()
        stepJob = viewModelScope.launch {
            delay(delayMs)
            devilStep()
        }
    }

    private fun devilStep() {
        if (!foreground || _currentScreen.value != Screen.BATTLE) return
        val state = _gameState.value
        if (state.devilHp <= 0 || state.playerHp <= 0) return

        when (state.devilState) {
            "retreat" -> {
                // High-speed flee: the devil can still be hit while running,
                // but its distance grows very quickly.
                val newDist = (state.distance + FLEE_STEP).coerceAtMost(Combat.MAX_RANGE)
                if (newDist >= Combat.MAX_RANGE) {
                    _gameState.update { it.copy(distance = Combat.MAX_RANGE, devilState = "flank") }
                    updateWalkAudio(Combat.MAX_RANGE, fleeing = false)
                    planDevilStep(1300L + Random.nextInt(700))
                } else {
                    _gameState.update { it.copy(distance = newDist) }
                    updateWalkAudio(newDist, fleeing = true)
                    planDevilStep(FLEE_TICK_MS)
                }
            }
            "flank" -> {
                val speed = 4.5f + _stats.value.level * 0.05f
                val newDist = max(ENGAGE_DISTANCE, state.distance - speed)
                if (newDist <= ENGAGE_DISTANCE) {
                    // Back in position: a devil that dropped below its
                    // half-health pool may flee again after the next hit.
                    _gameState.update {
                        it.copy(
                            distance = ENGAGE_DISTANCE,
                            devilState = "advance",
                            devilFleeArmed = true
                        )
                    }
                    playSound("jump", posX = 35f, posZ = -ENGAGE_DISTANCE)
                    updateWalkAudio(ENGAGE_DISTANCE, fleeing = false)
                    msg(t("devilFlank"))
                } else {
                    _gameState.update { it.copy(distance = newDist) }
                    updateWalkAudio(newDist, fleeing = false)
                    planDevilStep(300L)
                }
            }
            else -> {
                if (state.distance > ENGAGE_DISTANCE) {
                    val prox = Combat.proximityOf(state.distance) / 100f
                    val speed = 2.0f + prox * 4.0f + _stats.value.level * 0.03f
                    val newDist = max(ENGAGE_DISTANCE, state.distance - speed)
                    _gameState.update { it.copy(distance = newDist) }
                    updateWalkAudio(newDist, fleeing = false)
                    if (Random.nextFloat() < 0.10f) {
                        playSound("jump", volume = 0.55f, posZ = -newDist)
                    }
                    planDevilStep(ADVANCE_TICK_MS)
                } else {
                    // In position. The separate fire loop keeps shooting; the
                    // footsteps keep a soft presence in both ears.
                    updateWalkAudio(ENGAGE_DISTANCE, fleeing = false)
                }
            }
        }
    }

    /**
     * The devil fires continuously for the whole battle, whether it is
     * advancing, holding position, flanking or fleeing. Its shots use exactly
     * the same four proximity zones as the player's shots.
     */
    private fun startDevilFireLoop(initialDelayMs: Long) {
        devilFireJob?.cancel()
        devilFireJob = viewModelScope.launch {
            delay(initialDelayMs)
            while (true) {
                if (!foreground || _currentScreen.value != Screen.BATTLE) break
                val state = _gameState.value
                if (state.playerHp <= 0 || state.devilHp <= 0) break
                devilAttack()
                delay(devilFireInterval())
            }
        }
    }

    private fun devilFireInterval(): Long {
        val base = (3000L - _stats.value.level * 40L).coerceIn(1600L, 3000L)
        return base + Random.nextLong(0, 600)
    }

    /**
     * Symmetrical Evil Character attack mechanics (same four zones as the player):
     * - Proximity 0% to 40%   -> the devil's shot misses.
     * - Proximity 40% to 70%  -> player loses 10% of max health.
     * - Proximity 70% to 90%  -> player loses 15% of max health.
     * - Proximity 90% to 100% -> player loses 20% of max health.
     *
     * The player survives the first zero-health hit with half their maximum
     * health. Three consecutive 90%-100% hits damage the gun in hand.
     */
    private fun devilAttack() {
        if (!foreground || _currentScreen.value != Screen.BATTLE) return
        var state = _gameState.value
        if (state.devilHp <= 0 || state.playerHp <= 0) return

        val gun = DEFAULT_GUNS[Random.nextInt(DEFAULT_GUNS.size)]
        playSound(gun.sound, volume = 0.9f, posX = 0f, posY = 0f, posZ = -state.distance)
        _gameState.update { it.copy(devilShots = it.devilShots + 1) }
        state = _gameState.value

        val proxPercent = Combat.proximityOf(state.distance)
        val zone = Combat.zoneFor(proxPercent)

        if (zone == HitZone.MISS) {
            // Log only: the 3D gunshot tells the player a shot went wide, and
            // a spoken miss every few seconds would drown out the hit reports.
            _gameState.update { it.copy(consecutiveHeavyHits = 0) }
            setLog("${t("devilMissed")} ${t(gun.nameKey)}.")
            return
        }

        var dealt = Combat.damageFor(state.playerMaxHp, proxPercent)

        // Gloo wall protection reduces impact
        var wallAbsorbed = false
        if (state.inCover) {
            dealt = max(1, (dealt * 0.40f).roundToInt())
            wallAbsorbed = true
        }

        val newPlayerHp = max(0, state.playerHp - dealt)

        // --- Gun damage: three consecutive point-blank hits break the gun in hand.
        var streak = Combat.nextHeavyHitStreak(state.consecutiveHeavyHits, proxPercent)
        var brokeGun = false
        val damaged = state.damagedGuns.toMutableList()
        val activeIndex = state.gunIndex.coerceIn(0, damaged.lastIndex)
        if (!damaged[activeIndex] && Combat.gunBreaks(streak)) {
            damaged[activeIndex] = true
            streak = 0
            brokeGun = true
        }

        _gameState.update {
            it.copy(
                playerHp = newPlayerHp,
                devilHits = it.devilHits + 1,
                devilDamage = it.devilDamage + dealt,
                consecutiveHeavyHits = streak,
                damagedGuns = damaged
            )
        }

        playSound("damage", volume = 1.0f)
        val hitHead = if (zone == HitZone.HEAVY) t("youWereHit") else t("devilGrazed")
        val coverNote = if (wallAbsorbed) " (${t("cover")})" else ""
        var text = "$hitHead ${t(gun.nameKey)}$coverNote. ${t("hitFor")} $dealt. ${t("yourHealth")} $newPlayerHp."
        if (brokeGun) {
            text += " ${t("gunDamaged")} ${t("recovery")}. ${t("coins")}: ${Combat.GUN_RECOVERY_COST}."
        }

        if (newPlayerHp <= 0) {
            if (!state.playerSecondChanceUsed) {
                val revivedHp = Combat.secondChanceHealth(state.playerMaxHp)
                _gameState.update {
                    it.copy(
                        playerHp = revivedHp,
                        playerSecondChanceUsed = true,
                        consecutiveHeavyHits = 0,
                        inCover = false
                    )
                }
                playSound("medkit")
                text += " ${t("playerSecondChance")} ${t("yourHealth")} $revivedHp."
                msg(text)
            } else {
                msg(text)
                endGame(won = false)
            }
            return
        }

        msg(text)
    }

    /** A hit that lands on the devil is sometimes answered immediately. */
    private fun devilOnHit() {
        counterJob?.cancel()
        if (Random.nextFloat() > COUNTER_FIRE_CHANCE) return
        counterJob = viewModelScope.launch {
            // A short reaction pause so a burst of player hits can never
            // stack several counter shots into the same instant.
            delay(350L)
            if (foreground &&
                _currentScreen.value == Screen.BATTLE &&
                _gameState.value.devilHp > 0 &&
                _gameState.value.playerHp > 0
            ) {
                devilAttack()
            }
        }
    }

    /**
     * Devil reactions after a player hit: high-speed flee when its health falls
     * below the half-health pool it received, a medkit when badly hurt, or a
     * fresh cover when its wall was destroyed.
     */
    private fun devilAfterHit(): String {
        val state = _gameState.value
        if (state.devilHp <= 0) return ""

        if (state.devilState == "advance" &&
            Revive.shouldDevilFlee(state.devilHp, state.devilMaxHp, state.devilFleeArmed)
        ) {
            return devilFlee()?.let { " $it" } ?: ""
        }

        var extra = ""
        val hpPct = state.devilHp.toFloat() / state.devilMaxHp.toFloat()

        if (state.devilHitsTaken >= state.devilReactAt) {
            _gameState.update {
                it.copy(
                    devilHitsTaken = 0,
                    devilReactAt = 4 + if (Random.nextFloat() < 0.6f) 0 else 1
                )
            }
            if (hpPct < 0.55f && state.devilMedkits > 0 && state.devilState != "retreat") {
                val healAmount = (state.devilMaxHp * 0.15f).roundToInt().coerceAtLeast(1)
                val healed = min(state.devilMaxHp, state.devilHp + healAmount)
                _gameState.update {
                    it.copy(devilMedkits = it.devilMedkits - 1, devilHp = healed)
                }
                playSound("medkit", posZ = -state.distance)
                extra += " ${t("devilMedkit")} ${t("enemyHealth")} $healed."
            } else if (state.devilCoverHp <= 0 && state.devilCoversLeft > 0) {
                _gameState.update {
                    it.copy(
                        devilCoversLeft = it.devilCoversLeft - 1,
                        devilCoverHp = it.devilCoverMax
                    )
                }
                playSound("gloowall", posZ = -state.distance)
                extra += " ${t("devilCoverUp")} ${t("devilCover")}: ${state.devilCoverMax}."
            }
        }
        return extra
    }

    private fun devilFlee(): String? {
        val state = _gameState.value
        if (!state.devilFleeArmed) return null
        if (state.devilState == "retreat" || state.devilState == "flank") return null
        if (state.devilHp <= 0) return null

        _gameState.update {
            it.copy(
                devilState = "retreat",
                devilFleeArmed = false,
                devilRetreats = it.devilRetreats + 1
            )
        }
        playSound("jump", posZ = -state.distance)
        planDevilStep(120L)
        return t("devilFled")
    }

    // ---------------------------------------------------------------------
    // Player firing
    // ---------------------------------------------------------------------

    /**
     * Symmetrical player firing mechanics:
     * - All three guns hold six bullets; one bullet is consumed per shot.
     * - Proximity 0% to 40%   -> miss.
     * - Proximity 40% to 70%  -> 10% damage.
     * - Proximity 70% to 90%  -> 15% damage.
     * - Proximity 90% to 100% -> 20% damage.
     * - A damaged gun cannot fire until Recovery is paid for.
     * - Hits count even while the devil is fleeing.
     */
    fun fire() {
        if (_currentScreen.value != Screen.BATTLE) return

        // One physical gesture/tap can only ever fire one bullet.
        val now = System.currentTimeMillis()
        if (now - lastFireAt < 250L) return
        lastFireAt = now

        var state = _gameState.value
        if (state.playerHp <= 0 || state.devilHp <= 0) return

        if (state.activeGunDamaged) {
            playSound("ammo")
            msg("${t("gunDamaged")} ${t("recovery")}. ${t("coins")}: ${Combat.GUN_RECOVERY_COST}.")
            return
        }

        // Atomically consume exactly one bullet from the ACTIVE magazine.
        var decremented = false
        _gameState.update { s ->
            val idx = s.gunIndex.coerceIn(0, s.ammo.lastIndex)
            val current = s.ammo[idx]
            if (current <= 0 || s.playerHp <= 0 || s.devilHp <= 0 || s.damagedGuns.getOrElse(idx) { false }) {
                s
            } else {
                decremented = true
                s.copy(
                    ammo = s.ammo.toMutableList().also { it[idx] = current - 1 },
                    playerShots = s.playerShots + 1
                )
            }
        }
        if (!decremented) {
            playSound("ammo")
            msg(t("outOfAmmo"))
            return
        }

        state = _gameState.value
        val gun = DEFAULT_GUNS[state.gunIndex]
        playSound(gun.sound, posX = 0f, posY = 0f, posZ = 0f)

        val proxPercent = Combat.proximityOf(state.distance)
        val zone = Combat.zoneFor(proxPercent)

        // Below 40% proximity: counted as a miss (also while fleeing, but a
        // shot fired before the devil got far away can still land).
        if (zone == HitZone.MISS) {
            msg(t("missed"))
            return
        }

        // The devil's raised cover absorbs the shot until it is destroyed.
        if (state.devilCoverHp > 0) {
            var remaining = 0
            _gameState.update { s ->
                remaining = (s.devilCoverHp - 1).coerceAtLeast(0)
                s.copy(devilCoverHp = remaining)
            }
            playSound("hit", volume = 0.7f)
            playSound("enemyHit", volume = 0.7f, posZ = -state.distance)
            var text = "${t("coverBreak")} ${t("devilCover")}: $remaining. ${t("enemyHealth")} ${state.devilHp}."
            if (remaining <= 0) text += " ${t("devilCoverDown")}"
            msg(text)
            devilOnHit()
            return
        }

        val damage = Combat.damageFor(state.devilMaxHp, proxPercent)
        val newDevilHp = max(0, state.devilHp - damage)
        _gameState.update {
            it.copy(
                devilHp = newDevilHp,
                playerHits = it.playerHits + 1,
                devilHitsTaken = it.devilHitsTaken + 1
            )
        }

        playSound("hit", volume = if (zone == HitZone.HEAVY) 1f else 0.7f)
        playSound("enemyHit", volume = if (zone == HitZone.HEAVY) 1f else 0.7f, posZ = -state.distance)

        var text = "${if (zone == HitZone.HEAVY) t("enemyHit") else t("enemyGrazed")} " +
            "${t("hitFor")} $damage. ${t("enemyHealth")} $newDevilHp."

        if (newDevilHp <= 0) {
            text += " ${resolveDevilZeroHealth()}"
            setLog(text)
            // If the devil actually died the screen is already RESULT.
            if (_currentScreen.value != Screen.BATTLE) return
            msg(text)
            return
        }

        text += devilAfterHit()

        // Ammo pickup chance (15%) — merged into the same announcement so the
        // hit report is never cut off mid-sentence.
        if (Random.nextFloat() < 0.15f) {
            var pickedUp = false
            _gameState.update { s ->
                val idx = s.gunIndex
                val mag = DEFAULT_GUNS[idx].mag
                val current = s.ammo[idx]
                if (current >= mag) {
                    s
                } else {
                    pickedUp = true
                    s.copy(ammo = s.ammo.toMutableList().also { it[idx] = min(mag, current + 2) })
                }
            }
            if (pickedUp) {
                text += " ${t("ammoPickup")}"
            }
        }

        msg(text)
        devilOnHit()
    }

    /**
     * Resolves what happens when the devil's health reaches zero:
     *  1. first zero -> second chance with half its maximum health and all
     *     three survival items (Gloo Wall, cover, medkit) activated;
     *  2. later zeros -> one item is consumed to survive;
     *  3. once every item is exhausted -> the devil dies for good.
     */
    private fun resolveDevilZeroHealth(): String {
        val state = _gameState.value
        val result = Revive.devilAtZero(
            maxHp = state.devilMaxHp,
            itemsLeft = state.devilItemsLeft,
            secondChanceUsed = state.devilSecondChanceUsed
        )

        when (result.outcome) {
            Revive.Outcome.SECOND_CHANCE -> {
                _gameState.update {
                    it.copy(
                        devilHp = result.hp,
                        devilSecondChanceUsed = true,
                        devilItemsLeft = result.itemsLeft,
                        // The survival pack raises the Gloo Wall/cover at once
                        // and stocks one medkit; the three tracked items are
                        // then spent one by one whenever health hits zero.
                        devilCoverHp = it.devilCoverMax,
                        devilMedkits = max(it.devilMedkits, 1),
                        devilState = "advance",
                        devilFleeArmed = true,
                        devilHitsTaken = 0,
                        devilReactAt = 4
                    )
                }
                playSound("medkit", posZ = -state.distance)
                return "${t("devilSecondChance")} ${t("enemyHealth")} ${result.hp}."
            }
            Revive.Outcome.USED_ITEM -> {
                val itemName = when (result.itemIndex) {
                    0 -> t("glooWall")
                    1 -> t("cover")
                    else -> t("medkit")
                }
                _gameState.update {
                    it.copy(
                        devilHp = result.hp,
                        devilItemsLeft = result.itemsLeft,
                        devilState = "advance",
                        devilFleeArmed = true,
                        devilHitsTaken = 0
                    )
                }
                playSound(
                    if (result.itemIndex == 2) "medkit" else "gloowall",
                    posZ = -state.distance
                )
                val exhausted = if (result.itemsLeft == 0) " ${t("devilItemsGone")}" else ""
                return "${t("devilSurvivedWith")} $itemName. ${t("enemyHealth")} ${result.hp}. " +
                    "${t("devilItemsLeft")} ${result.itemsLeft}.$exhausted"
            }
            Revive.Outcome.DEFEATED -> {
                _gameState.update { it.copy(devilHp = 0) }
                endGame(won = true)
                return t("devilDefeatedFinal")
            }
        }
    }

    private fun setLog(text: String) {
        _gameState.update { it.copy(log = text) }
    }

    // ---------------------------------------------------------------------
    // Player actions
    // ---------------------------------------------------------------------

    /**
     * Deducts [amount] coins. Every time coins are spent the coin
     * "money spent" sound (money-spend.mp3) plays — mixed together with the
     * action's own sound (reload, cover, medkit, ...) when one is given.
     */
    private fun spendCoins(amount: Int, actionSounds: List<String> = emptyList()): Boolean {
        if (_stats.value.coins < amount) {
            playSound("ammo")
            speak(t("notEnough"))
            return false
        }
        val newCoins = _stats.value.coins - amount
        val updated = _stats.value.copy(coins = newCoins)
        _stats.value = updated
        prefs.saveStats(updated)
        if (actionSounds.isEmpty()) {
            playSound("spend", volume = 0.9f)
        } else {
            audioManager.playMixed(listOf("spend") + actionSounds, volume = 0.9f)
        }
        return true
    }

    /**
     * Reload mechanism:
     * - Reload costs 30 coins; the coin "money spent" sound plays mixed with
     *   the reload sound (gun-reload.mp3).
     * - Fills the active magazine back to its full six bullets.
     * - Announces "Not enough coins." if insufficient funds.
     */
    fun doReload() {
        if (_currentScreen.value != Screen.BATTLE) return
        val state = _gameState.value
        val idx = state.gunIndex
        val mag = DEFAULT_GUNS[idx].mag
        if (state.damagedGuns.getOrElse(idx) { false }) {
            playSound("ammo")
            msg("${t("gunDamaged")} ${t("recovery")}. ${t("coins")}: ${Combat.GUN_RECOVERY_COST}.")
            return
        }
        if (state.ammo[idx] >= mag) {
            playSound("click")
            msg(t("magFull"))
            return
        }
        if (!spendCoins(30, listOf("reload"))) {
            return
        }
        var filled = false
        _gameState.update { s ->
            val i = s.gunIndex
            val m = DEFAULT_GUNS[i].mag
            if (s.ammo[i] >= m) {
                s
            } else {
                filled = true
                s.copy(ammo = s.ammo.toMutableList().also { list -> list[i] = m })
            }
        }
        if (!filled) {
            playSound("click")
            return
        }
        msg("${t("reloaded")} ${t("coinsSpent")}: 30.")
    }

    /**
     * Recovery: repairs every damaged gun for exactly 300 coins.
     * Reachable from the battle button or a three-finger left swipe.
     */
    fun recoverGun() {
        if (_currentScreen.value != Screen.BATTLE) return
        val state = _gameState.value
        if (!state.anyGunDamaged) {
            playSound("click")
            msg(t("gunNotDamaged"))
            return
        }
        if (!spendCoins(Combat.GUN_RECOVERY_COST, listOf("reload"))) {
            return
        }
        _gameState.update {
            it.copy(
                damagedGuns = List(DEFAULT_GUNS.size) { false },
                consecutiveHeavyHits = 0
            )
        }
        msg("${t("gunRecovered")} ${t("coinsSpent")}: ${Combat.GUN_RECOVERY_COST}.")
    }

    /**
     * Gloo Wall deployment:
     * - Two-finger swipe up (or the cover button / three-finger toggle /
     *   double swipe-up shortcut).
     * - Costs 100 coins; the coin "money spent" sound plays mixed with the
     *   gloo wall sound and the dedicated cover sound effect.
     * - Re-deploying while already in cover is free and simply announced.
     */
    fun deployGlooWall() {
        if (_currentScreen.value != Screen.BATTLE) return
        if (_gameState.value.inCover) {
            playSound("click")
            msg(t("coverOn"))
            return
        }
        if (!spendCoins(100, listOf("gloowall", "cover"))) {
            return
        }
        _gameState.update { it.copy(inCover = true) }
        msg(t("glooWallDeployed"))
    }

    fun toggleCover() {
        if (_currentScreen.value != Screen.BATTLE) return
        val state = _gameState.value
        if (!state.inCover) {
            deployGlooWall()
        } else {
            _gameState.update { it.copy(inCover = false) }
            playSound("click")
            msg(t("coverOff"))
        }
    }

    /**
     * Medkit: costs 100 coins; the coin "money spent" sound plays mixed with
     * the medkit sound effect.
     */
    fun useMedkit() {
        if (_currentScreen.value != Screen.BATTLE) return
        val state = _gameState.value
        if (state.medkits <= 0) {
            playSound("ammo")
            msg(t("noMedkits"))
            return
        }
        if (state.playerHp >= state.playerMaxHp) {
            playSound("click")
            msg("${t("medkitUsed")} ${state.playerHp}")
            return
        }
        if (!spendCoins(100, listOf("medkit"))) {
            return
        }
        var healed = state.playerHp
        _gameState.update { s ->
            val heal = (s.playerMaxHp * 0.30f).roundToInt()
            val newHp = min(s.playerMaxHp, s.playerHp + heal)
            healed = newHp
            s.copy(medkits = s.medkits - 1, playerHp = newHp)
        }
        msg("${t("medkitUsed")} $healed")
    }

    fun switchGun(dir: Int) {
        if (_currentScreen.value != Screen.BATTLE) return
        val current = _gameState.value.gunIndex
        val next = (current + dir + DEFAULT_GUNS.size) % DEFAULT_GUNS.size
        _gameState.update { it.copy(gunIndex = next) }
        playSound("click")
        val damagedNote = if (_gameState.value.damagedGuns.getOrElse(next) { false }) {
            " ${t("damaged")}. ${t("recovery")}: ${Combat.GUN_RECOVERY_COST}."
        } else ""
        msg("${t("switchedTo")} ${t(DEFAULT_GUNS[next].nameKey)}.$damagedNote")
    }

    fun speakStatus(own: Boolean) {
        if (_currentScreen.value != Screen.BATTLE) return
        val state = _gameState.value
        if (own) {
            val gun = DEFAULT_GUNS[state.gunIndex]
            val gunState = if (state.activeGunDamaged) {
                "${t("damaged")}. ${t("recovery")}: ${Combat.GUN_RECOVERY_COST}"
            } else {
                t(gun.nameKey)
            }
            val chance = if (state.playerSecondChanceUsed) " ${t("secondChanceUsed")}." else ""
            speak(
                "${t("yourHealth")} ${state.playerHp}. ${t("ammo")} ${state.ammo[state.gunIndex]}. " +
                    "$gunState. ${if (state.inCover) t("coverOn") else t("coverOff")}. " +
                    "${t("medkits")} ${state.medkits}. ${t("coins")} ${_stats.value.coins}.$chance"
            )
        } else {
            val coverText = if (state.devilCoverHp > 0) " ${t("devilCover")} ${state.devilCoverHp}." else ""
            val itemsText = if (state.devilSecondChanceUsed) {
                " ${t("devilItemsLeft")} ${state.devilsItemCount()}."
            } else ""
            speak(
                "${t("enemyHealth")} ${state.devilHp}. ${t("distance")} ${state.distance.roundToInt()} " +
                    "${t("meters")}.$coverText$itemsText"
            )
        }
    }

    private fun GameState.devilsItemCount(): Int = devilItemsLeft.coerceAtLeast(0)

    fun exitBattle() {
        stopTimers()
        audioManager.stopSound("walk")
        audioManager.stopScreenAudio()
        ttsManager.stop()
        playSound("click")
        _currentScreen.value = Screen.MENU
        updateAudioForScreen(Screen.MENU)
        speak(t("exited"))
    }

    // ---------------------------------------------------------------------
    // End game and progression
    // ---------------------------------------------------------------------

    /**
     * End Game & Level/Round Progression:
     * - 10 wins promote one level.
     * - Completing level 30 ends the round and begins the next round at level 1.
     * - Immediately plays victory/defeat sound via managed audio channel.
     */
    private fun endGame(won: Boolean) {
        stopTimers()
        audioManager.stopSound("walk")

        val state = _gameState.value
        val base = if (won) 150 else 30
        var gain = (base + state.playerHits * 10 + max(0, state.playerHp) * 0.5f).roundToInt()
        if (gain < 20) gain = 20

        val oldStats = _stats.value
        var updatedStats = oldStats.copy(
            coins = oldStats.coins + gain,
            games = oldStats.games + 1,
            wins = if (won) oldStats.wins + 1 else oldStats.wins,
            kills = if (won) oldStats.kills + 1 else oldStats.kills
        )

        var leveled = false
        var roundCompleted = false
        if (won) {
            val promotion = Progression.applyWin(updatedStats)
            updatedStats = promotion.stats
            leveled = promotion.leveledUp
            roundCompleted = promotion.roundCompleted
        }

        _stats.value = updatedStats
        prefs.saveStats(updatedStats)

        _resultData.value = ResultData(
            won = won,
            coins = gain,
            hits = state.playerHits,
            shots = state.playerShots,
            devilShots = state.devilShots,
            hp = state.playerHp,
            levelUp = leveled,
            roundComplete = roundCompleted,
            round = updatedStats.round,
            level = updatedStats.level
        )

        _currentScreen.value = Screen.RESULT
        updateAudioForScreen(Screen.RESULT)
        playSound(if (won) "win" else "lose")

        var speech = if (won) t("victoryMsg") else t("defeatMsg")
        speech += " ${t("earned")} $gain."
        if (roundCompleted) {
            speech += " ${t("roundCompleted")} ${t("round")} ${updatedStats.round}!"
        } else if (leveled) {
            speech += " ${t("newLevel")} ${updatedStats.level}."
        }
        speak(speech)
    }

    /**
     * Play Again:
     * Immediately stops victory or defeat audio before restarting the game.
     */
    fun playAgain() {
        stopTimers()
        audioManager.stopSound("win")
        audioManager.stopSound("lose")
        audioManager.stopScreenAudio()
        ttsManager.stop()
        playSound("click")
        resetBattleState()
        _currentScreen.value = Screen.JUMP
        _gameState.update { it.copy(log = t("jumpMsg")) }
        updateAudioForScreen(Screen.JUMP)
        speak(t("jumpMsg"))
    }

    // ---------------------------------------------------------------------
    // Stats
    // ---------------------------------------------------------------------

    /**
     * Completely wipes the player's statistics: games, wins, kills, coins and
     * progress all go back to zero, and level and round go back to 1.
     * The name and audio settings are kept so the app stays usable.
     */
    fun clearStats() {
        stopTimers()
        audioManager.stopSound("walk")
        ttsManager.stop()

        val cleared = GameStats.cleared()
        prefs.clearStats(cleared)
        _stats.value = cleared
        _resultData.value = ResultData()
        resetBattleState()
        // resetBattleState stops screen audio; the Profile screen must keep
        // its menu music after the wipe.
        updateAudioForScreen(_currentScreen.value)

        playSound("spend", volume = 0.9f)
        msg(t("statsCleared"))
    }

    // ---------------------------------------------------------------------
    // Gestures
    // ---------------------------------------------------------------------

    fun handleGesture(direction: String, fingers: Int) {
        val mode = _settings.value.mode
        if (mode == ControlMode.BUTTONS) return

        when (GestureRouter.route(_currentScreen.value, direction, fingers)) {
            GameAction.CONTINUE -> continueToJump()
            GameAction.BACK_TO_MENU -> navigateTo(Screen.MENU)
            GameAction.JUMP -> doJump()
            GameAction.OPEN_PARA -> doPara()
            GameAction.LAND -> doLand()
            GameAction.FIRE -> fire()
            GameAction.RELOAD -> doReload()
            GameAction.NEXT_GUN -> switchGun(1)
            GameAction.PREV_GUN -> switchGun(-1)
            // Two-finger swipe up: Gloo Wall (cover) for 100 coins.
            GameAction.GLOO_WALL -> deployGlooWall()
            GameAction.MEDKIT -> useMedkit()
            GameAction.TOGGLE_COVER -> toggleCover()
            GameAction.EXIT_BATTLE -> exitBattle()
            // Three-finger swipe left: repair every damaged gun for 300 coins.
            GameAction.RECOVER_GUN -> recoverGun()
            GameAction.STATUS_OWN -> speakStatus(own = true)
            GameAction.STATUS_ENEMY -> speakStatus(own = false)
            GameAction.PLAY_AGAIN -> playAgain()
            GameAction.NONE -> {}
        }
    }

    private fun stopTimers() {
        stepJob?.cancel()
        stepJob = null
        counterJob?.cancel()
        counterJob = null
        devilFireJob?.cancel()
        devilFireJob = null
    }

    override fun onCleared() {
        super.onCleared()
        stopTimers()
        audioManager.release()
        ttsManager.shutdown()
    }
}

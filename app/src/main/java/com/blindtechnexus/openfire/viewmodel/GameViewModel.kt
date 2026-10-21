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
import com.blindtechnexus.openfire.model.ResultData
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

    private val _stats = MutableStateFlow(prefs.loadStats())
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
    private var stepCount = 0

    /** Debounce so one physical tap / swipe can never fire (and spend) more than one bullet. */
    private var lastFireAt = 0L

    init {
        syncSettingsToManagers(_settings.value)
    }

    private fun syncSettingsToManagers(s: GameSettings) {
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
        } else {
            ttsManager.applyVoiceSettings()
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

    fun setLanguage(lang: String) {
        val updated = _settings.value.copy(lang = lang)
        updateSettings(updated)
        // Keep the TTS voice list aligned with the newly selected language so
        // only voices that can actually speak it are offered.
        ttsManager.refreshVoices()
    }

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
            Screen.PROFILE -> speak("${t("profileTitle")}. ${t("round")} ${_stats.value.round}. ${t("level")} ${_stats.value.level}. ${t("needWin")} ${_stats.value.needWinsForNext}.")
            Screen.ABOUT -> speak("${t("aboutTitle")}. ${t("developed")}. ${t("version")}")
            Screen.CHANGELOG -> speak(t("changelogTitle"))
            Screen.MENU -> speak(t("mainMenu"))
            Screen.READER -> speak("${t("readerMsg")} ${t("readerUp")}. ${t("readerDown")}.")
            else -> {}
        }
    }

    private fun updateAudioForScreen(screen: Screen) {
        when (screen) {
            Screen.WELCOME, Screen.MENU, Screen.SETTINGS, Screen.GUIDE, Screen.PROFILE, Screen.ABOUT, Screen.CHANGELOG -> {
                audioManager.stopScreenAudio()
                audioManager.startMusic(_settings.value.musicVolume * 0.35f)
            }
            Screen.JUMP -> {
                audioManager.stopMusic()
                audioManager.stopScreenAudio()
                audioManager.playLoop("airplane", volume = _settings.value.sfxVolume * 0.45f)
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
                updateWalkAudio(_gameState.value.distance)
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

        _gameState.value = GameState(
            playerHp = equalHp,
            playerMaxHp = equalHp,
            devilHp = equalHp,
            devilMaxHp = equalHp,
            gunIndex = 0, // Starts with Pistol
            ammo = DEFAULT_GUNS.map { it.mag }, // All three guns start with a full six-bullet magazine
            medkits = 3,
            inCover = false,
            distance = 100f,
            devilCoverHp = 0,
            devilCoverMax = 4,
            devilCoversLeft = covers,
            devilMedkits = meds,
            devilState = "advance",
            devilHitsTaken = 0,
            devilReactAt = 4 + if (Random.nextFloat() < 0.6f) 0 else 1,
            devilRetreats = 0,
            log = t("ready"),
            hitStreak = 0
        )
    }

    /**
     * Enhanced 3D walking sound:
     * As the devil advances, distance decreases from 100 to 0.
     * Footsteps alternate between ears (-12f to +12f) to create a vivid physical sensation of approaching steps.
     */
    private fun updateWalkAudio(distance: Float) {
        if (_currentScreen.value != Screen.BATTLE) {
            audioManager.stopSound("walk")
            return
        }
        val lateral = if (stepCount % 2 == 0) -12f else 12f
        audioManager.updateWalk3dAudio(distance = distance, lateralOffset = lateral)
    }

    private fun planDevilStep(delayMs: Long) {
        stepJob?.cancel()
        stepJob = viewModelScope.launch {
            delay(delayMs)
            devilStep()
        }
    }

    private fun devilStep() {
        if (_currentScreen.value != Screen.BATTLE) return
        val state = _gameState.value
        if (state.devilHp <= 0 || state.playerHp <= 0) return

        stepCount++

        if (state.devilState == "retreat") {
            val newDist = (state.distance + 14f).coerceAtMost(95f)
            if (newDist >= 95f) {
                _gameState.update { it.copy(distance = 95f, devilState = "flank") }
                updateWalkAudio(95f)
                planDevilStep((1800 + Random.nextInt(800)).toLong())
            } else {
                _gameState.update { it.copy(distance = newDist) }
                updateWalkAudio(newDist)
                planDevilStep(350L)
            }
            return
        }

        if (state.devilState == "flank") {
            val speed = 18f + _stats.value.level * 0.4f
            val newDist = max(28f, state.distance - speed)
            if (newDist <= 28f) {
                _gameState.update { it.copy(distance = 28f, devilState = "advance") }
                playSound("jump", posX = 35f, posZ = -28f)
                updateWalkAudio(28f)
                msg(t("devilFlank"))
                devilAttack(isCounter = false)
            } else {
                _gameState.update { it.copy(distance = newDist) }
                updateWalkAudio(newDist)
                planDevilStep(280L)
            }
            return
        }

        val prox = (1.0f - state.distance / 100.0f).coerceIn(0f, 1f)
        if (state.distance > 28f) {
            val speed = 7f + prox * 9f + _stats.value.level * 0.2f
            val newDist = max(28f, state.distance - speed)
            _gameState.update { it.copy(distance = newDist) }
            updateWalkAudio(newDist)
            if (Random.nextFloat() < 0.12f) {
                playSound("jump", volume = 0.55f, posZ = -newDist)
            }
            val delayMs = max(320L, (800L - (prox * 450L).toLong()))
            planDevilStep(delayMs)
        } else {
            audioManager.stopSound("walk")
            devilAttack(isCounter = false)
        }
    }

    /**
     * Symmetrical Evil Character attack mechanics (same three zones as the player):
     * - Proximity 0% to 40%   -> the devil's shot misses.
     * - Proximity 40% to 70%  -> player loses 10% of max health.
     * - Proximity 70% to 100% -> player loses 20% of max health.
     * - The player dies only when health actually reaches zero — never after a
     *   fixed number of hits.
     */
    private fun devilAttack(isCounter: Boolean) {
        if (_currentScreen.value != Screen.BATTLE) return
        val state = _gameState.value
        if (state.devilHp <= 0 || state.playerHp <= 0) return

        val gun = DEFAULT_GUNS[Random.nextInt(DEFAULT_GUNS.size)]
        playSound(gun.sound, volume = 0.9f, posX = 0f, posY = 0f, posZ = -state.distance)
        _gameState.update { it.copy(devilShots = it.devilShots + 1) }

        val proxPercent = Combat.proximityOf(state.distance)
        val zone = Combat.zoneFor(proxPercent)

        if (zone == HitZone.MISS) {
            msg("${t("devilMissed")} ${t(gun.nameKey)}.")
            if (!isCounter) {
                planDevilStep(1200L + Random.nextInt(600))
            }
            return
        }

        // Zone damage: 10% between 40-70%, 20% between 70-100%.
        var dealt = Combat.damageFor(state.playerMaxHp, proxPercent)

        // Gloo wall protection reduces impact
        var wallAbsorbed = false
        if (state.inCover) {
            dealt = max(1, (dealt * 0.40f).roundToInt())
            wallAbsorbed = true
        }

        val newPlayerHp = max(0, state.playerHp - dealt)
        _gameState.update {
            it.copy(
                playerHp = newPlayerHp,
                devilHits = it.devilHits + 1,
                devilDamage = it.devilDamage + dealt
            )
        }

        playSound("damage", volume = 1.0f)
        val hitHead = if (zone == HitZone.HEAVY) t("youWereHit") else t("devilGrazed")
        val coverNote = if (wallAbsorbed) " (${t("cover")})" else ""
        msg("$hitHead ${t(gun.nameKey)}$coverNote. ${t("hitFor")} $dealt. ${t("yourHealth")} $newPlayerHp.")

        if (newPlayerHp <= 0) {
            endGame(won = false)
            return
        }

        if (!isCounter) {
            planDevilStep(1200L + Random.nextInt(700))
        }
    }

    /**
     * The devil reacts after taking a number of hits: heals, raises cover or
     * retreats. Returns an optional announcement so callers can merge it into
     * a single, uninterrupted screen-reader message.
     */
    private fun devilReact(): String? {
        val state = _gameState.value
        if (state.devilHp <= 0) return null
        val hpPct = state.devilHp.toFloat() / state.devilMaxHp.toFloat()

        if (hpPct < 0.55f && state.devilMedkits > 0) {
            val healAmount = (state.devilMaxHp * 0.30f).roundToInt()
            val healed = min(state.devilMaxHp, state.devilHp + healAmount)
            _gameState.update { it.copy(devilMedkits = it.devilMedkits - 1, devilHp = healed) }
            playSound("medkit", posZ = -state.distance)
            return "${t("devilMedkit")} ${t("enemyHealth")} $healed."
        }

        if (state.devilCoverHp <= 0 && state.devilCoversLeft > 0) {
            _gameState.update { it.copy(devilCoversLeft = it.devilCoversLeft - 1, devilCoverHp = it.devilCoverMax) }
            playSound("gloowall", posZ = -state.distance)
            return "${t("devilCoverUp")} ${t("devilCover")}: ${state.devilCoverMax}."
        }

        if (hpPct < 0.45f) {
            return devilRetreat()
        }
        return null
    }

    private fun devilRetreat(): String? {
        val state = _gameState.value
        if (state.devilState == "retreat" || state.devilState == "flank") return null
        if (state.devilRetreats >= 2) return null

        counterJob?.cancel()
        _gameState.update { it.copy(devilState = "retreat", devilRetreats = it.devilRetreats + 1) }
        playSound("jump", posZ = -state.distance)
        planDevilStep(300L)
        return t("devilFled")
    }

    private fun devilOnHit() {
        counterJob?.cancel()
        counterJob = viewModelScope.launch {
            delay(280L)
            if (_currentScreen.value == Screen.BATTLE && _gameState.value.devilHp > 0 && _gameState.value.playerHp > 0) {
                devilAttack(isCounter = true)
            }
        }
    }

    /**
     * Symmetrical Player Firing Mechanics:
     * - All three guns (pistol, assault rifle, shotgun) hold six bullets and
     *   exactly ONE bullet is consumed per shot; reload is required only after
     *   all six bullets are fired.
     * - Proximity 0% to 40%   -> miss.
     * - Proximity 40% to 70%  -> the devil loses 10% of max health.
     * - Proximity 70% to 100% -> the devil loses 20% of max health (proper hit).
     * - The devil dies only when its health actually reaches zero — there is no
     *   preset where it falls after a fixed number of hits.
     */
    fun fire() {
        if (_currentScreen.value != Screen.BATTLE) return

        // One physical gesture/tap can only ever fire one bullet.
        val now = System.currentTimeMillis()
        if (now - lastFireAt < 250L) return
        lastFireAt = now

        var state = _gameState.value
        if (state.playerHp <= 0 || state.devilHp <= 0) return

        if (state.devilState == "retreat") {
            playSound("ammo")
            msg(t("missed"))
            return
        }

        // Atomically consume exactly one bullet from the ACTIVE magazine,
        // computed from the very latest state — a stale snapshot can never wipe
        // (or restore) a whole magazine after a reload.
        var decremented = false
        _gameState.update { s ->
            val idx = s.gunIndex
            val current = s.ammo[idx]
            if (current <= 0 || s.playerHp <= 0 || s.devilHp <= 0 || s.devilState == "retreat") {
                s
            } else {
                decremented = true
                s.copy(
                    ammo = s.ammo.toMutableList().also { it[idx] = current - 1 },
                    playerShots = s.playerShots + 1,
                    hitStreak = s.hitStreak + 1
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

        // Below 40% proximity: counted as a miss.
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

        // Exact zone damage: 10% for 40-70%, 20% for 70-100% of max health.
        val damage = Combat.damageFor(state.devilMaxHp, proxPercent)
        val newDevilHp = max(0, state.devilHp - damage)
        _gameState.update {
            it.copy(
                devilHp = newDevilHp,
                playerHits = it.playerHits + 1
            )
        }

        playSound("hit", volume = if (zone == HitZone.HEAVY) 1f else 0.7f)
        playSound("enemyHit", volume = if (zone == HitZone.HEAVY) 1f else 0.7f, posZ = -state.distance)

        var text = "${if (zone == HitZone.HEAVY) t("enemyHit") else t("enemyGrazed")} ${t("hitFor")} $damage. ${t("enemyHealth")} $newDevilHp."

        if (newDevilHp <= 0) {
            // Defeat only at exactly zero health — no preset hit counters.
            setLog(text)
            endGame(won = true)
            return
        }

        // Devil reaction after a fair number of hits (heal / cover / retreat).
        val hitsTaken = state.devilHitsTaken + 1
        if (hitsTaken >= state.devilReactAt) {
            _gameState.update {
                it.copy(
                    devilHitsTaken = 0,
                    devilReactAt = 4 + if (Random.nextFloat() < 0.6f) 0 else 1
                )
            }
            val reactText = devilReact()
            if (reactText != null) text += " $reactText"
        } else if (_gameState.value.devilHp.toFloat() / state.devilMaxHp.toFloat() < 0.30f &&
            state.devilState != "retreat" && state.devilState != "flank"
        ) {
            val retreatText = devilRetreat()
            if (retreatText != null) text += " $retreatText"
        }

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

    private fun setLog(text: String) {
        _gameState.update { it.copy(log = text) }
    }

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
        msg("${t("switchedTo")} ${t(DEFAULT_GUNS[next].nameKey)}")
    }

    fun speakStatus(own: Boolean) {
        if (_currentScreen.value != Screen.BATTLE) return
        val state = _gameState.value
        if (own) {
            val gun = DEFAULT_GUNS[state.gunIndex]
            speak("${t("yourHealth")} ${state.playerHp}. ${t("ammo")} ${state.ammo[state.gunIndex]}. ${t(gun.nameKey)}. ${if (state.inCover) t("coverOn") else t("coverOff")}. ${t("medkits")} ${state.medkits}. ${t("coins")} ${_stats.value.coins}.")
        } else {
            val coverText = if (state.devilCoverHp > 0) " ${t("devilCover")} ${state.devilCoverHp}." else ""
            speak("${t("enemyHealth")} ${state.devilHp}. ${t("distance")} ${state.distance.roundToInt()} ${t("meters")}.$coverText")
        }
    }

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

    /**
     * End Game & Level/Round Progression:
     * - Completing level 30 ends the round and begins the next round.
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
        val newCoins = oldStats.coins + gain
        val newGames = oldStats.games + 1
        val newWins = if (won) oldStats.wins + 1 else oldStats.wins
        val newKills = if (won) oldStats.kills + 1 else oldStats.kills

        var newRound = oldStats.round
        var newLevel = oldStats.level
        var leveled = false
        var roundCompleted = false

        if (won) {
            if (newWins % 10 == 0) {
                if (oldStats.level >= 30) {
                    newRound = oldStats.round + 1
                    newLevel = 1
                    roundCompleted = true
                    leveled = true
                } else {
                    newLevel = oldStats.level + 1
                    leveled = true
                }
            }
        }

        val updatedStats = oldStats.copy(
            coins = newCoins,
            games = newGames,
            wins = newWins,
            kills = newKills,
            round = newRound,
            level = newLevel
        )
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
            round = newRound,
            level = newLevel
        )

        _currentScreen.value = Screen.RESULT
        updateAudioForScreen(Screen.RESULT)
        playSound(if (won) "win" else "lose")

        var speech = if (won) t("victoryMsg") else t("defeatMsg")
        speech += " ${t("earned")} $gain."
        if (roundCompleted) {
            speech += " ${t("roundCompleted")} ${t("round")} $newRound!"
        } else if (leveled) {
            speech += " ${t("newLevel")} $newLevel."
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
    }

    override fun onCleared() {
        super.onCleared()
        stopTimers()
        audioManager.release()
        ttsManager.shutdown()
    }
}

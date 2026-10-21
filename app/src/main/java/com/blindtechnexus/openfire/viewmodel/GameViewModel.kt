package com.blindtechnexus.openfire.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.blindtechnexus.openfire.audio.AudioManager
import com.blindtechnexus.openfire.audio.TtsManager
import com.blindtechnexus.openfire.data.PreferencesManager
import com.blindtechnexus.openfire.data.Strings
import com.blindtechnexus.openfire.model.ControlMode
import com.blindtechnexus.openfire.model.DEFAULT_GUNS
import com.blindtechnexus.openfire.model.GameSettings
import com.blindtechnexus.openfire.model.GameState
import com.blindtechnexus.openfire.model.GameStats
import com.blindtechnexus.openfire.model.ResultData
import com.blindtechnexus.openfire.model.Screen
import com.blindtechnexus.openfire.model.SoundTheme
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
    }

    fun navigateTo(screen: Screen) {
        stopTimers()
        ttsManager.stop()
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
            Screen.PROFILE -> speak("${t("profileTitle")}. ${t("level")} ${_stats.value.level}. ${t("needWin")} ${_stats.value.needWinsForNext}.")
            Screen.ABOUT -> speak("${t("aboutTitle")}. ${t("developed")}")
            Screen.MENU -> speak(t("mainMenu"))
            Screen.READER -> speak("${t("readerMsg")} ${t("readerUp")}. ${t("readerDown")}.")
            else -> {}
        }
    }

    private fun updateAudioForScreen(screen: Screen) {
        when (screen) {
            Screen.WELCOME, Screen.MENU, Screen.SETTINGS, Screen.GUIDE, Screen.PROFILE, Screen.ABOUT -> {
                audioManager.stopLoop("walk")
                audioManager.stopLoop("airplane")
                audioManager.startMusic(_settings.value.musicVolume * 0.35f)
            }
            Screen.JUMP -> {
                audioManager.stopMusic()
                audioManager.playLoop("airplane", volume = _settings.value.sfxVolume * 0.4f)
            }
            Screen.BATTLE -> {
                audioManager.stopMusic()
                audioManager.stopLoop("airplane")
                updateWalkAudio(_gameState.value.distance)
            }
            else -> {
                audioManager.stopMusic()
                audioManager.stopLoop("walk")
                audioManager.stopLoop("airplane")
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
        playSound("click")
        _gameState.update { it.copy(log = t("jumpMsg")) }
        _currentScreen.value = Screen.JUMP
        updateAudioForScreen(Screen.JUMP)
        speak(t("jumpMsg"))
    }

    fun doJump() {
        stopTimers()
        audioManager.stopLoop("airplane")
        ttsManager.stop()
        playSound("jump")
        _gameState.update { it.copy(log = t("paraMsg")) }
        _currentScreen.value = Screen.PARA
        speak(t("paraMsg"))
    }

    fun doPara() {
        stopTimers()
        ttsManager.stop()
        playSound("parachute")
        _gameState.update { it.copy(log = t("landMsg")) }
        _currentScreen.value = Screen.LAND
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
            lv >= 21 -> Pair(7, 8)
            lv >= 16 -> Pair(5, 7)
            lv >= 11 -> Pair(2, 3)
            else -> Pair(1, 1)
        }
    }

    private fun resetBattleState() {
        stopTimers()
        val (meds, covers) = devilLoadout()
        val pMaxHp = _settings.value.playerMaxHp
        val dMaxHp = _settings.value.devilMaxHp

        _gameState.value = GameState(
            playerHp = pMaxHp,
            playerMaxHp = pMaxHp,
            devilHp = dMaxHp,
            devilMaxHp = dMaxHp,
            gunIndex = 0,
            ammo = listOf(DEFAULT_GUNS[0].mag, DEFAULT_GUNS[1].mag, DEFAULT_GUNS[2].mag),
            medkits = 3,
            inCover = false,
            distance = 100f,
            devilCoverHp = 0,
            devilCoverMax = 6,
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

    private fun updateWalkAudio(distance: Float) {
        if (_currentScreen.value != Screen.BATTLE) {
            audioManager.stopLoop("walk")
            return
        }
        val prox = (1.0f - distance / 100.0f).coerceIn(0f, 1f)
        val vol = (0.38f + prox * 0.62f).coerceIn(0f, 1f)
        val rate = 0.4f + prox * 1.6f
        audioManager.playLoop("walk", volume = vol, rate = rate, posX = 0f, posY = 0f, posZ = -distance)
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

        if (state.devilState == "retreat") {
            val newDist = (state.distance + 14f).coerceAtMost(95f)
            if (newDist >= 95f) {
                _gameState.update { it.copy(distance = 95f, devilState = "flank") }
                updateWalkAudio(95f)
                planDevilStep((1800 + Random.nextInt(1000)).toLong())
            } else {
                _gameState.update { it.copy(distance = newDist) }
                updateWalkAudio(newDist)
                planDevilStep(350L)
            }
            return
        }

        if (state.devilState == "flank") {
            val speed = 18f + _stats.value.level * 0.5f
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
            val speed = 7f + prox * 9f + _stats.value.level * 0.25f
            val newDist = max(28f, state.distance - speed)
            _gameState.update { it.copy(distance = newDist) }
            updateWalkAudio(newDist)
            if (Random.nextFloat() < 0.14f) {
                playSound("jump", volume = 0.55f, posZ = -newDist)
            }
            val delayMs = max(320L, (800L - (prox * 450L).toLong()))
            planDevilStep(delayMs)
        } else {
            audioManager.stopLoop("walk")
            devilAttack(isCounter = false)
        }
    }

    private fun devilAttack(isCounter: Boolean) {
        if (_currentScreen.value != Screen.BATTLE) return
        val state = _gameState.value
        if (state.devilHp <= 0 || state.playerHp <= 0) return

        val gun = DEFAULT_GUNS[Random.nextInt(DEFAULT_GUNS.size)]
        playSound(gun.sound, volume = 0.9f, posX = 0f, posY = 0f, posZ = -state.distance)
        _gameState.update { it.copy(devilShots = it.devilShots + 1) }

        var acc = when {
            state.distance <= 35f -> 0.55f
            state.distance <= 65f -> 0.30f
            else -> 0.10f
        }
        if (isCounter) acc *= 0.55f
        if (state.inCover) acc *= 0.45f

        val roll = Random.nextFloat()
        val outcome = when {
            roll < acc -> "hit"
            roll < acc + 0.18f -> "graze"
            else -> "miss"
        }

        var dealt = when (outcome) {
            "hit" -> (gun.dmg * _settings.value.devilDamageMult).roundToInt()
            "graze" -> ((gun.dmg * 0.35f) * _settings.value.devilDamageMult).roundToInt()
            else -> 0
        }

        if (dealt > 0) {
            if (state.inCover) dealt = (dealt * 0.55f).roundToInt()
            val newPlayerHp = max(0, state.playerHp - dealt)
            _gameState.update {
                it.copy(
                    playerHp = newPlayerHp,
                    devilHits = it.devilHits + 1,
                    devilDamage = it.devilDamage + dealt
                )
            }
            playSound("damage", volume = 1.0f)
            val head = if (outcome == "hit") t("youWereHit") else t("devilGrazed")
            msg("$head ${t(gun.nameKey)}. ${t("hitFor")} $dealt. ${t("yourHealth")} $newPlayerHp.")
            if (newPlayerHp <= 0) {
                endGame(won = false)
                return
            }
        } else {
            msg("${t("devilMissed")} ${t(gun.nameKey)}.")
        }

        if (!isCounter) {
            planDevilStep(1200L + Random.nextInt(700))
        }
    }

    private fun devilReact() {
        val state = _gameState.value
        if (state.devilHp <= 0) return
        val hpPct = state.devilHp.toFloat() / state.devilMaxHp.toFloat()

        if (hpPct < 0.55f && state.devilMedkits > 0) {
            val healed = min(state.devilMaxHp, state.devilHp + 60)
            _gameState.update { it.copy(devilMedkits = it.devilMedkits - 1, devilHp = healed) }
            playSound("medkit", posZ = -state.distance)
            msg("${t("devilMedkit")} ${t("enemyHealth")} $healed.")
            return
        }

        if (state.devilCoverHp <= 0 && state.devilCoversLeft > 0) {
            _gameState.update { it.copy(devilCoversLeft = it.devilCoversLeft - 1, devilCoverHp = it.devilCoverMax) }
            playSound("gloowall", posZ = -state.distance)
            msg("${t("devilCoverUp")} ${t("devilCover")}: ${state.devilCoverMax}.")
            return
        }

        if (hpPct < 0.50f) {
            devilRetreat()
        }
    }

    private fun devilRetreat() {
        val state = _gameState.value
        if (state.devilState == "retreat" || state.devilState == "flank") return
        if (state.devilRetreats >= 2) return

        counterJob?.cancel()
        _gameState.update { it.copy(devilState = "retreat", devilRetreats = it.devilRetreats + 1) }
        playSound("jump", posZ = -state.distance)
        msg(t("devilFled"))
        planDevilStep(300L)
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

    fun fire() {
        if (_currentScreen.value != Screen.BATTLE) return
        val state = _gameState.value
        if (state.playerHp <= 0 || state.devilHp <= 0) return

        if (state.devilState == "retreat") {
            playSound("ammo")
            msg(t("missed"))
            return
        }

        val gun = DEFAULT_GUNS[state.gunIndex]
        val currentAmmo = state.ammo[state.gunIndex]
        if (currentAmmo <= 0) {
            playSound("ammo")
            msg(t("outOfAmmo"))
            return
        }

        val newAmmoList = state.ammo.toMutableList().apply { this[state.gunIndex] = currentAmmo - 1 }
        _gameState.update { it.copy(ammo = newAmmoList, playerShots = it.playerShots + 1) }
        playSound(gun.sound, posX = 0f, posY = 0f, posZ = 0f)

        val prox = (1.0f - state.distance / 100.0f).coerceIn(0f, 1f)
        val outcome = when {
            prox < 0.40f -> "miss"
            prox < 0.70f -> {
                val f = (prox - 0.40f) / 0.30f
                val roll = Random.nextFloat()
                when {
                    roll < 0.20f + f * 0.50f -> "hit"
                    roll < 0.90f -> "graze"
                    else -> "miss"
                }
            }
            else -> "hit"
        }

        if (outcome == "miss") {
            msg(t("missed"))
            return
        }

        val newHitStreak = state.hitStreak + 1
        val isHeadshot = (newHitStreak % 4 == 0)
        var baseDmg = if (outcome == "hit") gun.dmg else (gun.dmg * 0.35f).roundToInt()
        baseDmg = (baseDmg * _settings.value.playerDamageMult).roundToInt()
        if (isHeadshot) baseDmg *= 2

        var brokeCover = false
        var currentCoverHp = state.devilCoverHp
        if (currentCoverHp > 0) {
            baseDmg = max(1, (baseDmg * 0.5f).roundToInt())
            currentCoverHp -= 1
            if (currentCoverHp <= 0) {
                currentCoverHp = 0
                brokeCover = true
            }
        }

        val newDevilHp = max(0, state.devilHp - baseDmg)
        _gameState.update {
            it.copy(
                hitStreak = newHitStreak,
                devilCoverHp = currentCoverHp,
                devilHp = newDevilHp,
                playerHits = it.playerHits + 1
            )
        }

        playSound("hit", volume = if (outcome == "hit") 1f else 0.65f)
        playSound("enemyHit", volume = if (outcome == "hit") 1f else 0.65f, posZ = -state.distance)

        var text = "${if (outcome == "hit") t("enemyHit") else t("enemyGrazed")} ${t("hitFor")} $baseDmg. ${t("enemyHealth")} $newDevilHp."
        if (isHeadshot) text += " ${t("headshot")}"
        if (brokeCover) text += " ${t("devilCoverDown")}"
        msg(text)

        if (newDevilHp <= 0) {
            endGame(won = true)
            return
        }

        val hitsTaken = state.devilHitsTaken + 1
        if (hitsTaken >= state.devilReactAt) {
            _gameState.update {
                it.copy(
                    devilHitsTaken = 0,
                    devilReactAt = 4 + if (Random.nextFloat() < 0.6f) 0 else 1
                )
            }
            devilReact()
            if (_gameState.value.devilHp <= 0) {
                endGame(won = true)
                return
            }
        } else if (_gameState.value.devilHp.toFloat() / state.devilMaxHp.toFloat() < 0.30f &&
            state.devilState != "retreat" && state.devilState != "flank") {
            devilRetreat()
        }

        // Ammo drop chance (15%)
        if (Random.nextFloat() < 0.15f) {
            val updatedAmmo = _gameState.value.ammo.toMutableList()
            val mag = DEFAULT_GUNS[_gameState.value.gunIndex].mag
            updatedAmmo[_gameState.value.gunIndex] = min(mag, updatedAmmo[_gameState.value.gunIndex] + 2)
            _gameState.update { it.copy(ammo = updatedAmmo) }
            msg(t("ammoPickup"))
        }

        devilOnHit()
    }

    private fun spendCoins(amount: Int): Boolean {
        if (_stats.value.coins < amount) return false
        val newCoins = _stats.value.coins - amount
        val updated = _stats.value.copy(coins = newCoins)
        _stats.value = updated
        prefs.saveStats(updated)
        playSound("spend", volume = 0.9f)
        return true
    }

    fun doReload() {
        if (_currentScreen.value != Screen.BATTLE) return
        val state = _gameState.value
        val mag = DEFAULT_GUNS[state.gunIndex].mag
        if (state.ammo[state.gunIndex] >= mag) {
            playSound("click")
            msg(t("reloaded"))
            return
        }
        if (!spendCoins(30)) {
            playSound("ammo")
            msg(t("notEnough"))
            return
        }
        val newAmmoList = state.ammo.toMutableList().apply { this[state.gunIndex] = mag }
        _gameState.update { it.copy(ammo = newAmmoList) }
        msg("${t("reloaded")} ${t("coinsSpent")}: 30.")
    }

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
        if (!spendCoins(100)) {
            playSound("ammo")
            msg(t("notEnough"))
            return
        }
        val newHp = min(state.playerMaxHp, state.playerHp + 50)
        _gameState.update { it.copy(medkits = it.medkits - 1, playerHp = newHp) }
        playSound("medkit")
        msg("${t("medkitUsed")} $newHp")
    }

    fun toggleCover() {
        if (_currentScreen.value != Screen.BATTLE) return
        val state = _gameState.value
        if (!state.inCover) {
            if (!spendCoins(100)) {
                playSound("ammo")
                msg(t("notEnough"))
                return
            }
        }
        val nowCover = !state.inCover
        _gameState.update { it.copy(inCover = nowCover) }
        playSound("gloowall")
        msg(if (nowCover) t("coverOn") else t("coverOff"))
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
            speak("${t("enemyHealth")} ${state.devilHp}. ${t("distance")} ${state.distance.roundToInt()}.$coverText")
        }
    }

    fun exitBattle() {
        stopTimers()
        audioManager.stopLoop("walk")
        audioManager.stopLoop("airplane")
        ttsManager.stop()
        playSound("click")
        _currentScreen.value = Screen.MENU
        updateAudioForScreen(Screen.MENU)
        speak(t("exited"))
    }

    private fun endGame(won: Boolean) {
        stopTimers()
        audioManager.stopLoop("walk")

        val state = _gameState.value
        val base = if (won) 150 else 30
        var gain = (base + state.playerHits * 10 + max(0, state.playerHp) * 0.5f).roundToInt()
        if (gain < 20) gain = 20

        val oldStats = _stats.value
        val newCoins = oldStats.coins + gain
        val newGames = oldStats.games + 1
        val newWins = if (won) oldStats.wins + 1 else oldStats.wins
        val newKills = if (won) oldStats.kills + 1 else oldStats.kills
        val newLevel = (newWins / 10).coerceAtLeast(0)
        val leveled = newLevel > oldStats.level

        val updatedStats = oldStats.copy(
            coins = newCoins,
            games = newGames,
            wins = newWins,
            kills = newKills,
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
            level = newLevel
        )

        _currentScreen.value = Screen.RESULT
        updateAudioForScreen(Screen.RESULT)
        playSound(if (won) "win" else "lose")

        var speech = if (won) t("victoryMsg") else t("defeatMsg")
        speech += " ${t("earned")} $gain."
        if (leveled) speech += " ${t("newLevel")} $newLevel."
        speak(speech)
    }

    fun playAgain() {
        stopTimers()
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

        when (_currentScreen.value) {
            Screen.READER -> {
                if (direction == "up") continueToJump()
                else if (direction == "down") navigateTo(Screen.MENU)
            }
            Screen.JUMP -> {
                if (direction == "up") doJump()
                else if (direction == "down") navigateTo(Screen.MENU)
            }
            Screen.PARA -> {
                if (direction == "up") doPara()
                else if (direction == "down") navigateTo(Screen.MENU)
            }
            Screen.LAND -> {
                if (direction == "up") doLand()
                else if (direction == "down") navigateTo(Screen.MENU)
            }
            Screen.BATTLE -> {
                if (fingers >= 3) {
                    if (direction == "up") toggleCover()
                    else if (direction == "down") exitBattle()
                } else if (fingers == 2) {
                    when (direction) {
                        "up" -> doReload()
                        "down" -> useMedkit()
                        "right" -> speakStatus(own = true)
                        "left" -> speakStatus(own = false)
                    }
                } else {
                    when (direction) {
                        "up" -> fire()
                        "down" -> doReload()
                        "right" -> switchGun(1)
                        "left" -> switchGun(-1)
                    }
                }
            }
            Screen.RESULT -> {
                if (direction == "up") playAgain()
                else if (direction == "down") navigateTo(Screen.MENU)
            }
            else -> {}
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

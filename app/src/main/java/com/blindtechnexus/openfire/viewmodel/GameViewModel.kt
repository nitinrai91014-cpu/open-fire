package com.blindtechnexus.openfire.viewmodel

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.blindtechnexus.openfire.audio.AudioManager
import com.blindtechnexus.openfire.audio.TtsManager
import com.blindtechnexus.openfire.data.PreferencesManager
import com.blindtechnexus.openfire.data.Strings
import com.blindtechnexus.openfire.model.BattleMenuItem
import com.blindtechnexus.openfire.model.Combat
import com.blindtechnexus.openfire.model.DefenceItem
import com.blindtechnexus.openfire.model.Extension
import com.blindtechnexus.openfire.model.GameAction
import com.blindtechnexus.openfire.model.GameSettings
import com.blindtechnexus.openfire.model.GameState
import com.blindtechnexus.openfire.model.GameStats
import com.blindtechnexus.openfire.model.GestureRouter
import com.blindtechnexus.openfire.model.HitZone
import com.blindtechnexus.openfire.model.Inventory
import com.blindtechnexus.openfire.model.Progression
import com.blindtechnexus.openfire.model.ResultData
import com.blindtechnexus.openfire.model.RobertProfile
import com.blindtechnexus.openfire.model.RobertRules
import com.blindtechnexus.openfire.model.Screen
import com.blindtechnexus.openfire.model.Shop
import com.blindtechnexus.openfire.model.ShopResult
import com.blindtechnexus.openfire.model.Weapon
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

    private val _inventory = MutableStateFlow(prefs.loadInventory())
    val inventory: StateFlow<Inventory> = _inventory.asStateFlow()

    private val _currentScreen = MutableStateFlow(
        if (_settings.value.name.isNotBlank()) Screen.MENU else Screen.WELCOME
    )
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    private val _gameState = MutableStateFlow(GameState())
    val gameState: StateFlow<GameState> = _gameState.asStateFlow()

    private val _resultData = MutableStateFlow(ResultData())
    val resultData: StateFlow<ResultData> = _resultData.asStateFlow()

    /** False during the four second pause after a victory or a defeat. */
    private val _resultReady = MutableStateFlow(true)
    val resultReady: StateFlow<Boolean> = _resultReady.asStateFlow()

    private var stepJob: Job? = null
    private var counterJob: Job? = null
    private var robertFireJob: Job? = null
    private var resultJob: Job? = null
    private var stepCount = 0
    private var foreground = true

    /** Last voice-related settings applied, to avoid resetting TTS while dragging volume. */
    private var syncedVoiceSignature: String? = null

    /** Debounce so one physical tap / swipe can never fire (and spend) more than one bullet. */
    private var lastFireAt = 0L

    /**
     * Timestamp of the last explicit button / gesture action. A tap that
     * immediately follows a button press must not also activate the quick
     * menu, otherwise a Fire or Reload button would trigger two actions.
     */
    private var lastExplicitActionAt = 0L

    private val robertProfile: RobertProfile
        get() = RobertRules.profile(_stats.value.round)

    private companion object {
        /** The Robert holds this distance once it reaches the player. */
        const val ENGAGE_DISTANCE = 2f

        /** Fast retreat: metres gained per flee tick. */
        const val FLEE_STEP = 6f
        const val FLEE_TICK_MS = 160L

        /** Advance tick length. */
        const val ADVANCE_TICK_MS = 550L

        /** Chance that a hit provokes an immediate counter shot. */
        const val COUNTER_FIRE_CHANCE = 0.25f

        /** Result screen lock-out before another game may start. */
        const val RESULT_LOCK_MS = 4000L

        /** Health percentage considered the "last stage" for automatic defences. */
        const val LAST_STAGE_FRACTION = 0.20f
    }

    init {
        syncSettingsToManagers(_settings.value)
        updateAudioForScreen(_currentScreen.value)
    }

    // ---------------------------------------------------------------------
    // Settings, volume and TTS lifecycle
    // ---------------------------------------------------------------------

    private fun syncSettingsToManagers(s: GameSettings) {
        audioManager.sfxVolume = s.sfxVolume
        audioManager.musicVolume = s.musicVolume
        audioManager.soundTheme = s.soundTheme
        audioManager.hapticsEnabled = s.hapticsEnabled
        // Footsteps always follow the voice level at 75%.
        audioManager.walkVolume = s.ttsVolume * 0.75f

        ttsManager.speechRate = s.ttsRate
        ttsManager.speechVolume = s.ttsVolume
        ttsManager.currentLanguage = s.lang
        ttsManager.currentVoiceName = s.voiceName
        if (s.ttsEnginePackage != ttsManager.currentEnginePackage) {
            ttsManager.initTts(s.ttsEnginePackage.ifEmpty { null })
            syncedVoiceSignature = null
        } else {
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

    private fun setLog(text: String) {
        _gameState.update { it.copy(log = text) }
    }

    fun speak(text: String, interrupt: Boolean = true) {
        ttsManager.speak(text, interrupt)
    }

    fun playSound(
        key: String,
        volume: Float = 1.0f,
        posX: Float = 0f,
        posY: Float = 0f,
        posZ: Float = 0f
    ) {
        audioManager.playSound(key, volume = volume, posX = posX, posY = posY, posZ = posZ)
    }

    private fun toast(text: String) {
        try {
            Toast.makeText(getApplication(), text, Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
        }
    }

    private fun notEnoughCoins() {
        playSound("ammo")
        val text = t("notEnoughCoins")
        toast(text)
        msg(text)
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
        updateSettings(_settings.value.copy(lang = lang))
        ttsManager.refreshVoices()
    }

    // ---------------------------------------------------------------------
    // Navigation and screen audio
    // ---------------------------------------------------------------------

    fun navigateTo(screen: Screen) {
        stopTimers()
        resultJob?.cancel()
        resultJob = null
        _resultReady.value = true
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
            Screen.SHOP -> speak("${t("shopTitle")}. ${t("shopSubtitle")}. ${coinsSpeech()}")
            Screen.PROFILE -> speak(
                "${t("profileTitle")}. ${t("round")} ${_stats.value.round}. " +
                    "${t("level")} ${_stats.value.level}. " +
                    "${t("needWin")} ${_stats.value.needWinsForNext}. ${coinsSpeech()}. " +
                    "${profileInventorySpeech()}"
            )
            Screen.ABOUT -> speak("${t("aboutTitle")}. ${t("developed")}. ${t("version")}")
            Screen.CHANGELOG -> speak(t("changelogTitle"))
            Screen.MENU -> speak(t("mainMenu"))
            Screen.READER -> speak("${t("readerMsg")} ${t("readerUp")}. ${t("readerDown")}.")
            Screen.BATTLE -> speakBattleMenu()
            else -> {}
        }
    }

    private fun coinsSpeech(): String = "${t("coins")} ${_stats.value.coins}."

    private fun updateAudioForScreen(screen: Screen) {
        when (screen) {
            Screen.WELCOME, Screen.MENU, Screen.SETTINGS, Screen.GUIDE, Screen.PROFILE,
            Screen.SHOP, Screen.ABOUT, Screen.CHANGELOG, Screen.READER -> {
                audioManager.stopScreenAudio()
                audioManager.startMusic()
            }
            Screen.JUMP -> {
                audioManager.stopMusic()
                audioManager.stopScreenAudio()
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
                updateWalkAudio(_gameState.value.distance, _gameState.value.robertFleeing)
            }
            Screen.RESULT -> {
                audioManager.stopMusic()
                audioManager.stopSound("walk")
            }
        }
    }

    fun onAppResumed() {
        foreground = true
        when (_currentScreen.value) {
            Screen.WELCOME, Screen.MENU, Screen.SETTINGS, Screen.GUIDE, Screen.PROFILE,
            Screen.SHOP, Screen.ABOUT, Screen.CHANGELOG, Screen.READER -> audioManager.startMusic()
            Screen.JUMP -> audioManager.playLoop("airplane", volume = 0.45f)
            Screen.BATTLE -> {
                val state = _gameState.value
                updateWalkAudio(state.distance, state.robertFleeing)
                if (state.playerHp > 0 && state.robertHp > 0) {
                    startRobertFireLoop(1200L)
                    val needsMovement = state.robertState == "retreat" ||
                        state.robertState == "flank" ||
                        (state.robertState == "advance" && state.distance > ENGAGE_DISTANCE)
                    if (needsMovement) {
                        planRobertStep(400L)
                    }
                }
            }
            else -> {}
        }
    }

    fun onAppPaused() {
        foreground = false
        stopTimers()
        audioManager.stopAllSounds()
        ttsManager.stop()
    }

    // ---------------------------------------------------------------------
    // Shop
    // ---------------------------------------------------------------------

    private fun applyPurchase(result: ShopResult, nameKey: String, successKey: String) {
        if (!result.success) {
            when (result.reasonKey) {
                "notEnoughCoins" -> notEnoughCoins()
                null -> {}
                else -> msg(t(result.reasonKey))
            }
            return
        }
        _inventory.value = result.inventory
        prefs.saveInventory(result.inventory)
        _stats.value = _stats.value.copy(coins = result.coins)
        prefs.saveStats(_stats.value)
        playSound("spend")
        msg("${t(nameKey)}. ${t(successKey)}")
    }

    fun buyWeapon(weaponId: String) {
        val weapon = Weapon.fromId(weaponId) ?: return
        applyPurchase(
            Shop.buyWeapon(_inventory.value, _stats.value.coins, weapon),
            weapon.nameKey,
            "bought"
        )
    }

    fun removeWeapon(weaponId: String) {
        val weapon = Weapon.fromId(weaponId) ?: return
        applyPurchase(
            Shop.removeWeapon(_inventory.value, _stats.value.coins, weapon),
            weapon.nameKey,
            "removed"
        )
    }

    fun buyDefence(itemId: String) {
        val item = DefenceItem.fromId(itemId) ?: return
        applyPurchase(
            Shop.buyDefence(_inventory.value, _stats.value.coins, item),
            item.nameKey,
            "bought"
        )
    }

    /** Discards one owned defence, after the shop confirmed the removal. */
    fun removeDefence(itemId: String) {
        val item = DefenceItem.fromId(itemId) ?: return
        if (_inventory.value.count(item) <= 0) {
            playSound("ammo")
            msg(t("nothingToRemove"))
            return
        }
        val updated = Shop.consume(_inventory.value, item)
        _inventory.value = updated
        prefs.saveInventory(updated)
        playSound("click")
        msg("${t(item.nameKey)}. ${t("removedOne")} ${updated.count(item)}.")
    }

    fun buyExtension(extensionId: String) {
        val extension = Extension.fromId(extensionId) ?: return
        applyPurchase(
            Shop.buyExtension(_inventory.value, _stats.value.coins, extension),
            extension.nameKey,
            "extensionBought"
        )
    }

    fun removeExtension(extensionId: String) {
        val extension = Extension.fromId(extensionId) ?: return
        applyPurchase(
            Shop.removeExtension(_inventory.value, _stats.value.coins, extension),
            extension.nameKey,
            "extensionRemoved"
        )
    }

    fun toggleExtension(extensionId: String) {
        val extension = Extension.fromId(extensionId) ?: return
        val result = Shop.toggleExtension(_inventory.value, _stats.value.coins, extension)
        if (!result.success) {
            msg(t(result.reasonKey ?: "notOwned"))
            return
        }
        _inventory.value = result.inventory
        prefs.saveInventory(result.inventory)
        playSound("click")
        val enabled = result.inventory.extensionEnabled(extension)
        msg(if (enabled) t("extensionEnabledMsg") else t("extensionDisabledMsg"))
    }

    /** Spoken summary of everything the player owns. */
    private fun profileInventorySpeech(): String {
        val inv = _inventory.value
        val weapons = inv.ownedWeapons().joinToString(", ") { t(it.nameKey) }
        val extensions = Extension.entries
            .filter { inv.extensionOwned(it) }
            .joinToString(", ") { extension ->
                val state = if (inv.extensionEnabled(extension)) t("enabledState") else t("disabledState")
                "${t(extension.nameKey)} ${state}"
            }
            .ifEmpty { t("noneOwned") }
        return "${t("profileWeapons")}: $weapons. ${t("profileDefences")}: " +
            "${inv.medikits} ${t("medkitsPlural")}, ${inv.glueWalls} ${t("glueWallsPlural")}, " +
            "${inv.covers} ${t("coversPlural")}. ${t("profileExtensions")}: $extensions."
    }

    fun speakInventory() {
        if (_currentScreen.value != Screen.BATTLE && _currentScreen.value != Screen.SHOP) return
        speak(profileInventorySpeech())
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
        speak("${t("aim")} ${t("menuHint")}")
        planRobertStep(1300L)
        startRobertFireLoop(2600L)
    }

    private fun resetBattleState() {
        stopTimers()
        resultJob?.cancel()
        resultJob = null
        _resultReady.value = true
        audioManager.stopScreenAudio()
        stepCount = 0
        lastFireAt = 0L

        val round = _stats.value.round.coerceIn(1, Progression.ROUNDS_PER_MATCH)
        val profile = RobertRules.profile(round)
        val hp = RobertRules.hpForRound(round)
        val owned = _inventory.value.ownedWeapons().ifEmpty { Weapon.defaults() }
        val starting = _inventory.value.startingWeapon()
        val startIndex = owned.indexOfFirst { it.id == starting.id }.coerceAtLeast(0)

        _gameState.value = GameState(
            playerHp = hp,
            playerMaxHp = hp,
            robertHp = hp,
            robertMaxHp = hp,
            round = round,
            weapons = owned.map { it.id },
            weaponIndex = startIndex,
            ammo = owned.map { it.magazine },
            damaged = owned.map { false },
            distance = Combat.MAX_RANGE,
            coverActive = false,
            coverHits = 0,
            glueWallActive = false,
            glueWallProgress = 0,
            robertCoverActive = false,
            robertCoverHits = 0,
            robertGlueWallActive = false,
            robertGlueWallProgress = 0,
            robertWeapons = profile.weapons.map { it.id },
            robertWeaponIndex = 0,
            robertAmmo = profile.weapons.map { it.magazine },
            robertDamaged = profile.weapons.map { false },
            robertMedkits = profile.medikits,
            robertCovers = profile.covers,
            robertGlueWalls = profile.glueWalls,
            robertDefenceArmed = true,
            robertConsecutiveHits = 0,
            robertState = "advance",
            robertHits = 0,
            robertShots = 0,
            robertDamage = 0,
            playerShots = 0,
            playerHits = 0,
            consecutiveHeavyHits = 0,
            playerSecondChanceUsed = false,
            menuIndex = BattleMenuItem.PLAY_INDEX,
            menuActive = false,
            pendingPurchaseId = null,
            log = t("ready"),
            matchWon = false
        )
    }

    // ---------------------------------------------------------------------
    // Robert movement, 3D footsteps, continuous fire
    // ---------------------------------------------------------------------

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

    private fun planRobertStep(delayMs: Long) {
        stepJob?.cancel()
        stepJob = viewModelScope.launch {
            delay(delayMs)
            robertStep()
        }
    }

    private fun robertStep() {
        if (!foreground || _currentScreen.value != Screen.BATTLE) return
        val state = _gameState.value
        if (state.robertHp <= 0 || state.playerHp <= 0) return

        when (state.robertState) {
            "retreat" -> {
                val newDist = (state.distance + FLEE_STEP).coerceAtMost(Combat.MAX_RANGE)
                if (newDist >= Combat.MAX_RANGE) {
                    _gameState.update { it.copy(distance = Combat.MAX_RANGE, robertState = "flank") }
                    updateWalkAudio(Combat.MAX_RANGE, fleeing = false)
                    planRobertStep(1300L + Random.nextInt(700))
                } else {
                    _gameState.update { it.copy(distance = newDist) }
                    updateWalkAudio(newDist, fleeing = true)
                    planRobertStep(FLEE_TICK_MS)
                }
            }
            "flank" -> {
                val speed = 4.5f + _stats.value.round * 0.35f
                val newDist = max(ENGAGE_DISTANCE, state.distance - speed)
                if (newDist <= ENGAGE_DISTANCE) {
                    _gameState.update {
                        it.copy(distance = ENGAGE_DISTANCE, robertState = "advance")
                    }
                    playSound("jump", posX = 35f, posZ = -ENGAGE_DISTANCE)
                    updateWalkAudio(ENGAGE_DISTANCE, fleeing = false)
                    msg(t("devilFlank"))
                } else {
                    _gameState.update { it.copy(distance = newDist) }
                    updateWalkAudio(newDist, fleeing = false)
                    planRobertStep(300L)
                }
            }
            else -> {
                if (state.distance > ENGAGE_DISTANCE) {
                    val prox = Combat.proximityOf(state.distance) / 100f
                    val speed = 2.0f + prox * 4.0f + _stats.value.round * 0.25f
                    val newDist = max(ENGAGE_DISTANCE, state.distance - speed)
                    _gameState.update { it.copy(distance = newDist) }
                    updateWalkAudio(newDist, fleeing = false)
                    if (Random.nextFloat() < 0.10f) {
                        playSound("jump", volume = 0.55f, posZ = -newDist)
                    }
                    planRobertStep(ADVANCE_TICK_MS)
                } else {
                    updateWalkAudio(ENGAGE_DISTANCE, fleeing = false)
                }
            }
        }
    }

    private fun startRobertFireLoop(initialDelayMs: Long) {
        robertFireJob?.cancel()
        robertFireJob = viewModelScope.launch {
            delay(initialDelayMs)
            while (true) {
                if (!foreground || _currentScreen.value != Screen.BATTLE) break
                val state = _gameState.value
                if (state.playerHp <= 0 || state.robertHp <= 0) break
                robertAttack()
                delay(robertFireInterval())
            }
        }
    }

    private fun isRobertWeaponDamaged(state: GameState, weapon: Weapon): Boolean {
        val index = state.robertWeapons.indexOf(weapon.id)
        if (index < 0) return false
        return state.robertDamaged.getOrElse(index) { false }
    }

    private fun robertFireInterval(): Long {
        val base = (3000L - _stats.value.round * 220L).coerceIn(1500L, 3000L)
        return base + Random.nextLong(0, 600)
    }

    /**
     * Robert's attack. It picks one of the weapons it is allowed to use right
     * now (switching often to confuse the player), reloads after six shots
     * without paying coins, and obeys the exact same proximity zones.
     */
    private fun robertAttack() {
        if (!foreground || _currentScreen.value != Screen.BATTLE) return
        var state = _gameState.value
        if (state.robertHp <= 0 || state.playerHp <= 0) return

        val allowed = RobertRules.allowedWeapons(
            robertProfile,
            state.glueWallActive,
            state.robertHp
        ).ifEmpty { robertProfile.weapons }
        var pool = allowed.filter { !isRobertWeaponDamaged(state, it) }
        if (pool.isEmpty()) {
            // Every weapon is broken: Robert repairs his arsenal for free.
            _gameState.update { it.copy(robertDamaged = it.robertDamaged.map { false }) }
            state = _gameState.value
            pool = allowed
        }
        // A raised glue wall can only be answered by the sniper rifle, so the
        // Robert switches to it automatically whenever it is allowed.
        val chosen = if (state.glueWallActive && pool.contains(Weapon.SNIPER)) {
            Weapon.SNIPER
        } else {
            pool[Random.nextInt(pool.size)]
        }
        val chosenIndex = state.robertWeapons.indexOf(chosen.id).coerceAtLeast(0)

        if (chosenIndex != state.robertWeaponIndex) {
            _gameState.update { it.copy(robertWeaponIndex = chosenIndex) }
            playSound(chosen.switchSoundKey, volume = 0.8f, posZ = -state.distance)
            state = _gameState.value
        }

        // Six rounds per magazine, then a free reload.
        if (chosen.needsReload && state.robertAmmoNow <= 0) {
            _gameState.update { s ->
                s.copy(
                    robertAmmo = s.robertAmmo.toMutableList().also {
                        it[s.robertWeaponIndex] = chosen.magazine
                    }
                )
            }
            playSound("gunReload", volume = 0.8f, posZ = -state.distance)
            setLog(t("robertReloaded"))
            return
        }

        playSound(chosen.fireSoundKey, volume = 0.9f, posZ = -state.distance)
        _gameState.update { s ->
            val nextAmmo = if (chosen.needsReload) {
                s.robertAmmo.toMutableList().also {
                    it[s.robertWeaponIndex] = (it[s.robertWeaponIndex] - 1).coerceAtLeast(0)
                }
            } else {
                s.robertAmmo
            }
            s.copy(robertShots = s.robertShots + 1, robertAmmo = nextAmmo)
        }
        state = _gameState.value

        val proxPercent = Combat.proximityOf(state.distance)
        val zone = Combat.zoneFor(proxPercent)

        if (zone == HitZone.MISS) {
            if (chosen.melee) playSound(chosen.missSoundKey, volume = 0.55f, posZ = -state.distance)
            _gameState.update { it.copy(consecutiveHeavyHits = 0) }
            setLog("${t("robertMissed")} ${t(chosen.nameKey)}.")
            return
        }

        // The player's glue wall stops everything until three sniper hits break it.
        if (state.glueWallActive) {
            val breaks = Combat.canBreakGlueWall(chosen, proxPercent)
            val progress = if (breaks) state.glueWallProgress + 1 else 0
            _gameState.update { it.copy(glueWallProgress = progress) }
            playSound("glueWallHit", volume = 0.9f)
            if (breaks) playSound("glueCrack", volume = 0.8f)
            if (progress >= Combat.GLUE_WALL_BREAK_HITS) {
                _gameState.update {
                    it.copy(glueWallActive = false, glueWallProgress = 0)
                }
                playSound("glueCrack", volume = 1.0f)
                msg(t("glueWallDown"))
            } else {
                val progressText = if (breaks) {
                    " ${t("glueWallBreaks")} $progress."
                } else {
                    ""
                }
                msg("${t("glueWallBlocked")}$progressText")
            }
            return
        }

        // The player's cover stops sixteen hits and breaks on the seventeenth.
        if (state.coverActive) {
            // The first sixteen hits are absorbed; the seventeenth breaks it.
            val hits = state.coverHits + 1
            if (hits > Combat.COVER_MAX_HITS) {
                _gameState.update { it.copy(coverActive = false, coverHits = 0) }
                playSound("coverCrack", volume = 1.0f)
                msg(t("coverBroken"))
            } else {
                _gameState.update { it.copy(coverHits = hits) }
                playSound("coverHit", volume = 0.8f)
                setLog("${t("coverHitsLeft")} ${Combat.COVER_MAX_HITS - hits}.")
            }
            return
        }

        var dealt = Combat.damageFor(RobertRules.powerFor(chosen), proxPercent)
        // While the quick menu is open every hit costs only 2 health.
        if (state.menuActive) dealt = min(2, dealt)

        val newPlayerHp = max(0, state.playerHp - dealt)

        var streak = Combat.nextHeavyHitStreak(state.consecutiveHeavyHits, proxPercent)
        var brokeWeapon = false
        val damaged = state.damaged.toMutableList()
        val activeIndex = state.weaponIndex.coerceIn(0, max(0, damaged.lastIndex))
        if (damaged.isNotEmpty() && !damaged[activeIndex] && Combat.gunBreaks(streak)) {
            damaged[activeIndex] = true
            streak = 0
            brokeWeapon = true
        }

        _gameState.update {
            it.copy(
                playerHp = newPlayerHp,
                robertHits = it.robertHits + 1,
                robertDamage = it.robertDamage + dealt,
                consecutiveHeavyHits = streak,
                damaged = damaged
            )
        }

        playSound(chosen.hitSoundKey, volume = 1.0f)
        playSound("damage", volume = 1.0f)

        val hitHead = if (zone == HitZone.HEAVY) t("youWereHit") else t("youWereGrazed")
        var text = "$hitHead ${t(chosen.nameKey)}. ${t("hitFor")} $dealt. " +
            "${t("yourHealth")} $newPlayerHp."
        if (brokeWeapon) {
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
                        coverActive = false,
                        glueWallActive = false
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

        text += applyAutomaticDefences()
        msg(text)
    }

    /**
     * Automatic helpers: reload, medikit under 50 health, cover and glue wall
     * in the last stage. They only ever use items the player owns and only
     * while the extension is enabled.
     */
    private fun applyAutomaticDefences(): String {
        val inv = _inventory.value
        var extra = ""
        val state = _gameState.value

        val lastStage = state.playerHp <= (state.playerMaxHp * LAST_STAGE_FRACTION).roundToInt()

        if (inv.extensionEnabled(Extension.AUTO_MEDKIT) &&
            state.playerHp < 50 &&
            inv.medikits > 0
        ) {
            useDefence(DefenceItem.MEDKIT, automatic = true)?.let { extra += " $it" }
        }
        if (inv.extensionEnabled(Extension.AUTO_GLUE_WALL) &&
            lastStage &&
            !_gameState.value.glueWallActive &&
            _inventory.value.glueWalls > 0
        ) {
            useDefence(DefenceItem.GLUE_WALL, automatic = true)?.let { extra += " $it" }
        }
        if (inv.extensionEnabled(Extension.AUTO_COVER) &&
            lastStage &&
            !_gameState.value.coverActive &&
            _inventory.value.covers > 0
        ) {
            useDefence(DefenceItem.COVER, automatic = true)?.let { extra += " $it" }
        }
        return extra
    }

    private fun robertOnHit() {
        counterJob?.cancel()
        if (Random.nextFloat() > COUNTER_FIRE_CHANCE) return
        counterJob = viewModelScope.launch {
            delay(350L)
            if (foreground &&
                _currentScreen.value == Screen.BATTLE &&
                _gameState.value.robertHp > 0 &&
                _gameState.value.playerHp > 0
            ) {
                robertAttack()
            }
        }
    }

    /** Robert's reaction after taking a hit: flee, heal, cover or glue wall. */
    private fun robertAfterHit(): String {
        val state = _gameState.value
        if (state.robertHp <= 0) return ""

        val profile = robertProfile
        val consecutive = state.robertConsecutiveHits

        if (state.robertState == "advance" &&
            RobertRules.shouldFlee(consecutive, profile.fleeAfterHits)
        ) {
            _gameState.update {
                it.copy(robertState = "retreat", robertConsecutiveHits = 0)
            }
            playSound("jump", posZ = -state.distance)
            planRobertStep(120L)
            return " ${t("robertFled")}"
        }

        // Re-arm the defence once Robert is healthy again or after two hits.
        if (state.robertHp > RobertRules.DEFENCE_THRESHOLD_HP || consecutive >= 2) {
            _gameState.update { it.copy(robertDefenceArmed = true) }
        }

        val current = _gameState.value
        if (!RobertRules.shouldUseDefence(current.robertHp, current.robertDefenceArmed)) {
            return ""
        }

        val defence = RobertRules.nextDefence(
            current.robertMedkits,
            current.robertCovers,
            current.robertGlueWalls
        ) ?: return ""

        _gameState.update { it.copy(robertDefenceArmed = false, robertConsecutiveHits = 0) }
        return when (defence) {
            DefenceItem.MEDKIT -> {
                _gameState.update {
                    it.copy(
                        robertHp = it.robertMaxHp,
                        robertMedkits = (it.robertMedkits - 1).coerceAtLeast(0)
                    )
                }
                playSound("medkit", posZ = -current.distance)
                " ${t("robertMedkit")} ${t("enemyHealth")} ${_gameState.value.robertHp}."
            }
            DefenceItem.COVER -> {
                _gameState.update {
                    it.copy(
                        robertCoverActive = true,
                        robertCoverHits = 0,
                        robertCovers = (it.robertCovers - 1).coerceAtLeast(0)
                    )
                }
                playSound("cover", posZ = -current.distance)
                " ${t("robertCoverUp")}"
            }
            DefenceItem.GLUE_WALL -> {
                _gameState.update {
                    it.copy(
                        robertGlueWallActive = true,
                        robertGlueWallProgress = 0,
                        robertGlueWalls = (it.robertGlueWalls - 1).coerceAtLeast(0)
                    )
                }
                playSound("glueSound", posZ = -current.distance)
                " ${t("robertGlueWall")}"
            }
        }
    }

    // ---------------------------------------------------------------------
    // Player firing
    // ---------------------------------------------------------------------

    fun fire() {
        if (_currentScreen.value != Screen.BATTLE) return
        markExplicitAction()

        val now = System.currentTimeMillis()
        if (now - lastFireAt < 220L) return
        lastFireAt = now

        var state = _gameState.value
        if (state.playerHp <= 0 || state.robertHp <= 0) return

        val weapon = state.activeWeapon
        if (state.activeWeaponDamaged) {
            playSound("ammo")
            msg("${t("gunDamaged")} ${t("recovery")}. ${t("coins")}: ${Combat.GUN_RECOVERY_COST}.")
            return
        }

        if (weapon.needsReload && state.activeAmmo <= 0) {
            playSound("ammo")
            val reloaded = autoReloadIfNeeded()
            if (reloaded == null) {
                msg(t("outOfAmmo"))
            } else {
                msg(reloaded)
            }
            return
        }

        var decremented = false
        _gameState.update { s ->
            val idx = s.weaponIndex.coerceIn(0, s.weapons.lastIndex)
            val current = s.ammo.getOrElse(idx) { 0 }
            if (weapon.needsReload && current <= 0) {
                s
            } else {
                decremented = true
                s.copy(
                    ammo = if (weapon.needsReload) {
                        s.ammo.toMutableList().also { it[idx] = current - 1 }
                    } else {
                        s.ammo
                    },
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
        playSound(weapon.fireSoundKey, volume = 1.0f)

        val proxPercent = Combat.proximityOf(state.distance)
        val zone = Combat.zoneFor(proxPercent)

        if (zone == HitZone.MISS) {
            // Firearms already played their shot; only melee weapons have a
            // dedicated miss sound, so the shot is never played twice.
            if (weapon.melee) playSound(weapon.missSoundKey, volume = 0.6f)
            msg(autoReloadIfNeeded()?.let { "$it ${t("missed")}" } ?: t("missed"))
            return
        }

        // Robert's glue wall: only three continuous sniper hits break it.
        if (state.robertGlueWallActive) {
            val breaks = Combat.canBreakGlueWall(weapon, proxPercent)
            val progress = if (breaks) state.robertGlueWallProgress + 1 else 0
            _gameState.update { it.copy(robertGlueWallProgress = progress) }
            playSound("glueWallHit", volume = 0.9f)
            if (breaks) playSound("glueCrack", volume = 0.8f)
            if (progress >= Combat.GLUE_WALL_BREAK_HITS) {
                _gameState.update {
                    it.copy(robertGlueWallActive = false, robertGlueWallProgress = 0)
                }
                playSound("glueCrack")
                msg(t("robertGlueDown"))
            } else if (breaks) {
                msg("${t("robertGlueCrack")} ${t("glueWallBreaks")} $progress.")
            } else {
                msg(t("robertGlueBlocked"))
            }
            robertOnHit()
            return
        }

        // Robert's cover: sixteen hits, broken on the seventeenth.
        if (state.robertCoverActive) {
            val hits = state.robertCoverHits + 1
            if (hits > Combat.COVER_MAX_HITS) {
                _gameState.update { it.copy(robertCoverActive = false, robertCoverHits = 0) }
                playSound("coverCrack", volume = 1.0f)
                msg(t("robertCoverDown"))
            } else {
                _gameState.update { it.copy(robertCoverHits = hits) }
                playSound("coverHit", volume = 0.8f)
                msg("${t("coverHitsLeft")} ${Combat.COVER_MAX_HITS - hits}.")
            }
            robertOnHit()
            return
        }

        val damage = Combat.damageFor(weapon.power, proxPercent)
        val newRobertHp = max(0, state.robertHp - damage)
        _gameState.update {
            it.copy(
                robertHp = newRobertHp,
                playerHits = it.playerHits + 1,
                robertConsecutiveHits = it.robertConsecutiveHits + 1
            )
        }

        // Three consecutive point-blank hits also damage Robert's weapon. He
        // switches to another one automatically on his next attack.
        var robertWeaponBroke = false
        val heavyStreak = if (zone == HitZone.HEAVY) state.playerHeavyHits + 1 else 0
        if (heavyStreak >= Combat.HITS_TO_DAMAGE_GUN) {
            val robertIndex = state.robertWeaponIndex
            val damagedList = state.robertDamaged.toMutableList()
            if (robertIndex in damagedList.indices && !damagedList[robertIndex]) {
                damagedList[robertIndex] = true
                robertWeaponBroke = true
            }
            _gameState.update { it.copy(playerHeavyHits = 0, robertDamaged = damagedList) }
        } else {
            _gameState.update { it.copy(playerHeavyHits = heavyStreak) }
        }

        playSound(weapon.hitSoundKey, volume = if (zone == HitZone.HEAVY) 1f else 0.7f)
        playSound("enemyHit", volume = if (zone == HitZone.HEAVY) 1f else 0.7f, posZ = -state.distance)

        var text = "${if (zone == HitZone.HEAVY) t("enemyHit") else t("enemyGrazed")} " +
            "${t("hitFor")} $damage. ${t("enemyHealth")} $newRobertHp."
        if (robertWeaponBroke) {
            playSound("gunReload", volume = 0.8f)
            text += " ${t("robertWeaponDamaged")}"
        }

        if (newRobertHp <= 0) {
            playSound(weapon.hitSoundKey, volume = 1f)
            msg(text)
            endGame(won = true)
            return
        }

        text += robertAfterHit()
        text += autoReloadIfNeeded()?.let { " $it" } ?: ""

        // Ammo pickup chance (15%): merged into the hit report.
        if (Random.nextFloat() < 0.15f && weapon.needsReload) {
            var pickedUp = false
            _gameState.update { s ->
                val idx = s.weaponIndex
                val mag = weapon.magazine
                val current = s.ammo.getOrElse(idx) { 0 }
                if (current >= mag) {
                    s
                } else {
                    pickedUp = true
                    s.copy(ammo = s.ammo.toMutableList().also { it[idx] = min(mag, current + 2) })
                }
            }
            if (pickedUp) text += " ${t("ammoPickup")}"
        }

        msg(text)
        robertOnHit()
    }

    /** Reloads the empty magazine automatically when the extension is owned. */
    private fun autoReloadIfNeeded(): String? {
        val state = _gameState.value
        val weapon = state.activeWeapon
        if (!weapon.needsReload || state.activeAmmo > 0) return null
        if (!_inventory.value.extensionEnabled(Extension.AUTO_RELOAD)) return null
        _gameState.update { s ->
            val idx = s.weaponIndex.coerceIn(0, s.ammo.lastIndex)
            s.copy(ammo = s.ammo.toMutableList().also { it[idx] = weapon.magazine })
        }
        playSound("gunReload")
        return t("autoReloaded")
    }

    fun doReload() {
        if (_currentScreen.value != Screen.BATTLE) return
        markExplicitAction()
        val state = _gameState.value
        val weapon = state.activeWeapon
        if (!weapon.needsReload) {
            // Melee weapons are always ready: they have no magazine at all.
            playSound("click")
            msg(t("meleeNoReload"))
            return
        }
        if (state.activeAmmo >= weapon.magazine) {
            playSound("ammo")
            msg(t("magFull"))
            return
        }
        _gameState.update { s ->
            val idx = s.weaponIndex.coerceIn(0, s.ammo.lastIndex)
            s.copy(ammo = s.ammo.toMutableList().also { it[idx] = weapon.magazine })
        }
        playSound("gunReload")
        msg(t("reloaded"))
    }

    /**
     * Right and left swipes walk through the weapons the player actually owns.
     * The chosen weapon is remembered for the next game.
     */
    fun switchWeapon(direction: Int) {
        if (_currentScreen.value != Screen.BATTLE) return
        markExplicitAction()
        val state = _gameState.value
        if (state.weapons.isEmpty()) return
        val next = ((state.weaponIndex + direction) % state.weapons.size + state.weapons.size) %
            state.weapons.size
        _gameState.update { it.copy(weaponIndex = next) }
        val weapon = _gameState.value.activeWeapon

        playSound(weapon.switchSoundKey)
        rememberWeapon(weapon)

        val damagedNote = if (_gameState.value.activeWeaponDamaged) {
            " ${t("damaged")}. ${t("recovery")}: ${Combat.GUN_RECOVERY_COST}."
        } else {
            ""
        }
        val ammoNote = if (weapon.needsReload) {
            " ${t("ammo")} ${_gameState.value.activeAmmo}."
        } else {
            ""
        }
        msg("${t("switchedTo")} ${t(weapon.nameKey)}.$ammoNote$damagedNote")
    }

    private fun rememberWeapon(weapon: Weapon) {
        if (_inventory.value.lastWeaponId == weapon.id) return
        val updated = _inventory.value.copy(lastWeaponId = weapon.id)
        _inventory.value = updated
        prefs.saveInventory(updated)
    }

    fun recoverGun() {
        if (_currentScreen.value != Screen.BATTLE) return
        markExplicitAction()
        val state = _gameState.value
        if (!state.anyWeaponDamaged) {
            playSound("click")
            msg(t("gunNotDamaged"))
            return
        }
        if (_stats.value.coins < Combat.GUN_RECOVERY_COST) {
            notEnoughCoins()
            return
        }
        _stats.value = _stats.value.copy(coins = _stats.value.coins - Combat.GUN_RECOVERY_COST)
        prefs.saveStats(_stats.value)
        _gameState.update { it.copy(damaged = it.damaged.map { false }) }
        playSound("spend")
        playSound("gunReload")
        msg("${t("gunRecovered")} ${t("coinsSpent")}: ${Combat.GUN_RECOVERY_COST}.")
    }

    // ---------------------------------------------------------------------
    // Quick menu and defences
    // ---------------------------------------------------------------------

    /** Cycles the quick menu: medikit, cover, glue wall, exit, play. */
    fun cycleMenu(direction: Int) {
        if (_currentScreen.value != Screen.BATTLE) return
        markExplicitAction()
        val state = _gameState.value
        val nextIndex = state.menuIndex + direction
        _gameState.update {
            it.copy(menuIndex = nextIndex, menuActive = true, pendingPurchaseId = null)
        }
        playSound("click")
        speakBattleMenu()
    }

    private fun speakBattleMenu() {
        val item = _gameState.value.menuItem
        when (item) {
            BattleMenuItem.MEDKIT, BattleMenuItem.COVER, BattleMenuItem.GLUE_WALL -> {
                val defence = item.defence ?: return
                val count = _inventory.value.count(defence)
                speak("${t("menuNow")}: ${t(defence.nameKey)}. $count.")
            }
            BattleMenuItem.EXIT -> speak("${t("menuNow")}: ${t("exitGame")}.")
            BattleMenuItem.PLAY -> speak("${t("menuNow")}: ${t("playState")}.")
        }
    }

    /**
     * A single tap activates the highlighted menu item. A defence that is not
     * owned asks for a confirming second tap and is then bought and used.
     */
    fun activateMenuItem() {
        if (_currentScreen.value != Screen.BATTLE) return
        markExplicitAction()
        val state = _gameState.value
        val item = state.menuItem
        when (item) {
            BattleMenuItem.MEDKIT, BattleMenuItem.COVER, BattleMenuItem.GLUE_WALL -> {
                val defence = item.defence ?: return
                useDefenceFromMenu(defence)
            }
            BattleMenuItem.EXIT -> exitBattle()
            BattleMenuItem.PLAY -> {
                _gameState.update { it.copy(menuActive = false, pendingPurchaseId = null) }
                playSound("click")
                speak(t("alreadyPlaying"))
            }
        }
    }

    private fun useDefenceFromMenu(defence: DefenceItem) {
        val inventory = _inventory.value
        if (inventory.count(defence) > 0) {
            _gameState.update { it.copy(pendingPurchaseId = null) }
            val spoken = useDefence(defence)
            if (spoken != null) msg(spoken)
            backToPlayState()
            return
        }

        val pending = _gameState.value.pendingPurchaseId
        if (pending == defence.id) {
            // Second tap: buy one at the shop price, then use it immediately.
            val purchase = Shop.buyDefence(_inventory.value, _stats.value.coins, defence)
            if (!purchase.success) {
                _gameState.update { it.copy(pendingPurchaseId = null) }
                notEnoughCoins()
                return
            }
            _inventory.value = purchase.inventory
            prefs.saveInventory(purchase.inventory)
            _stats.value = _stats.value.copy(coins = purchase.coins)
            prefs.saveStats(_stats.value)
            playSound("spend")
            _gameState.update { it.copy(pendingPurchaseId = null) }
            val spoken = useDefence(defence)
            val text = "${t(defence.nameKey)} ${t("itemAutoActivated")}" +
                (spoken?.let { " $it" } ?: "")
            msg(text)
            backToPlayState()
        } else {
            _gameState.update { it.copy(pendingPurchaseId = defence.id, menuActive = true) }
            playSound("ammo")
            val text = "${t("itemMissingPrompt")} ${t(defence.nameKey)}. ${t("itemMissingTail")}"
            msg(text)
        }
    }

    private fun backToPlayState() {
        _gameState.update {
            it.copy(menuIndex = BattleMenuItem.PLAY_INDEX, menuActive = false)
        }
    }

    /**
     * Consumes exactly one defence and applies its effect. Returns the spoken
     * description, or null when the item could not be used.
     */
    private fun useDefence(item: DefenceItem, automatic: Boolean = false): String? {
        val state = _gameState.value
        val inventory = _inventory.value
        if (inventory.count(item) <= 0) return null

        when (item) {
            DefenceItem.MEDKIT -> {
                if (state.playerHp >= state.playerMaxHp) {
                    playSound("click")
                    return "${t("yourHealth")} ${state.playerHp}."
                }
                _gameState.update { it.copy(playerHp = it.playerMaxHp) }
                consumeDefence(item)
                playSound("medkit")
                return (if (automatic) t("autoMedkitUsed") else t("medkitUsed")) +
                    " ${_gameState.value.playerHp}."
            }
            DefenceItem.COVER -> {
                if (state.coverActive) return null
                _gameState.update { it.copy(coverActive = true, coverHits = 0) }
                consumeDefence(item)
                playSound("cover")
                return (if (automatic) t("autoCoverUsed") else t("coverDeployed")) +
                    " ${Combat.COVER_MAX_HITS}."
            }
            DefenceItem.GLUE_WALL -> {
                if (state.glueWallActive) return null
                _gameState.update { it.copy(glueWallActive = true, glueWallProgress = 0) }
                consumeDefence(item)
                playSound("glueSound")
                return if (automatic) t("autoGlueWallUsed") else t("glooWallDeployed")
            }
        }
    }

    private fun consumeDefence(item: DefenceItem) {
        val updated = Shop.consume(_inventory.value, item)
        _inventory.value = updated
        prefs.saveInventory(updated)
    }

    fun speakStatus(own: Boolean) {
        if (_currentScreen.value != Screen.BATTLE) return
        markExplicitAction()
        val state = _gameState.value
        if (own) {
            val weapon = state.activeWeapon
            val weaponState = if (state.activeWeaponDamaged) {
                "${t("damaged")}. ${t("recovery")}: ${Combat.GUN_RECOVERY_COST}"
            } else {
                t(weapon.nameKey)
            }
            val weapons = state.weapons
                .mapNotNull { Weapon.fromId(it) }
                .joinToString(", ") { "${t(it.nameKey)} ${state.ammo.getOrElse(state.weapons.indexOf(it.id)) { 0 }}" }
            val inv = _inventory.value
            val extensions = Extension.entries
                .filter { inv.extensionOwned(it) }
                .joinToString(", ") {
                    "${t(it.nameKey)} ${if (inv.extensionEnabled(it)) t("enabledState") else t("disabledState")}"
                }
                .ifEmpty { t("noneOwned") }
            val chance = if (state.playerSecondChanceUsed) " ${t("secondChanceUsed")}." else ""
            speak(
                "${t("yourHealth")} ${state.playerHp}. ${t("ammo")} ${state.activeAmmo}. " +
                    "$weaponState. $weapons. " +
                    "${t("medkits")} ${inv.medikits}. ${t("glooWall")} ${inv.glueWalls}. " +
                    "${t("cover")} ${inv.covers}. " +
                    "${if (state.coverActive) t("coverOn") else t("coverOff")}. " +
                    "${t("profileExtensions")} $extensions. ${t("coins")} ${_stats.value.coins}.$chance"
            )
        } else {
            val coverText = if (state.robertCoverActive) {
                " ${t("cover")} ${Combat.COVER_MAX_HITS - state.robertCoverHits}."
            } else ""
            val glueText = if (state.robertGlueWallActive) {
                " ${t("glooWall")} ${state.robertGlueWallProgress}."
            } else ""
            val weaponText = state.robertWeapon?.let { t(it.nameKey) } ?: ""
            speak(
                "${t("enemyHealth")} ${state.robertHp}. ${t("distance")} " +
                    "${state.distance.roundToInt()} ${t("meters")}. $weaponText." +
                    "$coverText$glueText"
            )
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

    // ---------------------------------------------------------------------
    // End game and progression
    // ---------------------------------------------------------------------

    /**
     * Ends a battle: coins are awarded, 10 wins promote a level, 10 levels
     * complete a round, the fifth round wins the match, and the result screen
     * is locked for four seconds.
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
        var matchWon = false
        if (won) {
            val promotion = Progression.applyWin(updatedStats)
            updatedStats = promotion.stats
            leveled = promotion.leveledUp
            roundCompleted = promotion.roundCompleted
            matchWon = promotion.matchWon
        }

        _stats.value = updatedStats
        prefs.saveStats(updatedStats)

        _resultData.value = ResultData(
            won = won,
            coins = gain,
            hits = state.playerHits,
            shots = state.playerShots,
            robertShots = state.robertShots,
            hp = state.playerHp,
            levelUp = leveled,
            roundComplete = roundCompleted || matchWon,
            matchWon = matchWon,
            round = updatedStats.round,
            level = updatedStats.level
        )

        _gameState.update { it.copy(matchWon = matchWon) }
        _currentScreen.value = Screen.RESULT
        updateAudioForScreen(Screen.RESULT)
        playSound(if (won) "win" else "lose")
        startResultLock()

        var speech = if (won) t("victoryMsg") else t("defeatMsg")
        speech += " ${t("earned")} $gain."
        if (matchWon) {
            speech += " ${t("matchWonMsg")}"
        } else if (roundCompleted) {
            speech += " ${t("roundCompleted")} ${t("round")} ${updatedStats.round}!"
        } else if (leveled) {
            speech += " ${t("newLevel")} ${updatedStats.level}."
        }
        speech += " ${t("resultWait")}"
        speak(speech)
    }

    private fun startResultLock() {
        resultJob?.cancel()
        _resultReady.value = false
        resultJob = viewModelScope.launch {
            delay(RESULT_LOCK_MS)
            _resultReady.value = true
        }
    }

    /** Play Again: only possible after the four second result lock. */
    fun playAgain() {
        if (!_resultReady.value) {
            playSound("ammo")
            speak(t("waitPlease"))
            return
        }
        resultJob?.cancel()
        resultJob = null
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

    fun clearStats() {
        stopTimers()
        audioManager.stopSound("walk")
        ttsManager.stop()

        val cleared = GameStats.cleared()
        prefs.clearStats(cleared)
        _stats.value = cleared
        _inventory.value = Inventory()
        _resultData.value = ResultData()
        resetBattleState()
        updateAudioForScreen(_currentScreen.value)

        playSound("spend", volume = 0.9f)
        msg(t("statsCleared"))
    }

    // ---------------------------------------------------------------------
    // Gestures
    // ---------------------------------------------------------------------

    fun handleGesture(direction: String, fingers: Int) {
        when (GestureRouter.route(_currentScreen.value, direction, fingers)) {
            GameAction.CONTINUE -> continueToJump()
            GameAction.BACK_TO_MENU -> navigateTo(Screen.MENU)
            GameAction.JUMP -> doJump()
            GameAction.OPEN_PARA -> doPara()
            GameAction.LAND -> doLand()
            GameAction.FIRE -> fire()
            GameAction.RELOAD -> doReload()
            GameAction.NEXT_WEAPON -> switchWeapon(1)
            GameAction.PREV_WEAPON -> switchWeapon(-1)
            GameAction.MENU_NEXT -> cycleMenu(1)
            GameAction.MENU_PREV -> cycleMenu(-1)
            GameAction.ACTIVATE_MENU -> activateMenuItem()
            GameAction.STATUS_OWN -> speakStatus(own = true)
            GameAction.STATUS_ENEMY -> speakStatus(own = false)
            GameAction.EXIT_BATTLE -> exitBattle()
            GameAction.PLAY_AGAIN -> playAgain()
            GameAction.NONE -> {}
        }
    }

    fun handleTap() {
        // Buttons and gestures set this marker; a tap immediately afterwards
        // belongs to that action and must not activate the menu as well.
        if (System.currentTimeMillis() - lastExplicitActionAt < 350L) return
        if (GestureRouter.tap(_currentScreen.value) == GameAction.ACTIVATE_MENU) {
            activateMenuItem()
        }
    }

    private fun markExplicitAction() {
        lastExplicitActionAt = System.currentTimeMillis()
    }

    private fun stopTimers() {
        stepJob?.cancel()
        stepJob = null
        counterJob?.cancel()
        counterJob = null
        robertFireJob?.cancel()
        robertFireJob = null
    }

    override fun onCleared() {
        super.onCleared()
        stopTimers()
        resultJob?.cancel()
        audioManager.release()
        ttsManager.shutdown()
    }
}

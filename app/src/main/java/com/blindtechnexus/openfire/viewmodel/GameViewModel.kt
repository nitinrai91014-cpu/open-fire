package com.blindtechnexus.openfire.viewmodel

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.blindtechnexus.openfire.audio.AudioManager
import com.blindtechnexus.openfire.audio.TtsManager
import com.blindtechnexus.openfire.data.PreferencesManager
import com.blindtechnexus.openfire.data.Strings
import com.blindtechnexus.openfire.model.Arena
import com.blindtechnexus.openfire.model.BattleMenuItem
import com.blindtechnexus.openfire.model.BombKind
import com.blindtechnexus.openfire.model.BombRules
import com.blindtechnexus.openfire.model.Combat
import com.blindtechnexus.openfire.model.DamageTable
import com.blindtechnexus.openfire.model.DefenceItem
import com.blindtechnexus.openfire.model.Extension
import com.blindtechnexus.openfire.model.FireRange
import com.blindtechnexus.openfire.model.GameAction
import com.blindtechnexus.openfire.model.GameSettings
import com.blindtechnexus.openfire.model.GameState
import com.blindtechnexus.openfire.model.GameStats
import com.blindtechnexus.openfire.model.GestureRouter
import com.blindtechnexus.openfire.model.HitZone
import com.blindtechnexus.openfire.model.Inventory
import com.blindtechnexus.openfire.model.MissileRules
import com.blindtechnexus.openfire.model.MoveDirection
import com.blindtechnexus.openfire.model.Progression
import com.blindtechnexus.openfire.model.ResultData
import com.blindtechnexus.openfire.model.RewardBundle
import com.blindtechnexus.openfire.model.Rewards
import com.blindtechnexus.openfire.model.RobertProfile
import com.blindtechnexus.openfire.model.RobertRules
import com.blindtechnexus.openfire.model.RunPressure
import com.blindtechnexus.openfire.model.RunSpeed
import com.blindtechnexus.openfire.model.Screen
import com.blindtechnexus.openfire.model.Shop
import com.blindtechnexus.openfire.model.ShopResult
import com.blindtechnexus.openfire.model.Weapon
import com.blindtechnexus.openfire.model.movePlayer
import com.blindtechnexus.openfire.model.movePlayerLateral
import com.blindtechnexus.openfire.model.moveRobert
import com.blindtechnexus.openfire.model.placeRobertAt
import com.blindtechnexus.openfire.model.SoundDurations
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs
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
    private var playerMoveJob: Job? = null
    private var missileJob: Job? = null
    private var bombJob: Job? = null
    private var autoFireJob: Job? = null
    private var endingJob: Job? = null
    private var foreground = true

    /** Latest touch pressure of the running hold (0 = unknown / released). */
    @Volatile
    private var movePressure: Float = 0f

    /** The running direction of the current hold (null while nothing is held). */
    @Volatile
    private var moveDirection: MoveDirection? = null

    /** Wall-clock moment the missile in the air is due to impact. */
    private var missileImpactAtMs: Long = 0L

    /** Wall-clock moment the bomb in the air is due to explode. */
    private var bombBlastAtMs: Long = 0L

    /** Last time the boundary wall was announced, so it never spams. */
    private var lastWallAnnounceAtMs: Long = 0L

    /** Wall-clock moment the quick-menu hit protection expires. */
    private var menuProtectUntilMs: Long = 0L

    /** Start of the player's current fire burst (continuous fire detection). */
    private var playerBurstStartedAtMs: Long = 0L

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
        get() = RobertRules.profile(_stats.value.round, _stats.value.level)

    private companion object {
        /** Fast retreat: metres gained per flee tick, and the tick length. */
        const val FLEE_STEP = RobertRules.FLEE_STEP_M
        const val FLEE_TICK_MS = RobertRules.FLEE_TICK_MS

        /**
         * Player movement at the normal speed level: [RunPressure.BASE_STEP_METRES]
         * metres per [PLAYER_MOVE_TICK_MS]. The live step is scaled by the
         * three-finger speed level and, in the normal level, by the touch
         * pressure: a stronger press runs faster, a lighter press runs slower.
         */
        const val PLAYER_MOVE_STEP = RunPressure.BASE_STEP_METRES
        const val PLAYER_MOVE_TICK_MS = 100L

        /** Fastest and slowest movement tick, so a sprint still looks smooth. */
        const val MIN_MOVE_TICK_MS = 30L
        const val MAX_MOVE_TICK_MS = 220L

        /** The wall announcement is repeated at most once per this window. */
        const val WALL_ANNOUNCE_COOLDOWN_MS = 1_400L

        /** Bullets per second of a held automatic weapon come from the weapon. */
        const val AUTO_FIRE_FIRST_SHOT_DELAY_MS = 0L

        /** Advance tick length. */
        const val ADVANCE_TICK_MS = 550L

        /** Chance that a hit provokes an immediate counter shot. */
        const val COUNTER_FIRE_CHANCE = 0.25f

        /** Result screen lock-out before another game may start. */
        const val RESULT_LOCK_MS = 4000L

        /** Health percentage considered the "last stage" for automatic defences. */
        const val LAST_STAGE_FRACTION = 0.20f

        /** Robert stops walking once he is this close to his band distance. */
        const val BAND_TOLERANCE_M = 1.5f

        /**
         * Pressure mode. While the weapon in the player's hands is damaged the
         * Robert closes in faster and fires almost continuously from the
         * 90%-100% band until a working weapon is used or Recovery is paid.
         */
        const val PRESSURE_SPEED_FACTOR = 1.6f
        const val PRESSURE_ADVANCE_TICK_MS = 320L

        /** Airplane menu: the cabin sound and the engine sound are mixed. */
        const val AIRPLANE_VOLUME = 0.45f
        const val AIRPLANE_ENGINE_VOLUME = 0.32f

        /** Flight and blast volumes of the launchers. */
        const val MISSILE_VOLUME = MissileRules.SOUND_VOLUME
        const val BOMB_VOLUME = 0.95f
        const val WALL_VOLUME = 1.0f

        /** Progression stings, kept a little under the win / lose sounds. */
        const val LEVEL_COMPLETE_VOLUME = 0.9f
        const val ROUND_COMPLETE_VOLUME = 0.9f

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
        // Footsteps play at exactly the voice level so movement is always
        // as clear as the speech announcements.
        audioManager.walkVolume = s.ttsVolume

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

    fun setKeepScreenOn(enabled: Boolean) {
        updateSettings(_settings.value.copy(keepScreenOn = enabled))
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
            Screen.ABOUT -> speak(
                "${t("aboutTitle")}. ${t("developed")}. " +
                    "${t("specialSupport")}: ${t("specialSupportName")}. " +
                    "${t("builtInNepal")} ${t("version")}"
            )
            Screen.CHANGELOG -> speak(t("changelogTitle"))
            Screen.MENU -> speak(t("mainMenu"))
            Screen.READER -> speak("${t("readerMsg")} ${t("readerUp")}. ${t("readerDown")}.")
            // The battle screen starts silently: no instructive message is
            // spoken when the game screen opens.
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
                startAirplaneAudio()
            }
            Screen.PARA -> {
                audioManager.stopMusic()
                stopAirplaneAudio()
                audioManager.playSound("parachute")
            }
            Screen.LAND -> {
                audioManager.stopMusic()
                audioManager.stopSound("parachute")
            }
            Screen.BATTLE -> {
                audioManager.stopMusic()
                audioManager.stopScreenAudio()
                // Footsteps start only when a fighter is actually running.
                audioManager.stopFootsteps()
            }
            Screen.RESULT -> {
                audioManager.stopMusic()
                audioManager.stopFootsteps()
            }
        }
    }

    /**
     * The airplane menu mixes two loops: the cabin ambience and the engine.
     * Both are managed MediaPlayer tracks, so they run at the same time.
     */
    private fun startAirplaneAudio() {
        audioManager.playLoop("airplane", volume = AIRPLANE_VOLUME)
        audioManager.playLoop("airplaneEngine", volume = AIRPLANE_ENGINE_VOLUME)
    }

    private fun stopAirplaneAudio() {
        audioManager.stopSound("airplane")
        audioManager.stopSound("airplaneEngine")
    }

    fun onAppResumed() {
        foreground = true
        when (_currentScreen.value) {
            Screen.WELCOME, Screen.MENU, Screen.SETTINGS, Screen.GUIDE, Screen.PROFILE,
            Screen.SHOP, Screen.ABOUT, Screen.CHANGELOG, Screen.READER -> audioManager.startMusic()
            Screen.JUMP -> startAirplaneAudio()
            Screen.BATTLE -> {
                val state = _gameState.value
                audioManager.stopFootsteps()
                if (state.playerHp > 0 && state.robertHp > 0) {
                    startRobertFireLoop(1200L)
                    if (state.missileInFlight) {
                        // A paused missile resumes its flight: restart the
                        // sound and the remaining time to impact.
                        if (MissileRules.soundLoops(state.distance)) {
                            audioManager.playLoop("missile")
                        } else {
                            playSound("missile")
                        }
                        val remaining = missileImpactAtMs - System.currentTimeMillis()
                        scheduleMissileImpact(remaining.coerceIn(800L, MissileRules.MAX_FLIGHT_MS))
                    }
                    val needsMovement = state.robertState == "retreat" ||
                        state.robertState == "flank" ||
                        state.robertState == "missileFlee" ||
                        (
                            state.robertState == "advance" &&
                                abs(state.distance - robertTargetDistance(state)) > BAND_TOLERANCE_M
                            )
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
                "missileLocked", "antiMissileLocked" -> {
                    playSound("ammo")
                    val text = t(result.reasonKey)
                    toast(text)
                    msg(text)
                }
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
            Shop.buyWeapon(
                _inventory.value,
                _stats.value.coins,
                weapon,
                _stats.value.completedRounds
            ),
            weapon.nameKey,
            "bought"
        )
    }

    /** Buys one stackable missile power (repeatable, exactly like a defence). */
    fun buyMissilePower() {
        applyPurchase(
            Shop.buyMissilePower(
                _inventory.value,
                _stats.value.coins,
                _stats.value.completedRounds
            ),
            "extMissilePowers",
            "bought"
        )
    }

    /** Discards one stacked missile power, after the shop confirmed it. */
    fun removeMissilePower() {
        if (_inventory.value.missilePowers <= 0) {
            playSound("ammo")
            msg(t("nothingToRemove"))
            return
        }
        val updated = Shop.consumeMissilePower(_inventory.value)
        _inventory.value = updated
        prefs.saveInventory(updated)
        playSound("click")
        msg("${t("extMissilePowers")}. ${t("removedOne")} ${updated.missilePowers}.")
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
            Shop.buyDefence(
                _inventory.value,
                _stats.value.coins,
                item,
                _stats.value.completedRounds
            ),
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

    /**
     * The Enable / Disable switch under every weapon row of the shop. It works
     * like an extension switch: a disabled weapon is never handed out at the
     * start of a battle and is skipped by the left and right swipe on the game
     * screen until it is enabled again.
     */
    fun toggleWeaponEnabled(weaponId: String) {
        val weapon = Weapon.fromId(weaponId) ?: return
        val result = Shop.toggleWeaponEnabled(_inventory.value, _stats.value.coins, weapon)
        if (!result.success) {
            playSound("ammo")
            msg(t(result.reasonKey ?: "notOwned"))
            return
        }
        _inventory.value = result.inventory
        prefs.saveInventory(result.inventory)
        playSound("click")
        val enabled = result.inventory.weaponEnabled(weapon)
        msg(
            "${t(weapon.nameKey)}: " +
                if (enabled) t("weaponEnabledMsg") else t("weaponDisabledMsg")
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
            "${inv.covers} ${t("coversPlural")}, ${inv.antiMissiles} ${t("antiMissilesPlural")}. " +
            "${t("extMissilePowers")} ${inv.missilePowers}. " +
            "${t("profileExtensions")}: $extensions."
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
        stopAirplaneAudio()
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
        // Nothing is spoken when the game screen starts. The hint stays on
        // screen only, so the battle always begins in silence.
        planRobertStep(1300L)
        startRobertFireLoop(2600L)
    }

    private fun resetBattleState() {
        stopTimers()
        resultJob?.cancel()
        resultJob = null
        endingJob?.cancel()
        endingJob = null
        _resultReady.value = true
        audioManager.stopScreenAudio()
        movePressure = 0f
        moveDirection = null
        missileImpactAtMs = 0L
        bombBlastAtMs = 0L
        lastWallAnnounceAtMs = 0L
        menuProtectUntilMs = 0L
        playerBurstStartedAtMs = 0L
        lastFireAt = 0L

        val round = _stats.value.round.coerceIn(1, Progression.ROUNDS_PER_MATCH)
        val level = _stats.value.level.coerceIn(1, Progression.LEVELS_PER_ROUND)
        val profile = RobertRules.profile(round, level)
        val hp = Progression.hpFor(round, level)
        // Weapons handed out for free in this round are issued before the
        // battle rotation is built (the MP40 and the AWM from round two, the
        // grenade from round three).
        val issued = issueRoundWeapons(round)
        // Disabled weapons are never revealed by a left or right swipe, so the
        // battle rotation only contains owned AND enabled weapons.
        val owned = _inventory.value.battleWeapons(round).ifEmpty { Weapon.defaults() }
        val starting = _inventory.value.startingWeapon()
        val startIndex = owned.indexOfFirst { it.id == starting.id }.coerceAtLeast(0)

        _gameState.value = GameState(
            playerHp = hp,
            playerMaxHp = hp,
            robertHp = hp,
            robertMaxHp = hp,
            round = round,
            level = level,
            weapons = owned.map { it.id },
            weaponIndex = startIndex,
            ammo = owned.map { it.magazine },
            damaged = owned.map { false },
            distance = Combat.MAX_RANGE,
            // Both fighters start on the centre line at opposite edges of the
            // 100 m zone, exactly one full range apart.
            playerX = Arena.PLAYER_START_X,
            playerY = Arena.PLAYER_START_Y,
            robertX = Arena.ROBERT_START_X,
            robertY = Arena.ROBERT_START_Y,
            runSpeed = RunSpeed.NORMAL.id,
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
            robertExchangeShots = 0,
            robertLateral = 0f,
            robertState = "advance",
            robertTurnBackTicks = 0,
            missileInFlight = false,
            missileFromPlayer = false,
            missileInterceptPlanned = false,
            robertMissilePowers = profile.missilePowers,
            robertAntiMissiles = profile.antiMissiles,
            bombInFlight = false,
            bombFromPlayer = false,
            bombKind = "",
            bombX = Arena.PLAYER_START_X,
            bombY = Arena.PLAYER_START_Y,
            bombInterceptPlanned = false,
            robertBombs = profile.bombs,
            robertAntiBombs = profile.antiBombs,
            timerSelectActive = false,
            timerIndex = BombRules.DEFAULT_TIMER_INDEX,
            autoFireActive = false,
            playerBurstShots = 0,
            playerBurstRobertHp = hp,
            playerBurstPlayerHp = hp,
            robertFireRange = RobertRules.preferredRange(Random).id,
            robertDeceptionShots = 0,
            robertHits = 0,
            robertShots = 0,
            robertDamage = 0,
            playerShots = 0,
            playerHits = 0,
            consecutiveHeavyHits = 0,
            playerSecondChanceUsed = false,
            menuIndex = BattleMenuItem.RECOVER_INDEX,
            menuActive = false,
            pendingPurchaseId = null,
            log = t("ready"),
            matchWon = false
        )
        // The battle screen itself starts silently: an issued weapon is only
        // shown on screen and in the status, never spoken over the opening.
        if (issued.isNotEmpty()) {
            setLog(
                "${t("weaponIssued")}: " + issued.joinToString(", ") { weapon -> t(weapon.nameKey) }
            )
        }
    }

    /**
     * Hands out the weapons the round provides for free: the MP40 and the AWM
     * from round two, and the grenade from round three. Weapons that are only
     * unlockable (the atom bomb, the timer bomb and the machine gun) are never
     * issued here, and no weapon is ever issued twice.
     */
    private fun issueRoundWeapons(round: Int): List<Weapon> {
        var updated = _inventory.value
        val issued = mutableListOf<Weapon>()
        for (weapon in Weapon.issuedForRound(round)) {
            if (updated.ownsWeapon(weapon)) continue
            val result = Shop.grantWeapon(updated, weapon)
            if (result.success) {
                updated = result.inventory
                issued.add(weapon)
            }
        }
        if (issued.isEmpty()) return emptyList()
        _inventory.value = updated
        prefs.saveInventory(updated)
        return issued
    }

    // ---------------------------------------------------------------------
    // Robert movement, 3D footsteps, continuous fire
    // ---------------------------------------------------------------------

    /**
     * Updates Robert's positional loop only while he is actually moving.
     * He faces the player head-on, so straight running always uses the
     * stored sideways offset (zero except mid-flank) and never alternates
     * between the ears. The playback rate follows his real speed.
     */
    private fun updateRobertWalkAudio(
        distance: Float,
        fleeing: Boolean = false,
        speedMps: Float = 0f
    ) {
        if (_currentScreen.value != Screen.BATTLE) {
            audioManager.stopSound("enemyWalk")
            return
        }
        audioManager.updateEnemyWalk3dAudio(
            distance = distance,
            lateralOffset = _gameState.value.robertLateral,
            speedMps = speedMps,
            fleeing = fleeing
        )
    }

    /** Stores the live touch pressure of the running hold. */
    fun updateMovePressure(pressure: Float) {
        movePressure = pressure
    }

    /** Robert's fast running speed for this round, in metres per second. */
    private fun enemyFastSpeedMps(): Float =
        RobertRules.enemyFastSpeedMps(_gameState.value.round)

    /**
     * The three-finger running speed gesture: swiping right steps one level
     * faster, swiping left one level slower, cycling slow / normal / fast.
     * The fast level matches Robert's own fast (fleeing) speed for the round
     * exactly, and the running sound gets louder or quieter with the level,
     * just like his footsteps do.
     */
    fun cycleRunSpeed(direction: Int) {
        if (_currentScreen.value != Screen.BATTLE) return
        markExplicitAction()
        val current = _gameState.value.runSpeedLevel
        val next = if (direction >= 0) current.faster() else current.slower()
        playSound("click")
        if (next == current) {
            msg("${t("runSpeed")}: ${t(next.nameKey)}. ${t("runSpeedLimit")}")
            return
        }
        _gameState.update { it.copy(runSpeed = next.id) }
        // Apply the new level to the live footstep loop immediately.
        val held = moveDirection
        if (held != null) {
            val speedMps = next.speedMps(RunPressure.speedFactor(movePressure), enemyFastSpeedMps())
            audioManager.updatePlayerWalkAudio(speedMps, next.volumeGain())
        }
        msg("${t("runSpeed")}: ${t(next.nameKey)}")
    }

    /**
     * Continuous player movement inside the 100 m battle zone, in all four
     * directions:
     *  - forward / backward: towards or away from Robert (top and bottom
     *    portions of the battle screen);
     *  - left / right: along the width of the zone (the two halves of the
     *    center portion).
     *
     * The speed comes from the three-finger level (slow, normal, fast) and, in
     * the normal level, from how strongly the screen is pressed. The running
     * sound follows the same level: faster and louder when running fast,
     * slower and quieter when running slow. Touching one of the four
     * boundaries stops the run, plays the wall sound and announces "Wall";
     * nobody ever gets beyond the 100 m perimeter.
     */
    fun startPlayerMove(direction: MoveDirection) {
        if (_currentScreen.value != Screen.BATTLE) return
        playerMoveJob?.cancel()
        moveDirection = direction
        playerMoveJob = viewModelScope.launch {
            while (true) {
                if (!foreground || _currentScreen.value != Screen.BATTLE) break
                val state = _gameState.value
                if (state.playerHp <= 0 || state.robertHp <= 0) break

                val speed = state.runSpeedLevel
                val factor = speed.factor(RunPressure.speedFactor(movePressure), enemyFastSpeedMps())
                val speedMps = RunPressure.BASE_SPEED_MPS * factor
                val tickMs = (PLAYER_MOVE_TICK_MS * (RunPressure.BASE_SPEED_MPS / speedMps))
                    .toLong()
                    .coerceIn(MIN_MOVE_TICK_MS, MAX_MOVE_TICK_MS)
                val stepMetres = speedMps * (tickMs / 1000f)

                var blocked = false
                _gameState.update { current ->
                    val move = if (direction.lateral) {
                        current.movePlayerLateral(direction == MoveDirection.RIGHT, stepMetres)
                    } else {
                        current.movePlayer(direction == MoveDirection.FORWARD, stepMetres)
                    }
                    blocked = move.blockedByWall
                    if (move.moved) move.state else current
                }

                if (blocked) {
                    // A boundary of the 100 m zone: no movement, wall sound.
                    audioManager.stopSound("playerWalk")
                    announceWall()
                    delay(tickMs)
                    continue
                }

                // Our own footsteps are centred at the speech level, follow the
                // running speed and get louder or quieter with the speed level.
                audioManager.updatePlayerWalkAudio(speedMps, speed.volumeGain())
                if (stepJob == null || stepJob?.isActive != true) {
                    planRobertStep(80L)
                }
                delay(tickMs)
            }
        }
    }

    /**
     * The boundary announcement: the wall impact sound, the word "Wall" spoken
     * aloud, and a hint on screen. It is rate-limited, so holding a finger
     * against a wall never turns into a machine-gun of announcements.
     */
    private fun announceWall() {
        val now = System.currentTimeMillis()
        val repeat = now - lastWallAnnounceAtMs < WALL_ANNOUNCE_COOLDOWN_MS
        lastWallAnnounceAtMs = now
        playSound("wall", volume = WALL_VOLUME)
        if (repeat) return
        setLog(t("wallBlocked"))
        speak(t("wallSpeech"), interrupt = true)
    }

    fun stopPlayerMove() {
        playerMoveJob?.cancel()
        playerMoveJob = null
        moveDirection = null
        movePressure = 0f
        audioManager.stopSound("playerWalk")
    }

    private fun planRobertStep(delayMs: Long) {
        stepJob?.cancel()
        stepJob = viewModelScope.launch {
            delay(delayMs)
            robertStep()
        }
    }

    /**
     * Wakes Robert's movement loop whenever his situational target band shifts
     * (for example after gaining an advantage or when a glue wall or damaged
     * weapon forces him into close range).
     */
    private fun ensureRobertPositioned(delayMs: Long = 180L) {
        val state = _gameState.value
        if (state.playerHp <= 0 || state.robertHp <= 0 || state.missileInFlight) return
        if (stepJob?.isActive == true) return
        if (abs(state.distance - robertTargetDistance(state)) > BAND_TOLERANCE_M) {
            planRobertStep(delayMs)
        }
    }

    /**
     * True while the weapon in the player's hands is damaged. Robert answers
     * that by closing in to the 90%-100% band and firing continuously, until
     * the player switches to a working weapon or pays for Recovery.
     */
    private fun isPlayerWeaponLocked(state: GameState): Boolean = state.activeWeaponDamaged

    /**
     * The band Robert holds right now: a damaged weapon or a glue wall
     * forces point blank; otherwise whoever is winning decides — an
     * advantaged Robert pushes into the 70%-90% or 90%-100% band, a losing
     * one falls back to 40%-70%.
     */
    private fun currentFireRange(state: GameState): FireRange = RobertRules.situationalRange(
        stored = state.robertRange,
        playerWeaponDamaged = isPlayerWeaponLocked(state),
        playerGlueWallActive = state.glueWallActive,
        robertHp = state.robertHp,
        robertMaxHp = state.robertMaxHp,
        playerHp = state.playerHp,
        playerMaxHp = state.playerMaxHp,
        deceptionShotsLeft = state.robertDeceptionShots,
        robertHasSniper = robertHasUsableSniper(state)
    )

    /** True when Robert owns a sniper rifle he may use and that is not damaged. */
    private fun robertHasUsableSniper(state: GameState): Boolean {
        val allowed = RobertRules.allowedWeapons(robertProfile, state.glueWallActive, state.robertHp)
        return RobertRules.hasUsableSniper(allowed, isRobertWeaponDamaged(state, Weapon.SNIPER))
    }

    /** The arena distance Robert is walking to for his current band. */
    private fun robertTargetDistance(state: GameState): Float =
        currentFireRange(state).targetDistance

    /**
     * Picks the band Robert fights from next. He randomly prefers either the
     * 40%-70% band or the 90%-100% band, and once in a while he goes to the
     * 0%-40% band instead to fire one or two shots that always miss, purely
     * to confuse the real player.
     */
    private fun rollRobertFireRange(allowDeception: Boolean) {
        val deception = allowDeception && RobertRules.shouldStartDeception(Random)
        val range = if (deception) FireRange.FAR else RobertRules.preferredRange(Random)
        val shots = if (deception) RobertRules.deceptionShots(Random) else 0
        _gameState.update {
            it.copy(robertFireRange = range.id, robertDeceptionShots = shots)
        }
    }

    /** Result of one of Robert's movement steps. */
    private data class StepOutcome(val state: GameState, val blocked: Boolean)

    /**
     * Robert's movement step. He now really walks inside the 100 m zone with
     * four directions available (towards the player, away from him and both
     * sideways), and every step is checked against the four boundaries:
     * when he detects a wall he turns back instead of pressing against it.
     *
     * States:
     *  - "advance":     walk to the distance his current firing band wants;
     *  - "retreat":     run away at his fast speed after sustained hits;
     *  - "flank":       re-approach from the side after a retreat hit the edge;
     *  - "avoid":       a glue wall he cannot break: keep away until it drops;
     *  - "missileFlee": sprint from a missile in the air;
     *  - "bombFlee":    sprint out of the 50 m blast radius of a bomb;
     *  - "turnBack":    he detected a wall and walks back into the open zone.
     */
    private fun robertStep() {
        if (!foreground || _currentScreen.value != Screen.BATTLE) return
        val state = _gameState.value
        if (state.robertHp <= 0 || state.playerHp <= 0) return

        // A damaged weapon in the player's hands stops every retreat: Robert
        // turns around and walks straight back to point blank. Nothing stops a
        // missile or a bomb escape: those runs continue until they resolve.
        val pressure = isPlayerWeaponLocked(state)
        val running = state.robertState
        if (pressure && running != "advance" && running != "missileFlee" && running != "bombFlee") {
            _gameState.update {
                it.copy(robertState = "advance", robertTurnBackTicks = 0)
            }
        }

        // While the player's glue wall is up and Robert has no sniper rifle to
        // break it, he refuses to approach and keeps running away instead.
        ensureGlueWallAvoidance()

        val round = state.round
        val speedMultiplier = RobertRules.movementSpeedMultiplier(round)
        when (_gameState.value.robertState) {
            "turnBack" -> {
                val metres = RobertRules.TURN_BACK_STEP_M * speedMultiplier
                _gameState.update { turnBackStep(it, metres) }
                val after = _gameState.value
                if (after.robertState == "turnBack") {
                    updateRobertWalkAudio(after.distance, fleeing = true, speedMps = metres / 0.3f)
                    planRobertStep(300L)
                } else {
                    audioManager.stopSound("enemyWalk")
                    rollRobertFireRange(allowDeception = false)
                    ensureRobertPositioned()
                }
            }
            "bombFlee" -> {
                val metres = BombRules.escapeStep(round)
                _gameState.update { bombFleeStep(it, metres) }
                val after = _gameState.value
                if (after.robertState == "bombFlee") {
                    updateRobertWalkAudio(
                        after.distance,
                        fleeing = true,
                        speedMps = metres / (BombRules.BOMB_FLEE_TICK_MS / 1000f)
                    )
                    planRobertStep(BombRules.BOMB_FLEE_TICK_MS)
                } else {
                    audioManager.stopSound("enemyWalk")
                }
            }
            "missileFlee" -> {
                // A missile is in the air: he sprints for the far edge of the
                // zone at his fast speed. It will still catch him.
                val metres = MissileRules.fleeStep(round, FLEE_TICK_MS)
                val fleeing = runAwayAndUpdate(metres, keepState = "missileFlee")
                if (fleeing) {
                    val after = _gameState.value
                    updateRobertWalkAudio(
                        after.distance,
                        fleeing = true,
                        speedMps = metres / (FLEE_TICK_MS / 1000f)
                    )
                    planRobertStep(FLEE_TICK_MS)
                } else {
                    audioManager.stopSound("enemyWalk")
                }
            }
            "avoid" -> {
                // He cannot break the glue wall: stay as far away as possible.
                val metres = RobertRules.fleeStep(round)
                val avoiding = runAwayAndUpdate(metres, keepState = "avoid")
                if (avoiding) {
                    val after = _gameState.value
                    updateRobertWalkAudio(
                        after.distance,
                        fleeing = true,
                        speedMps = metres / (FLEE_TICK_MS / 1000f)
                    )
                    planRobertStep(FLEE_TICK_MS)
                } else {
                    audioManager.stopSound("enemyWalk")
                    ensureRobertPositioned()
                }
            }
            "retreat" -> {
                val metres = RobertRules.fleeStep(round)
                var outcomeBlocked = false
                var flanked = false
                _gameState.update { current ->
                    val outcome = runAwayStep(current, metres)
                    outcomeBlocked = outcome.blocked
                    flanked = outcome.state.robertState == "flank"
                    outcome.state
                }
                when {
                    outcomeBlocked -> {
                        startTurnBack(_gameState.value)
                        audioManager.stopSound("enemyWalk")
                    }
                    flanked -> {
                        // The far edge is reached: he comes around from the side.
                        audioManager.stopSound("enemyWalk")
                        playSound("wall", volume = 0.7f, posZ = -_gameState.value.distance)
                        planRobertStep(900L + Random.nextInt(500))
                    }
                    else -> {
                        val after = _gameState.value
                        updateRobertWalkAudio(
                            after.distance,
                            fleeing = true,
                            speedMps = metres / (FLEE_TICK_MS / 1000f)
                        )
                        planRobertStep(FLEE_TICK_MS)
                    }
                }
            }
            "flank" -> {
                val current = _gameState.value
                val target = robertTargetDistance(current)
                val speed = (4.5f + round * 0.35f) * speedMultiplier
                var blocked = false
                _gameState.update { now ->
                    val side = now.robertFlankSide
                    val strafed = strafeStep(now, speed * 0.6f, side)
                    val base = if (strafed.blocked) now else strafed.state
                    val wanted = max(target, base.distance - speed * 0.4f)
                    val placed = base.placeRobertAt(wanted)
                    blocked = placed.blocked
                    if (blocked) base else placed.state
                }
                if (blocked) {
                    startTurnBack(_gameState.value)
                    audioManager.stopSound("enemyWalk")
                    return
                }
                val after = _gameState.value
                if (after.distance <= target + BAND_TOLERANCE_M) {
                    _gameState.update { it.copy(robertState = "advance", robertTurnBackTicks = 0) }
                    playSound("jump", posX = after.robertLateral, posZ = -after.distance)
                    audioManager.stopSound("enemyWalk")
                    msg(t("devilFlank"))
                } else {
                    updateRobertWalkAudio(after.distance, fleeing = false, speedMps = speed / 0.3f)
                    planRobertStep(300L)
                }
            }
            else -> {
                val current = _gameState.value
                val target = robertTargetDistance(current)
                val delta = current.distance - target
                if (abs(delta) <= BAND_TOLERANCE_M) {
                    // Robert has reached his firing band and is stationary.
                    audioManager.stopSound("enemyWalk")
                    return
                }
                val prox = Combat.proximityOf(current.distance) / 100f
                val speed = (2.0f + prox * 4.0f + round * 0.25f) *
                    speedMultiplier *
                    if (pressure) PRESSURE_SPEED_FACTOR else 1f
                val metres = min(abs(delta), speed)
                val tickMs = if (pressure) PRESSURE_ADVANCE_TICK_MS else ADVANCE_TICK_MS

                // All four directions: most steps walk the line to the player,
                // but once in a while he strafes sideways around him.
                val strafe = RobertRules.shouldStrafe(Random) && !pressure
                val side = RobertRules.strafeDirection(Random)
                var blocked = false
                _gameState.update { now ->
                    val outcome = when {
                        strafe -> strafeStep(now, metres, side)
                        delta > 0f -> approachStep(now, metres)
                        else -> runAwayStep(now, metres, keepState = "advance")
                    }
                    blocked = outcome.blocked
                    outcome.state
                }
                if (blocked) {
                    startTurnBack(_gameState.value)
                    return
                }
                val after = _gameState.value
                updateRobertWalkAudio(
                    after.distance,
                    fleeing = delta < 0f,
                    speedMps = metres / (tickMs / 1000f)
                )
                if (Random.nextFloat() < 0.10f) {
                    playSound("jump", volume = 0.55f, posZ = -after.distance)
                }
                planRobertStep(tickMs)
            }
        }
    }

    /** Runs one "away from the player" step; false when a wall stopped him. */
    private fun runAwayAndUpdate(metres: Float, keepState: String): Boolean {
        var blocked = false
        _gameState.update { current ->
            val outcome = runAwayStep(current, metres, keepState)
            blocked = outcome.blocked
            outcome.state
        }
        if (blocked) startTurnBack(_gameState.value)
        return !blocked && _gameState.value.robertState == keepState
    }

    /**
     * Robert detected a wall: he announces it with the wall sound, turns around
     * and walks back into the open zone for a few ticks before fighting on.
     */
    private fun startTurnBack(state: GameState) {
        _gameState.update {
            it.copy(
                robertState = "turnBack",
                robertTurnBackTicks = RobertRules.TURN_BACK_TICKS
            )
        }
        playSound("wall", volume = 0.8f, posZ = -state.distance)
        setLog(t("robertTurnedBack"))
        planRobertStep(280L)
    }

    /** Walks back towards the middle of the zone until the wall is behind him. */
    private fun turnBackStep(current: GameState, metres: Float): GameState {
        val ticks = (current.robertTurnBackTicks - 1).coerceAtLeast(0)
        val move = current.moveRobert(
            Arena.SIZE / 2f - current.robertX,
            Arena.SIZE / 2f - current.robertY,
            metres
        )
        val base = if (move.moved) move.state else current
        return if (ticks <= 0) {
            base.copy(robertState = "advance", robertTurnBackTicks = 0)
        } else {
            base.copy(robertTurnBackTicks = ticks)
        }
    }

    /** One step straight towards the player (closing the distance). */
    private fun approachStep(current: GameState, metres: Float): StepOutcome {
        val move = current.moveRobert(
            current.playerX - current.robertX,
            current.playerY - current.robertY,
            metres
        )
        return StepOutcome(if (move.moved) move.state else current, move.blockedByWall)
    }

    /** One sideways step around the player: left or right along the zone. */
    private fun strafeStep(current: GameState, metres: Float, side: Int): StepOutcome {
        val dx = current.playerX - current.robertX
        val dy = current.playerY - current.robertY
        val move = current.moveRobert(-dy * side, dx * side, metres)
        return StepOutcome(if (move.moved) move.state else current, move.blockedByWall)
    }

    /**
     * One step away from the player. When the boundary stops him completely he
     * turns back; when it only shortens the step he switches to a flank (or
     * keeps the state he was told to keep, for the avoidance and missile runs).
     */
    private fun runAwayStep(
        current: GameState,
        metres: Float,
        keepState: String = "retreat"
    ): StepOutcome {
        val move = current.moveRobert(
            current.robertX - current.playerX,
            current.robertY - current.playerY,
            metres
        )
        return when {
            move.blockedByWall -> StepOutcome(
                current.copy(
                    robertState = "turnBack",
                    robertTurnBackTicks = RobertRules.TURN_BACK_TICKS
                ),
                blocked = true
            )
            move.touchedWall && keepState == "retreat" -> {
                // The far edge is reached: come around from the side instead.
                val side = if (Random.nextBoolean()) 1 else -1
                StepOutcome(
                    move.state.copy(robertState = "flank", robertFlankSide = side),
                    blocked = false
                )
            }
            else -> StepOutcome(move.state.copy(robertState = keepState), blocked = false)
        }
    }

    /**
     * Sprint out of a bomb's 50 m blast radius, in whichever direction really
     * increases the distance from the destination. If the bomb explodes before
     * he crosses the line, the blast kills him.
     */
    private fun bombFleeStep(current: GameState, metres: Float): GameState {
        if (!current.bombInFlight) return current.copy(robertState = "advance")
        if (current.robertDistanceFromBomb >= BombRules.SAFE_DISTANCE) {
            return current.copy(robertState = "advance", robertTurnBackTicks = 0)
        }
        val escape = Arena.escapeDirection(
            current.bombX, current.bombY, current.robertX, current.robertY
        )
        val move = current.moveRobert(escape.first, escape.second, metres)
        return if (move.blockedByWall) {
            // Nowhere left to run: he turns back and takes his chances.
            current.copy(
                robertState = "turnBack",
                robertTurnBackTicks = RobertRules.TURN_BACK_TICKS
            )
        } else {
            move.state.copy(robertState = "bombFlee")
        }
    }

    /**
     * Keeps the "avoid" state in sync with the glue wall: while the player's
     * glue wall is up and Robert owns no usable sniper rifle to break it, he
     * runs away and stays away. As soon as the wall drops (or he does own a
     * working sniper) he approaches again and fires continuously.
     */
    private fun ensureGlueWallAvoidance() {
        val state = _gameState.value
        if (state.playerHp <= 0 || state.robertHp <= 0) return
        if (state.missileInFlight || state.bombInFlight) return
        val allowed = RobertRules.allowedWeapons(robertProfile, state.glueWallActive, state.robertHp)
        val sniperDamaged = isRobertWeaponDamaged(state, Weapon.SNIPER)
        val hasSniper = RobertRules.hasUsableSniper(allowed, sniperDamaged)
        val shouldAvoid = RobertRules.shouldAvoidGlueWall(state.glueWallActive, hasSniper)
        val avoiding = state.robertState == "avoid"
        if (shouldAvoid && !avoiding && state.robertState != "turnBack") {
            _gameState.update {
                it.copy(robertState = "avoid", robertDeceptionShots = 0, robertTurnBackTicks = 0)
            }
            rollRobertFireRange(allowDeception = false)
            msg(t("robertAvoidingGlueWall"))
            planRobertStep(140L)
        } else if (!shouldAvoid && avoiding) {
            _gameState.update { it.copy(robertState = "advance", robertTurnBackTicks = 0) }
            msg(t("robertApproachingAgain"))
            ensureRobertPositioned()
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

    /**
     * Situational fire timing — never a fixed clock. An advantaged Robert
     * (or one pressing a damaged weapon) fires continuously; an even fight
     * uses a varied human-like pace; a losing or fleeing Robert fires only
     * sparsely while he repositions.
     */
    private fun robertFireInterval(): Long {
        val state = _gameState.value
        val situation = RobertRules.assessSituation(
            robertHp = state.robertHp,
            robertMaxHp = state.robertMaxHp,
            playerHp = state.playerHp,
            playerMaxHp = state.playerMaxHp,
            playerWeaponDamaged = isPlayerWeaponLocked(state)
        )
        val fleeing = state.robertFleeing ||
            state.robertState == "missileFlee" ||
            state.robertState == "bombFlee" ||
            state.robertState == "avoid"
        // Continuous fire back at continuous fire, and continuous fire while
        // the player's own health is dropping.
        val continuous = RobertRules.shouldFireContinuously(
            playerBurstShots = state.playerBurstShots,
            playerHealthDropping = playerHealthDropping(state),
            situation = situation
        )
        return RobertRules.fireIntervalMs(situation, fleeing, Random, continuous)
    }

    /** True while the player's health went down inside the current burst. */
    private fun playerHealthDropping(state: GameState): Boolean =
        state.playerBurstShots > 0 && state.playerHp < state.playerBurstPlayerHp

    /** True while Robert's health went down inside the current burst. */
    private fun robertHealthDropping(state: GameState): Boolean =
        state.playerBurstShots > 0 && state.robertHp < state.playerBurstRobertHp

    /**
     * Registers one player shot in the burst counter that Robert reads. Shots
     * further apart than [RobertRules.PLAYER_BURST_WINDOW_MS] start a new
     * burst, and the health both fighters had at the start of the burst is
     * remembered so Robert can tell whose health is really dropping.
     */
    private fun registerPlayerBurstShot() {
        val now = System.currentTimeMillis()
        _gameState.update { state ->
            val fresh = now - playerBurstStartedAtMs > RobertRules.PLAYER_BURST_WINDOW_MS
            if (fresh) playerBurstStartedAtMs = now
            state.copy(
                playerBurstShots = if (fresh) 1 else state.playerBurstShots + 1,
                playerBurstRobertHp = if (fresh) state.robertHp else state.playerBurstRobertHp,
                playerBurstPlayerHp = if (fresh) state.playerHp else state.playerBurstPlayerHp
            )
        }
    }

    /** Drops a burst that has gone quiet, so Robert stops answering it. */
    private fun expirePlayerBurstIfStale() {
        val state = _gameState.value
        if (state.playerBurstShots <= 0) return
        if (System.currentTimeMillis() - playerBurstStartedAtMs <= RobertRules.PLAYER_BURST_WINDOW_MS) return
        _gameState.update {
            it.copy(
                playerBurstShots = 0,
                playerBurstRobertHp = it.robertHp,
                playerBurstPlayerHp = it.playerHp
            )
        }
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
        // While a missile is in the air all normal fire is suspended: the
        // target is running for its life and the outcome is already sealed.
        if (state.missileInFlight) return
        // The same holds for a bomb counting down towards him: he only runs.
        if (state.bombInFlight && state.bombFromPlayer) return
        expirePlayerBurstIfStale()

        // Round four and later he may spend his one missile power, but only
        // as a desperation weapon while he is clearly losing.
        if (
            MissileRules.shouldEnemyLaunch(
                round = state.round,
                missilePowers = state.robertMissilePowers,
                robertHp = state.robertHp,
                robertMaxHp = state.robertMaxHp,
                playerHp = state.playerHp,
                playerMaxHp = state.playerMaxHp,
                robertShots = state.robertShots,
                missileInFlight = state.missileInFlight
            )
        ) {
            launchEnemyMissile()
            return
        }

        // From round three he also carries bombs. Like his missile they are a
        // desperation weapon: he throws one only while clearly losing.
        if (
            BombRules.shouldEnemyThrow(
                round = state.round,
                bombsLeft = state.robertBombs,
                robertHp = state.robertHp,
                robertMaxHp = state.robertMaxHp,
                playerHp = state.playerHp,
                playerMaxHp = state.playerMaxHp,
                robertShots = state.robertShots,
                bombInFlight = state.bombInFlight,
                missileInFlight = state.missileInFlight
            )
        ) {
            launchEnemyBomb()
            return
        }

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
        val proximityNow = Combat.proximityOf(state.distance)
        // A raised glue wall can only be answered by the sniper rifle, so
        // Robert switches to it automatically whenever it is allowed. And when
        // he spots the player closing in holding a submachine gun, he answers
        // with his own MP40 without being told.
        val chosen = when {
            state.glueWallActive && pool.contains(Weapon.SNIPER) -> Weapon.SNIPER
            state.activeWeapon.autoFire &&
                pool.contains(Weapon.MP40) &&
                Combat.zoneFor(proximityNow) != HitZone.MISS -> Weapon.MP40
            else -> pool[Random.nextInt(pool.size)]
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

        // A melee attack must not play its swing/switch sound before the
        // outcome is known: a miss uses only *-miss and a hit uses *-hit.
        if (!chosen.melee) {
            playSound(chosen.fireSoundKey, volume = 0.9f, posZ = -state.distance)
        }
        _gameState.update { s ->
            val nextAmmo = if (chosen.needsReload) {
                s.robertAmmo.toMutableList().also {
                    it[s.robertWeaponIndex] = (it[s.robertWeaponIndex] - 1).coerceAtLeast(0)
                }
            } else {
                s.robertAmmo
            }
            s.copy(
                robertShots = s.robertShots + 1,
                robertExchangeShots = s.robertExchangeShots + 1,
                robertAmmo = nextAmmo
            )
        }
        state = _gameState.value

        // The 0%-40% deception band is only held for one or two shots; every
        // one of them misses on purpose, then Robert picks a real band again.
        if (currentFireRange(state) == FireRange.FAR && state.robertDeceptionShots > 0) {
            val shotsLeft = state.robertDeceptionShots - 1
            _gameState.update { it.copy(robertDeceptionShots = shotsLeft) }
            if (shotsLeft <= 0) {
                rollRobertFireRange(allowDeception = false)
                ensureRobertPositioned()
            }
        }

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
            playSound("glueHit", volume = 0.9f)
            if (progress >= Combat.GLUE_WALL_BREAK_HITS) {
                _gameState.update {
                    it.copy(glueWallActive = false, glueWallProgress = 0)
                }
                playSound("glueWallCrack", volume = 1.0f)
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

        // The player's cover stops eleven hits and breaks on the twelfth.
        if (state.coverActive) {
            // The first eleven hits are absorbed; the twelfth breaks it.
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

        // Robert's assault rifle and shotgun use his explicit per-zone table;
        // every other weapon uses the standard player table.
        var dealt = RobertRules.weaponDamage(chosen, proxPercent, state.playerMaxHp)
        // While the quick menu is genuinely being navigated every hit is cut
        // down to a fixed percentage of maximum health. The protection expires
        // on its own, so browsing the menu can never become invulnerability.
        if (isMenuProtectionActive()) {
            dealt = min(dealt, Combat.menuProtectedDamage(state.playerMaxHp))
        }

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
                // Landing a hit ends the one-sided exchange.
                robertConsecutiveHits = 0,
                robertExchangeShots = 0,
                damaged = damaged
            )
        }

        // The weapon's own hit sound is always mixed with the sound of the
        // fighter that was hit: here the player, so player-hit.
        playSound(chosen.hitSoundKey, volume = 1.0f)
        playSound("playerHit", volume = 1.0f)

        val hitHead = if (zone == HitZone.HEAVY) t("youWereHit") else t("youWereGrazed")
        var text = "$hitHead ${t(chosen.nameKey)}. ${t("hitFor")} $dealt. " +
            "${t("yourHealth")} $newPlayerHp."
        if (brokeWeapon) {
            text += " ${t("gunDamaged")} ${t("recovery")}. " +
                "${t("coins")}: ${Combat.GUN_RECOVERY_COST}. ${t("robertPressing")}"
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
                ensureRobertPositioned()
                msg(text)
            } else {
                msg(text)
                endGame(won = false)
            }
            return
        }

        text += applyAutomaticDefences()
        ensureRobertPositioned()
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
            !_gameState.value.coverActive &&
            _inventory.value.glueWalls > 0
        ) {
            useDefence(DefenceItem.GLUE_WALL, automatic = true)?.let { extra += " $it" }
        }
        if (inv.extensionEnabled(Extension.AUTO_COVER) &&
            lastStage &&
            !_gameState.value.coverActive &&
            !_gameState.value.glueWallActive &&
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

        // He runs as fast as possible either after the round's hit streak,
        // or immediately when he is at a disadvantage and an exchange turns
        // one-sided against him: two or three sustained hits while he managed
        // only one or two answering shots inside combat range.
        val situation = RobertRules.assessSituation(
            robertHp = state.robertHp,
            robertMaxHp = state.robertMaxHp,
            playerHp = state.playerHp,
            playerMaxHp = state.playerMaxHp,
            playerWeaponDamaged = isPlayerWeaponLocked(state)
        )
        val disadvantaged = situation == RobertRules.CombatSituation.DISADVANTAGE ||
            state.robertHp < state.playerHp
        val mustFlee = RobertRules.shouldFlee(consecutive, profile.fleeAfterHits) ||
            RobertRules.shouldFleeFromExchange(
                hitsTakenInRow = consecutive,
                shotsFiredInExchange = state.robertExchangeShots,
                proximityPercent = Combat.proximityOf(state.distance),
                atDisadvantage = disadvantaged
            )
        // His own health dropping under continuous fire makes him run; the
        // player's health dropping does the opposite, so Robert keeps firing.
        val sustained = RobertRules.shouldRetreatFromSustainedFire(
            playerBurstShots = state.playerBurstShots,
            robertHealthDropping = robertHealthDropping(state),
            playerHealthDropping = playerHealthDropping(state)
        )
        if ((state.robertState == "advance" || state.robertState == "flank") && (mustFlee || sustained)) {
            _gameState.update {
                it.copy(
                    robertState = "retreat",
                    robertConsecutiveHits = 0,
                    robertExchangeShots = 0,
                    robertLateral = 0f
                )
            }
            // He picks a new firing band for the way back, and sometimes that
            // band is the confusing 0%-40% one.
            rollRobertFireRange(allowDeception = true)
            playSound("jump", posZ = -state.distance)
            planRobertStep(120L)
            return " ${t("robertFled")}"
        }

        // Re-arm the defence once Robert is healthy again for this round or
        // after two hits. The health level depends on the round: 60% (never
        // below 100 HP) in rounds one and two, 200 HP in round three, 300 HP
        // in rounds four and five.
        val threshold = RobertRules.defenceThresholdHp(state.round, state.robertMaxHp)
        if (state.robertHp > threshold || consecutive >= 2) {
            _gameState.update { it.copy(robertDefenceArmed = true) }
        }

        val current = _gameState.value
        if (
            !RobertRules.shouldUseDefence(
                robertHp = current.robertHp,
                defenceArmed = current.robertDefenceArmed,
                thresholdHp = RobertRules.defenceThresholdHp(current.round, current.robertMaxHp)
            )
        ) {
            return ""
        }

        // He now picks situationally instead of always in the same order: a
        // barrier when the player is close, a medkit when he is below half.
        val defence = RobertRules.chooseDefence(
            medikits = current.robertMedkits,
            covers = current.robertCovers,
            glueWalls = current.robertGlueWalls,
            robertHp = current.robertHp,
            robertMaxHp = current.robertMaxHp,
            proximityPercent = Combat.proximityOf(current.distance)
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
                ensureRobertPositioned()
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
            // The two interceptors are never spent here: the anti-missile is
            // fired at a missile in the air and the anti-bomb explosive is
            // deployed the moment Robert hears a bomb coming.
            DefenceItem.ANTI_MISSILE -> ""
            DefenceItem.ANTI_BOMB -> ""
        }
    }

    // ---------------------------------------------------------------------
    // Guided missiles (player and Robert)
    // ---------------------------------------------------------------------

    /**
     * Swiping up with the missile selected launches it, consuming exactly
     * one stacked missile power. Without a power nothing is spent and the
     * swipe-down purchase is announced instead.
     */
    private fun launchPlayerMissile() {
        val state = _gameState.value
        if (!MissileRules.canLaunch(
                ownsMissile = _inventory.value.ownsWeapon(Weapon.MISSILE),
                missilePowers = _inventory.value.missilePowers,
                missileInFlight = state.missileInFlight
            )
        ) {
            playSound("ammo")
            msg(t("missileNoPower"))
            return
        }
        val updated = Shop.consumeMissilePower(_inventory.value)
        _inventory.value = updated
        prefs.saveInventory(updated)

        val fleeing = MissileRules.targetWillFlee(state.distance)
        val intercept = MissileRules.willIntercept(
            round = state.round,
            robertHp = state.robertHp,
            antiMissilesLeft = state.robertAntiMissiles,
            missileInFlight = true
        )
        _gameState.update {
            it.copy(
                missileInFlight = true,
                missileFromPlayer = true,
                missileInterceptPlanned = intercept,
                playerShots = it.playerShots + 1,
                robertState = if (fleeing) "missileFlee" else it.robertState,
                robertLateral = 0f
            )
        }
        // A fleeing target is chased with the sound on loop; a target with
        // nowhere to run is hit as the single play of the sound ends.
        if (MissileRules.soundLoops(state.distance)) {
            audioManager.playLoop("missile")
        } else {
            playSound("missile")
        }
        var text = t("missileLaunched")
        if (fleeing) {
            text += " ${t("missileEnemyFleeing")}"
            planRobertStep(120L)
        }
        msg(text)
        scheduleMissileImpact(MissileRules.flightMs(state.distance))
    }

    /** Robert's one desperation missile per game (rounds four and five). */
    private fun launchEnemyMissile() {
        val state = _gameState.value
        _gameState.update {
            it.copy(
                robertMissilePowers = (it.robertMissilePowers - 1).coerceAtLeast(0),
                missileInFlight = true,
                missileFromPlayer = false,
                missileInterceptPlanned = false,
                robertShots = it.robertShots + 1
            )
        }
        if (MissileRules.soundLoops(state.distance)) {
            audioManager.playLoop("missile")
        } else {
            playSound("missile")
        }
        msg(t("robertMissileLaunched"))
        scheduleMissileImpact(MissileRules.flightMs(state.distance))
    }

    private fun scheduleMissileImpact(delayMs: Long) {
        missileJob?.cancel()
        missileImpactAtMs = System.currentTimeMillis() + delayMs
        missileJob = viewModelScope.launch {
            delay(delayMs)
            onMissileImpact()
        }
    }

    /**
     * Missile impact. The looping flight sound stops at once and the
     * explosion plays, mixed with the hit sound of the destroyed fighter.
     * The target drops directly to zero health through running, glue walls
     * and covers alike — unless Robert's end-game anti-missile system
     * (round four once, round five twice, his health below 100) destroys
     * the player's missile in the air instead.
     */
    private fun onMissileImpact() {
        val state = _gameState.value
        if (_currentScreen.value != Screen.BATTLE || !state.missileInFlight) return
        if (state.playerHp <= 0 || state.robertHp <= 0) return
        missileImpactAtMs = 0L
        audioManager.stopSound("missile")
        audioManager.stopSound("enemyWalk")

        if (state.missileFromPlayer) {
            val intercepted = state.missileInterceptPlanned ||
                MissileRules.willIntercept(
                    round = state.round,
                    robertHp = state.robertHp,
                    antiMissilesLeft = state.robertAntiMissiles,
                    missileInFlight = state.missileInFlight
                )
            if (intercepted) {
                playSound("explosion")
                _gameState.update {
                    it.copy(
                        missileInFlight = false,
                        missileFromPlayer = false,
                        missileInterceptPlanned = false,
                        robertAntiMissiles = (it.robertAntiMissiles - 1).coerceAtLeast(0),
                        robertState = "advance",
                        robertLateral = 0f
                    )
                }
                rollRobertFireRange(allowDeception = false)
                msg(t("antiMissileIntercept"))
                planRobertStep(500L)
                return
            }
            audioManager.playMixed(listOf("explosion", "enemyHit"))
            _gameState.update {
                it.copy(
                    robertHp = 0,
                    missileInFlight = false,
                    missileFromPlayer = false,
                    missileInterceptPlanned = false,
                    robertCoverActive = false,
                    robertGlueWallActive = false,
                    playerHits = it.playerHits + 1
                )
            }
            msg(t("missileHitEnemy"))
            // The enemy-hit sound and the explosion play together and the
            // battle is only ended once the explosion has finished.
            endGameAfterExplosion(won = true)
        } else {
            // Enemy missile hit: play player-hit.mp3 alongside explosion-sound.mp3.
            audioManager.playMixed(listOf("explosion", "playerHit"))
            _gameState.update {
                it.copy(
                    playerHp = 0,
                    missileInFlight = false,
                    missileFromPlayer = false,
                    missileInterceptPlanned = false,
                    coverActive = false,
                    glueWallActive = false,
                    robertHits = it.robertHits + 1
                )
            }
            msg(t("missileHitPlayer"))
            // A missile destroys completely: even the second chance does
            // not apply to a direct missile hit. The result is spoken only
            // after the explosion has run out.
            endGameAfterExplosion(won = false)
        }
    }

    /**
     * Swiping down with the missile selected buys one missile power for
     * 600 coins, exactly as announced when no power is owned.
     */
    private fun buyMissilePowerInBattle() {
        if (_gameState.value.missileInFlight) {
            msg(t("missileInFlight"))
            return
        }
        val result = Shop.buyMissilePower(
            _inventory.value,
            _stats.value.coins,
            _stats.value.completedRounds
        )
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
        msg("${t("missilePowerBought")} ${t("extMissilePowers")} ${result.inventory.missilePowers}.")
    }

    // ---------------------------------------------------------------------
    // Bombs: grenade, atom bomb and timer bomb (round three onwards)
    // ---------------------------------------------------------------------

    /**
     * Opens the timer bomb duration picker. Swiping up with the timer bomb
     * selected never throws it straight away: first the player hears
     * "Select your timer" and picks 15, 30, 40, 50 or 60 seconds with a left
     * or right swipe, then locks the choice with a tap in the centre.
     */
    private fun startTimerSelection() {
        val state = _gameState.value
        if (state.bombInFlight || state.missileInFlight) {
            playSound("click")
            msg(if (state.missileInFlight) t("missileInFlight") else t("bombInFlight"))
            return
        }
        if (state.timerSelectActive) {
            speakTimerSelection()
            return
        }
        _gameState.update {
            it.copy(timerSelectActive = true, timerIndex = BombRules.DEFAULT_TIMER_INDEX)
        }
        playSound("click")
        msg(t("selectTimer"))
        speakTimerSelection(instructions = true)
    }

    /** Walks the timer picker one duration left (-1) or right (+1). */
    fun cycleTimerSelection(direction: Int) {
        val state = _gameState.value
        if (!state.timerSelectActive) return
        markExplicitAction()
        _gameState.update {
            it.copy(timerIndex = BombRules.stepTimerIndex(it.timerIndex, direction))
        }
        playSound("click")
        speakTimerSelection()
    }

    /** A tap in the centre locks the highlighted duration and throws the bomb. */
    fun confirmTimerSelection() {
        val state = _gameState.value
        if (!state.timerSelectActive) return
        markExplicitAction()
        _gameState.update { it.copy(timerSelectActive = false) }
        msg("${t("timerSet")} ${state.timerSeconds} ${t("seconds")}.")
        launchPlayerBomb()
    }

    /** Cancels the picker without throwing (used when leaving the battle). */
    fun cancelTimerSelection() {
        if (!_gameState.value.timerSelectActive) return
        _gameState.update { it.copy(timerSelectActive = false) }
        playSound("click")
        msg(t("timerCancelled"))
    }

    /** Speaks the highlighted duration, and how to change it on first open. */
    private fun speakTimerSelection(instructions: Boolean = false) {
        val seconds = _gameState.value.timerSeconds
        val text = if (instructions) {
            "${t("selectTimer")} $seconds ${t("seconds")}. ${t("timerHowTo")}"
        } else {
            "$seconds ${t("seconds")}"
        }
        setLog(text)
        speak(text)
    }

    /**
     * Throws the selected bomb. Its destination is fixed the moment it leaves
     * the hand - Robert's position - and the countdown is exactly the length
     * of the bomb's own sound (the timer bomb counts for its chosen duration
     * with timer-sound on loop). Robert hears it and sprints for the 50 metre
     * safety line; if the blast catches him inside it, he is destroyed.
     */
    private fun launchPlayerBomb() {
        val state = _gameState.value
        val weapon = state.activeWeapon
        val kind = BombKind.fromWeapon(weapon) ?: return
        if (state.bombInFlight) {
            playSound("click")
            msg(t("bombInFlight"))
            return
        }
        if (state.missileInFlight) {
            playSound("click")
            msg(t("missileInFlight"))
            return
        }

        var spent = false
        _gameState.update { s ->
            val idx = s.weaponIndex.coerceIn(0, s.ammo.lastIndex)
            if (s.ammo.getOrElse(idx) { 0 } <= 0) return@update s
            spent = true
            // Robert deploys his one anti-bomb explosive per game from round
            // three the moment he hears a bomb coming.
            val intercept = BombRules.willIntercept(
                round = s.round,
                antiBombsLeft = s.robertAntiBombs,
                bombInFlight = true
            )
            s.copy(
                bombInFlight = true,
                bombFromPlayer = true,
                bombKind = kind.id,
                bombX = s.robertX,
                bombY = s.robertY,
                bombInterceptPlanned = intercept,
                bombInterceptedByPlayer = false,
                timerSelectActive = false,
                playerShots = s.playerShots + 1,
                ammo = s.ammo.toMutableList().also { list -> list[idx] = 0 },
                robertState = if (intercept) s.robertState else "bombFlee",
                robertTurnBackTicks = 0
            )
        }
        if (!spent) {
            playSound("ammo")
            msg(t("bombNoCharge"))
            return
        }

        val after = _gameState.value
        val flightMs = BombRules.flightMs(kind, after.timerSeconds)
        startBombAudio(kind, distance = after.distance, timer = kind == BombKind.TIMER)
        bombBlastAtMs = System.currentTimeMillis() + flightMs

        var text = "${t("bombThrown")} ${t(kind.nameKey)}."
        if (kind == BombKind.TIMER) {
            text += " ${t("timerCountdown")} ${after.timerSeconds} ${t("seconds")}."
        } else {
            text += " ${t("bombEnemyFleeing")} ${BombRules.SAFE_DISTANCE.toInt()} ${t("meters")}."
        }
        msg(text)
        planRobertStep(120L)
        scheduleBombBlast(flightMs)
    }

    /**
     * Robert throws one of his own bombs (round three: one per game, rounds
     * four and five: two). The destination is the player's position and the
     * player must sprint out of the 50 metres or deploy the anti-bomb
     * explosive defence while the bomb sound is still running.
     */
    private fun launchEnemyBomb() {
        val state = _gameState.value
        if (state.robertBombs <= 0 || state.bombInFlight || state.missileInFlight) return
        val kind = if (state.round >= 4 && Random.nextBoolean()) BombKind.ATOM else BombKind.GRENADE
        val flightMs = BombRules.flightMs(kind)
        _gameState.update {
            it.copy(
                robertBombs = (it.robertBombs - 1).coerceAtLeast(0),
                bombInFlight = true,
                bombFromPlayer = false,
                bombKind = kind.id,
                bombX = it.playerX,
                bombY = it.playerY,
                bombInterceptPlanned = false,
                bombInterceptedByPlayer = false,
                robertShots = it.robertShots + 1
            )
        }
        startBombAudio(kind, distance = state.distance, timer = false)
        bombBlastAtMs = System.currentTimeMillis() + flightMs
        val bombWarning = "${t("bombRunAway")} ${BombRules.SAFE_DISTANCE.toInt()} ${t("meters")}."
        msg("${t("robertBombThrown")} ${t(kind.nameKey)}. $bombWarning")
        speak(bombWarning)
        scheduleBombBlast(flightMs)
    }

    /** Starts the countdown audio of a thrown bomb. */
    private fun startBombAudio(kind: BombKind, distance: Float, timer: Boolean) {
        if (timer) {
            // timer-sound.mp3 loops for the whole countdown, then stops and
            // the explosion takes over.
            audioManager.playLoop(kind.flightSoundKey)
        } else {
            // The grenade and atom bomb sounds are exactly as long as their
            // flight, so a single play ends with the blast.
            playSound(kind.flightSoundKey, volume = BOMB_VOLUME, posZ = -distance)
        }
    }

    private fun scheduleBombBlast(delayMs: Long) {
        bombJob?.cancel()
        bombJob = viewModelScope.launch {
            delay(delayMs)
            onBombBlast()
        }
    }

    /**
     * The blast. The countdown sound stops at once and the explosion plays
     * mixed with the hit sound of whoever it caught - and it plays in every
     * outcome: when the bomb destroys its target, when the target escaped the
     * 50 metres, and when an anti-bomb explosive destroyed the bomb in the
     * air. A finished battle is only ended after the explosion has run out.
     */
    private fun onBombBlast() {
        val state = _gameState.value
        if (_currentScreen.value != Screen.BATTLE || !state.bombInFlight) return
        if (state.playerHp <= 0 || state.robertHp <= 0) return
        bombBlastAtMs = 0L
        val kind = state.bomb ?: BombKind.GRENADE
        val fromPlayer = state.bombFromPlayer
        audioManager.stopLoop("timerLoop")
        audioManager.stopSound("timerLoop")
        audioManager.stopSound(kind.flightSoundKey)
        audioManager.stopSound("enemyWalk")
        audioManager.stopFootsteps()
        audioManager.stopSound("playerWalk")

        val targetDistance =
            if (fromPlayer) state.robertDistanceFromBomb else state.playerDistanceFromBomb
        val throwerDistance =
            if (fromPlayer) state.playerDistanceFromBomb else state.robertDistanceFromBomb
        val intercepted = if (fromPlayer) {
            state.bombInterceptPlanned ||
                BombRules.willIntercept(state.round, state.robertAntiBombs, bombInFlight = true)
        } else {
            state.bombInterceptedByPlayer
        }

        // An anti-bomb explosive destroys the bomb in the air: the explosion
        // still plays, exactly like an intercepted missile, but nobody is hit.
        if (intercepted) {
            playSound("explosion")
            _gameState.update {
                it.copy(
                    bombInFlight = false,
                    bombFromPlayer = false,
                    bombKind = "",
                    bombInterceptPlanned = false,
                    bombInterceptedByPlayer = false,
                    robertAntiBombs = if (fromPlayer) {
                        (it.robertAntiBombs - 1).coerceAtLeast(0)
                    } else {
                        it.robertAntiBombs
                    },
                    robertState = if (fromPlayer) "advance" else it.robertState,
                    robertTurnBackTicks = 0
                )
            }
            msg(if (fromPlayer) t("antiBombIntercept") else t("antiBombSavedYou"))
            rollRobertFireRange(allowDeception = false)
            ensureRobertPositioned()
            planRobertStep(400L)
            return
        }

        val killsTarget = BombRules.blastKills(targetDistance)
        // The timer bomb is the one bomb that also destroys its own thrower
        // when the thrower stayed inside the 50 metre safety distance.
        val killsThrower = kind == BombKind.TIMER &&
            BombRules.timerBombKillsThrower(throwerDistance)

        // Explosion and the hit sound of the destroyed fighter play together.
        audioManager.playMixed(
            listOf(kind.blastSoundKey, if (fromPlayer) "enemyHit" else "playerHit")
        )

        _gameState.update {
            it.copy(
                // The target of the bomb, and - for a timer bomb only - its
                // own thrower when the thrower stayed inside the 50 metres.
                robertHp = when {
                    fromPlayer && killsTarget -> 0
                    !fromPlayer && killsThrower -> 0
                    else -> it.robertHp
                },
                playerHp = when {
                    !fromPlayer && killsTarget -> 0
                    fromPlayer && killsThrower -> 0
                    else -> it.playerHp
                },
                playerHits = if (fromPlayer && killsTarget) it.playerHits + 1 else it.playerHits,
                robertHits = if (!fromPlayer && killsTarget) it.robertHits + 1 else it.robertHits,
                bombInFlight = false,
                bombFromPlayer = false,
                bombKind = "",
                bombInterceptPlanned = false,
                bombInterceptedByPlayer = false,
                coverActive = if (!fromPlayer && killsTarget) false else it.coverActive,
                glueWallActive = if (!fromPlayer && killsTarget) false else it.glueWallActive,
                robertCoverActive = if (fromPlayer && killsTarget) false else it.robertCoverActive,
                robertGlueWallActive = if (fromPlayer && killsTarget) false else it.robertGlueWallActive,
                robertState = "advance",
                robertTurnBackTicks = 0
            )
        }

        val text = buildString {
            append(t("bombExploded"))
            append(" ")
            append(
                when {
                    fromPlayer && killsTarget -> t("bombHitEnemy")
                    !fromPlayer && killsTarget -> t("bombHitPlayer")
                    else -> t("bombEscaped") +
                        " ${targetDistance.toInt()} ${t("meters")}."
                }
            )
            if (killsThrower) {
                append(" ")
                append(if (fromPlayer) t("bombKilledYou") else t("bombKilledRobert"))
            }
        }
        msg(text)

        val after = _gameState.value
        val playerDead = after.playerHp <= 0
        val robertDead = after.robertHp <= 0
        when {
            // Both destroyed by a timer bomb: the thrower loses.
            playerDead && robertDead -> endGameAfterExplosion(won = !fromPlayer)
            robertDead -> endGameAfterExplosion(won = true)
            playerDead -> endGameAfterExplosion(won = false)
            else -> {
                // The bomb exploded harmlessly: the battle simply goes on.
                rollRobertFireRange(allowDeception = false)
                ensureRobertPositioned()
                planRobertStep(500L)
            }
        }
    }

    /**
     * Ends the battle only once the explosion has finished. The hit sound and
     * the explosion are already playing together, and cutting them short to
     * speak the result would swallow the blast, so the result waits for the
     * exact length of explosion-sound.mp3.
     */
    private fun endGameAfterExplosion(won: Boolean) {
        if (endingJob != null) return
        endingJob = viewModelScope.launch {
            delay(SoundDurations.EXPLOSION_MS)
            endingJob = null
            if (foreground && _currentScreen.value == Screen.BATTLE) endGame(won)
        }
    }

    /** True while the closing explosion of a missile or a bomb is running. */
    private fun isBattleEnding(): Boolean = endingJob != null

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
        if (state.missileInFlight) {
            playSound("click")
            msg(t("missileInFlight"))
            return
        }
        // A bomb counting down owns the battlefield: nothing else is fired
        // until it explodes, exactly like a missile in the air.
        if (state.bombInFlight || isBattleEnding()) {
            playSound("click")
            msg(if (state.bombInFlight) t("bombInFlight") else t("waitExplosion"))
            return
        }

        val weapon = state.activeWeapon
        if (state.activeWeaponDamaged) {
            playSound("ammo")
            msg("${t("gunDamaged")} ${t("recovery")}. ${t("coins")}: ${Combat.GUN_RECOVERY_COST}.")
            return
        }

        // Swipe up with the missile selected launches the missile instead
        // of a normal shot (one stacked missile power is consumed).
        if (weapon.missileLauncher) {
            launchPlayerMissile()
            return
        }

        // A bomb is thrown, not fired. The timer bomb first asks for its
        // duration; the grenade and the atom bomb fly straight away.
        if (weapon.bomb) {
            if (weapon.needsTimerSelection && !state.timerSelectActive) {
                startTimerSelection()
            } else if (state.timerSelectActive) {
                confirmTimerSelection()
            } else {
                launchPlayerBomb()
            }
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
        // One shot of a burst, so Robert can tell continuous fire from a
        // single tap and answer it with continuous fire of his own.
        registerPlayerBurstShot()
        // Knife and sword sounds are selected strictly after hit/miss is known.
        // A held automatic burst already has its sound on loop, so the single
        // shot sound is only played for taps and for every other weapon.
        if (!weapon.melee && !(weapon.autoFire && state.autoFireActive)) {
            playSound(weapon.fireSoundKey, volume = 1.0f)
        }

        val proxPercent = Combat.proximityOf(state.distance)
        val zone = Combat.zoneFor(proxPercent)
        // The AWM is the long-range rifle: inside its 20% to 40% band it hits
        // where every other weapon misses completely.
        val longRange = DamageTable.isAwmLongRangeBand(weapon, proxPercent)

        if (zone == HitZone.MISS && !longRange) {
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
            playSound("glueHit", volume = 0.9f)
            if (progress >= Combat.GLUE_WALL_BREAK_HITS) {
                _gameState.update {
                    it.copy(robertGlueWallActive = false, robertGlueWallProgress = 0)
                }
                playSound("glueWallCrack")
                msg(t("robertGlueDown"))
            } else if (breaks) {
                msg("${t("robertGlueCrack")} ${t("glueWallBreaks")} $progress.")
            } else {
                msg(t("robertGlueBlocked"))
            }
            robertOnHit()
            return
        }

        // Robert's cover: eleven hits, broken on the twelfth.
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

        val damage = Combat.weaponDamage(weapon, proxPercent, state.robertMaxHp)
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

        var text = "${
            when {
                longRange -> t("awmLongRangeHit")
                zone == HitZone.HEAVY -> t("enemyHit")
                else -> t("enemyGrazed")
            }
        } " + "${t("hitFor")} $damage. ${t("enemyHealth")} $newRobertHp."
        if (robertWeaponBroke) {
            playSound("gunReload", volume = 0.8f)
            text += " ${t("robertWeaponDamaged")}"
        }

        if (newRobertHp <= 0) {
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

    /**
     * Holding the up-swipe with a submachine gun (MP40 or machine gun)
     * selected keeps firing: one bullet per [Weapon.burstIntervalMs] for at
     * most [Weapon.maxBurstMs], with the weapon's own sound on loop for the
     * whole burst. Releasing the swipe stops the bullets and the sound at
     * once, and the player may hold or release at any moment.
     */
    fun startHoldFire() {
        if (_currentScreen.value != Screen.BATTLE) return
        val state = _gameState.value
        val weapon = state.activeWeapon
        if (!weapon.autoFire || state.autoFireActive) {
            fire()
            return
        }
        if (!holdFireAllowed()) {
            fire()
            return
        }
        markExplicitAction()
        _gameState.update { it.copy(autoFireActive = true) }
        // The 4 second MP40 sound loops for as long as the burst runs.
        audioManager.playLoop(weapon.fireSoundKey)
        autoFireJob?.cancel()
        autoFireJob = viewModelScope.launch {
            val deadline = System.currentTimeMillis() + weapon.maxBurstMs
            // The first bullet leaves at once, the rest follow the interval.
            fireOneAutomaticShot()
            while (System.currentTimeMillis() < deadline) {
                delay(weapon.burstIntervalMs)
                if (!holdFireAllowed()) break
                fireOneAutomaticShot()
            }
            stopHoldFire()
        }
    }

    /** Releasing the swipe: the burst and its looped sound both stop. */
    fun stopHoldFire() {
        autoFireJob?.cancel()
        autoFireJob = null
        val state = _gameState.value
        if (!state.autoFireActive) return
        _gameState.update { it.copy(autoFireActive = false) }
        audioManager.stopLoop(state.activeWeapon.fireSoundKey)
        audioManager.stopSound(state.activeWeapon.fireSoundKey)
    }

    /** One bullet of a held automatic burst. */
    private fun fireOneAutomaticShot() {
        if (!holdFireAllowed()) return
        lastFireAt = 0L
        fire()
    }

    /** True while a held automatic burst may keep firing. */
    private fun holdFireAllowed(): Boolean {
        if (!foreground || _currentScreen.value != Screen.BATTLE) return false
        val state = _gameState.value
        if (state.playerHp <= 0 || state.robertHp <= 0) return false
        if (state.missileInFlight || state.bombInFlight || isBattleEnding()) return false
        if (state.activeWeaponDamaged) return false
        if (!state.activeWeapon.autoFire) return false
        if (state.timerSelectActive) return false
        return state.activeAmmo > 0
    }

    /** True while the selected weapon is a hold-to-fire submachine gun. */
    fun activeWeaponIsAutomatic(): Boolean = _gameState.value.activeWeapon.autoFire

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
        if (weapon.missileLauncher) {
            // The missile has no magazine: swipe down buys one missile
            // power for 600 coins, as announced when none is owned.
            buyMissilePowerInBattle()
            return
        }
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
        // While the timer bomb picker is open the same left / right swipe
        // walks through the durations instead of changing weapon.
        if (state.timerSelectActive) {
            cycleTimerSelection(direction)
            return
        }
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
        // Moving from a broken weapon to a working one ends Robert's pressure.
        val pressureNote = if (state.activeWeaponDamaged && !_gameState.value.activeWeaponDamaged) {
            " ${t("robertPressureOver")}"
        } else {
            ""
        }
        val ammoNote = if (weapon.needsReload) {
            " ${t("ammo")} ${_gameState.value.activeAmmo}."
        } else {
            ""
        }
        // The missile announces its stacked powers, or the swipe-down
        // purchase when the player owns none.
        val missileNote = if (weapon.missileLauncher) {
            val powers = _inventory.value.missilePowers
            if (powers > 0) {
                " ${t("extMissilePowers")} $powers. ${t("missileReady")}"
            } else {
                " ${t("missileNoPower")}"
            }
        } else {
            ""
        }
        msg("${t("switchedTo")} ${t(weapon.nameKey)}.$ammoNote$damagedNote$pressureNote$missileNote")
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

    /**
     * Cycles the quick menu: medikit, cover, glue wall, anti-missile,
     * anti-bomb explosive, exit, recover.
     */
    fun cycleMenu(direction: Int) {
        if (_currentScreen.value != Screen.BATTLE) return
        markExplicitAction()
        val state = _gameState.value
        val nextIndex = state.menuIndex + direction
        _gameState.update {
            it.copy(menuIndex = nextIndex, menuActive = true, pendingPurchaseId = null)
        }
        // Browsing the items is a real navigation, so it is protected.
        armMenuProtection()
        playSound("click")
        speakBattleMenu()
    }

    /**
     * Starts the short window in which Robert's hits are cut down to
     * [DamageTable.MENU_PROTECTION_PERCENT] percent of maximum health. It is
     * armed by every genuine quick-menu step and expires by itself after
     * [DamageTable.MENU_PROTECTION_MS], so it can never become invulnerability.
     */
    private fun armMenuProtection() {
        menuProtectUntilMs = System.currentTimeMillis() + DamageTable.MENU_PROTECTION_MS
    }

    /** True while the menu-protection window is still open. */
    private fun isMenuProtectionActive(): Boolean =
        menuProtectUntilMs > 0L && System.currentTimeMillis() < menuProtectUntilMs

    private fun speakBattleMenu() {
        val item = _gameState.value.menuItem
        when (item) {
            BattleMenuItem.MEDKIT,
            BattleMenuItem.COVER,
            BattleMenuItem.GLUE_WALL,
            BattleMenuItem.ANTI_MISSILE,
            BattleMenuItem.ANTI_BOMB -> {
                val defence = item.defence ?: return
                val count = _inventory.value.count(defence)
                speak("${t("menuNow")}: ${t(defence.nameKey)}. $count.")
            }
            BattleMenuItem.EXIT -> speak("${t("menuNow")}: ${t("exitGame")}.")
            BattleMenuItem.RECOVER -> speak(
                "${t("menuNow")}: ${t("recoverWeapon")}. " +
                    "${Combat.GUN_RECOVERY_COST} ${t("coins")}."
            )
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
            BattleMenuItem.MEDKIT,
            BattleMenuItem.COVER,
            BattleMenuItem.GLUE_WALL,
            BattleMenuItem.ANTI_MISSILE,
            BattleMenuItem.ANTI_BOMB -> {
                val defence = item.defence ?: return
                useDefenceFromMenu(defence)
            }
            BattleMenuItem.EXIT -> exitBattle()
            BattleMenuItem.RECOVER -> {
                // Recovery replaces the old "play" item and costs 100 coins.
                recoverGun()
                resetMenuSelection()
            }
        }
    }

    private fun useDefenceFromMenu(defence: DefenceItem) {
        val inventory = _inventory.value
        val state = _gameState.value
        // Both interceptors only make sense with a projectile in the air.
        val antiMissileIdle = defence == DefenceItem.ANTI_MISSILE && !state.missileInFlight
        val antiBombIdle = defence == DefenceItem.ANTI_BOMB &&
            (!state.bombInFlight || state.bombFromPlayer)
        if (antiMissileIdle || antiBombIdle) {
            if (inventory.count(defence) <= 0 &&
                _stats.value.completedRounds < defence.requiredCompletedRounds
            ) {
                playSound("ammo")
                val text = t(defence.lockedKey)
                toast(text)
                msg(text)
            } else {
                playSound("click")
                msg(if (antiMissileIdle) t("noActiveMissile") else t("noActiveBomb"))
            }
            resetMenuSelection()
            return
        }

        if (inventory.count(defence) > 0) {
            _gameState.update { it.copy(pendingPurchaseId = null) }
            val spoken = useDefence(defence)
            if (spoken != null) msg(spoken)
            resetMenuSelection()
            return
        }

        if (_stats.value.completedRounds < defence.requiredCompletedRounds) {
            _gameState.update { it.copy(pendingPurchaseId = null) }
            playSound("ammo")
            val text = t(defence.lockedKey)
            toast(text)
            msg(text)
            resetMenuSelection()
            return
        }

        val pending = _gameState.value.pendingPurchaseId
        if (pending == defence.id) {
            // Second tap: buy one at the shop price, then use it immediately.
            val purchase = Shop.buyDefence(
                _inventory.value,
                _stats.value.coins,
                defence,
                _stats.value.completedRounds
            )
            if (!purchase.success) {
                _gameState.update { it.copy(pendingPurchaseId = null) }
                val locked = purchase.reasonKey == "antiMissileLocked" ||
                    purchase.reasonKey == defence.lockedKey
                if (locked) {
                    playSound("ammo")
                    val text = t(defence.lockedKey)
                    toast(text)
                    msg(text)
                } else {
                    notEnoughCoins()
                }
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
            resetMenuSelection()
        } else {
            _gameState.update { it.copy(pendingPurchaseId = defence.id, menuActive = true) }
            playSound("ammo")
            val text = "${t("itemMissingPrompt")} ${t(defence.nameKey)}. ${t("itemMissingTail")}"
            msg(text)
        }
    }

    /** Puts the quick menu back on its safe default item. */
    private fun resetMenuSelection() {
        // The navigation is over, so its protection ends with it.
        menuProtectUntilMs = 0L
        _gameState.update {
            it.copy(
                menuIndex = BattleMenuItem.RECOVER_INDEX,
                menuActive = false,
                pendingPurchaseId = null
            )
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
                // The two barriers are mutually exclusive: raising one while
                // the other is already up says which one is already active.
                if (state.coverActive || state.glueWallActive) {
                    if (automatic) return null
                    playSound("click")
                    return t(
                        if (state.coverActive) "coverAlreadyActivated"
                        else "glueWallAlreadyActivated"
                    )
                }
                _gameState.update { it.copy(coverActive = true, coverHits = 0) }
                consumeDefence(item)
                playSound("cover")
                return (if (automatic) t("autoCoverUsed") else t("coverDeployed")) +
                    " ${Combat.COVER_MAX_HITS}."
            }
            DefenceItem.GLUE_WALL -> {
                if (state.glueWallActive || state.coverActive) {
                    if (automatic) return null
                    playSound("click")
                    return t(
                        if (state.glueWallActive) "glueWallAlreadyActivated"
                        else "coverAlreadyActivated"
                    )
                }
                _gameState.update { it.copy(glueWallActive = true, glueWallProgress = 0) }
                consumeDefence(item)
                playSound("glueSound")
                ensureRobertPositioned()
                return if (automatic) t("autoGlueWallUsed") else t("glooWallDeployed")
            }
            DefenceItem.ANTI_MISSILE -> {
                if (!state.missileInFlight) {
                    playSound("click")
                    return t("noActiveMissile")
                }
                missileJob?.cancel()
                missileJob = null
                missileImpactAtMs = 0L
                audioManager.stopSound("missile")
                audioManager.stopSound("enemyWalk")
                _gameState.update {
                    it.copy(
                        missileInFlight = false,
                        missileFromPlayer = false,
                        missileInterceptPlanned = false,
                        robertState = if (it.robertState == "missileFlee") "advance" else it.robertState,
                        robertLateral = 0f
                    )
                }
                consumeDefence(item)
                playSound("explosion")
                planRobertStep(500L)
                return t("antiMissileDestroyed")
            }
            DefenceItem.ANTI_BOMB -> {
                // The anti-bomb explosive is deployed on hearing a bomb: it
                // marks the bomb in the air as destroyed, and the explosion
                // sound still plays when the countdown ends.
                if (!state.bombInFlight || state.bombFromPlayer) {
                    playSound("click")
                    return t("noActiveBomb")
                }
                if (state.bombInterceptedByPlayer) {
                    playSound("click")
                    return t("antiBombAlreadyDeployed")
                }
                _gameState.update { it.copy(bombInterceptedByPlayer = true) }
                consumeDefence(item)
                playSound("click")
                return t("antiBombDeployed")
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
                .joinToString(", ") { owned ->
                    val load = if (owned.missileLauncher) {
                        _inventory.value.missilePowers
                    } else {
                        state.ammo.getOrElse(state.weapons.indexOf(owned.id)) { 0 }
                    }
                    "${t(owned.nameKey)} $load"
                }
            val inv = _inventory.value
            val extensions = Extension.entries
                .filter { inv.extensionOwned(it) }
                .joinToString(", ") {
                    "${t(it.nameKey)} ${if (inv.extensionEnabled(it)) t("enabledState") else t("disabledState")}"
                }
                .ifEmpty { t("noneOwned") }
            val chance = if (state.playerSecondChanceUsed) " ${t("secondChanceUsed")}." else ""
            // The three-finger running speed and the position inside the
            // 100 metre zone belong to the own status too.
            val speedText = "${t("runSpeed")} ${t(state.runSpeedLevel.nameKey)}."
            val positionText = "${t("yourPosition")} ${state.playerX.roundToInt()}, " +
                "${state.playerY.roundToInt()} ${t("meters")}."
            val bombText = if (state.bombInFlight) {
                " ${t("bombDestination")} ${state.playerDistanceFromBomb.roundToInt()} ${t("meters")}."
            } else {
                ""
            }
            speak(
                "${t("yourHealth")} ${state.playerHp}. ${t("ammo")} ${state.activeAmmo}. " +
                    "$weaponState. $speedText $positionText $weapons. " +
                    "${t("medkits")} ${inv.medikits}. ${t("glooWall")} ${inv.glueWalls}. " +
                    "${t("cover")} ${inv.covers}. ${t("antiMissile")} ${inv.antiMissiles}. " +
                    "${t("antiBomb")} ${inv.antiBombs}.$bombText " +
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
            val positionText = "${t("enemyPosition")} ${state.robertX.roundToInt()}, " +
                "${state.robertY.roundToInt()} ${t("meters")}."
            // While a bomb counts down its destination is announced next to
            // the enemy health: the distance from Robert to the blast, and
            // the distance the player still has to cover to be safe.
            val bombText = if (state.bombInFlight) {
                " ${t("bombDestination")}: ${t("enemy")} " +
                    "${state.robertDistanceFromBomb.roundToInt()} ${t("meters")}, " +
                    "${t("you")} ${state.playerDistanceFromBomb.roundToInt()} ${t("meters")}."
            } else {
                ""
            }
            speak(
                "${t("enemyHealth")} ${state.robertHp}. ${t("distance")} " +
                    "${state.distance.roundToInt()} ${t("meters")}. $positionText $weaponText." +
                    "$coverText$glueText$bombText"
            )
        }
    }

    fun exitBattle() {
        cancelTimerSelection()
        stopTimers()
        audioManager.stopFootsteps()
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
     * Ends a battle: coins are awarded, five wins complete a level, five
     * levels complete a round, the fifth round wins the match, and the result
     * screen is locked for four seconds.
     *
     * A completed level pays one of the five random reward bundles (never
     * more than one glue wall) with level-complete.mp3, and a completed round
     * always pays two glue walls, two medikits and three covers with
     * round-complete.mp3.
     */
    private fun endGame(won: Boolean) {
        stopTimers()
        audioManager.stopFootsteps()

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
        var reward = RewardBundle(emptyMap())
        if (won) {
            val promotion = Progression.applyWin(updatedStats)
            updatedStats = promotion.stats
            leveled = promotion.leveledUp
            roundCompleted = promotion.roundCompleted
            matchWon = promotion.matchWon
            // The reward of the promotion: the fixed round bundle when a
            // round was completed, otherwise one random level bundle.
            reward = Rewards.forPromotion(
                levelCompleted = leveled,
                roundCompleted = roundCompleted,
                random = Random
            )
            if (!reward.isEmpty) {
                val granted = Shop.grantBundle(_inventory.value, reward)
                _inventory.value = granted
                prefs.saveInventory(granted)
            }
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
            level = updatedStats.level,
            rewardText = if (reward.isEmpty) "" else rewardSpeech(reward),
            rewardMedkits = reward.medikits,
            rewardCovers = reward.covers,
            rewardGlueWalls = reward.glueWalls
        )

        _gameState.update { it.copy(matchWon = matchWon) }
        _currentScreen.value = Screen.RESULT
        updateAudioForScreen(Screen.RESULT)
        playSound(if (won) "win" else "lose")
        // The progression stings: round-complete after a completed round (it
        // is the longer one and covers the match win too), level-complete
        // after a completed level.
        if (roundCompleted || matchWon) {
            playSound("roundComplete", volume = ROUND_COMPLETE_VOLUME)
        } else if (leveled) {
            playSound("levelComplete", volume = LEVEL_COMPLETE_VOLUME)
        }
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
        if (!reward.isEmpty) {
            speech += " ${t("rewardReceived")} ${rewardSpeech(reward)}."
        }
        speech += " ${t("resultWait")}"
        speak(speech)
    }

    /** A reward bundle as a spoken list, in the language of the player. */
    private fun rewardSpeech(bundle: RewardBundle): String =
        bundle.parts().joinToString(", ") { part ->
            "${t(part.first.pluralKey)} ${part.second}"
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
        audioManager.stopFootsteps()
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
            // Three fingers: the running speed cycles slow, normal, fast.
            GameAction.SPEED_FASTER -> cycleRunSpeed(1)
            GameAction.SPEED_SLOWER -> cycleRunSpeed(-1)
            GameAction.NONE -> {}
        }
    }

    fun handleCenterTap() {
        // While the timer bomb picker is open a tap locks the highlighted
        // duration in and throws the bomb.
        if (_gameState.value.timerSelectActive) {
            confirmTimerSelection()
            return
        }
        // The center third is the menu activation surface. Recover is the
        // safe default, so a tap is valid even before the first menu swipe.
        // Swipe and tap dispatch are mutually exclusive, so no button-style
        // debounce is needed here.
        activateMenuItem()
    }

    fun handleTap() {
        handleCenterTap()
    }

    private fun markExplicitAction() {
        lastExplicitActionAt = System.currentTimeMillis()
    }

    private fun stopTimers() {
        playerMoveJob?.cancel()
        playerMoveJob = null
        stepJob?.cancel()
        stepJob = null
        counterJob?.cancel()
        counterJob = null
        robertFireJob?.cancel()
        robertFireJob = null
        missileJob?.cancel()
        missileJob = null
        bombJob?.cancel()
        bombJob = null
        autoFireJob?.cancel()
        autoFireJob = null
        endingJob?.cancel()
        endingJob = null
        // Stop a missile or a bomb still in the air (their countdown sounds
        // included), but never cut an explosion that has just impacted:
        // screen changes stop that separately.
        audioManager.stopBattleProjectiles()
        audioManager.stopFootsteps()
    }

    override fun onCleared() {
        super.onCleared()
        stopTimers()
        resultJob?.cancel()
        audioManager.release()
        ttsManager.shutdown()
    }
}

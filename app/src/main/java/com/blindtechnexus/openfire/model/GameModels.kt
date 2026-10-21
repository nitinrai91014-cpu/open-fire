package com.blindtechnexus.openfire.model

enum class Screen {
    WELCOME,
    MENU,
    GUIDE,
    PROFILE,
    ABOUT,
    CHANGELOG,
    SETTINGS,
    READER,
    JUMP,
    PARA,
    LAND,
    BATTLE,
    RESULT
}

enum class ControlMode {
    BUTTONS,
    GESTURES,
    BOTH
}

enum class SoundTheme(val title: String) {
    TACTICAL("Tactical Royale"),
    CYBER("Sci-Fi Cyber Warfare"),
    STEALTH("Night Ops / Stealth")
}

data class Gun(
    val nameKey: String,
    val dmg: Int,
    val mag: Int,
    val sound: String
)

// Strict rule: all three guns (pistol, assault rifle, shotgun) hold exactly
// six bullets. One bullet is consumed per shot; reload is required only after
// all six bullets of the active gun have been fired.
val DEFAULT_GUNS = listOf(
    Gun("gunPistol", 25, 6, "pistol"),
    Gun("gunRifle", 35, 6, "fire"),
    Gun("gunShotgun", 50, 6, "shotgun")
)

fun getHpForLevel(level: Int): Int {
    return when {
        level <= 10 -> 200
        level <= 20 -> 300
        level <= 25 -> 500
        else -> 800
    }
}

data class GameSettings(
    val name: String = "",
    val lang: String = "en", // "en", "hi", "ne"
    val mode: ControlMode = ControlMode.BUTTONS,
    val ttsEnginePackage: String = "",
    val voiceName: String = "",
    val ttsRate: Float = 1.0f,
    val ttsVolume: Float = 1.0f,
    val musicVolume: Float = 0.5f,
    val sfxVolume: Float = 1.0f,
    val playerMaxHp: Int = 200,
    val devilMaxHp: Int = 200,
    val soundTheme: SoundTheme = SoundTheme.TACTICAL,
    val hapticsEnabled: Boolean = true
)

data class GameStats(
    val games: Int = 0,
    val wins: Int = 0,
    val kills: Int = 0,
    val coins: Int = 100,
    val round: Int = 1,
    val level: Int = 1,
    /** Wins collected inside the current level (0..9). 10 wins promote a level. */
    val levelWins: Int = 0
) {
    /** Wins still required to reach the next level (always 1..10). */
    val needWinsForNext: Int
        get() = (Progression.WINS_PER_LEVEL - levelWins)
            .coerceIn(1, Progression.WINS_PER_LEVEL)

    companion object {
        /**
         * A completely wiped profile: every counter is zero and the level and
         * round are back to 1, as required by "Clear My Stats".
         */
        fun cleared(): GameStats = GameStats(
            games = 0,
            wins = 0,
            kills = 0,
            coins = 0,
            round = 1,
            level = 1,
            levelWins = 0
        )
    }
}

data class GameState(
    val playerHp: Int = 200,
    val playerMaxHp: Int = 200,
    val devilHp: Int = 200,
    val devilMaxHp: Int = 200,
    val gunIndex: Int = 0,
    val ammo: List<Int> = listOf(6, 6, 6),
    val medkits: Int = 3,
    val inCover: Boolean = false,
    val distance: Float = Combat.MAX_RANGE,
    val devilShots: Int = 0,
    val devilHits: Int = 0,
    val devilDamage: Int = 0,
    val playerShots: Int = 0,
    val playerHits: Int = 0,
    val devilCoverHp: Int = 0,
    val devilCoverMax: Int = 4,
    val devilCoversLeft: Int = 1,
    val devilMedkits: Int = 1,
    val devilState: String = "advance", // "advance", "retreat" (fleeing), "flank"
    val devilHitsTaken: Int = 0,
    val devilReactAt: Int = 4,
    val devilRetreats: Int = 0,
    val log: String = "",

    /** One second chance per fight: health restored to half the maximum. */
    val playerSecondChanceUsed: Boolean = false,
    val devilSecondChanceUsed: Boolean = false,
    /** Gloo wall, cover and medkit the devil activates after its second chance. */
    val devilItemsLeft: Int = 0,
    /** True while the devil still has a high-speed flee available this life. */
    val devilFleeArmed: Boolean = true,
    /** Which of the three guns are currently broken and need Recovery. */
    val damagedGuns: List<Boolean> = listOf(false, false, false),
    /** Consecutive point-blank (90%-100%) hits the player has taken. */
    val consecutiveHeavyHits: Int = 0
) {
    val activeGunDamaged: Boolean
        get() = damagedGuns.getOrElse(gunIndex) { false }

    val anyGunDamaged: Boolean
        get() = damagedGuns.any { it }

    val devilFleeing: Boolean
        get() = devilState == "retreat"
}

data class ResultData(
    val won: Boolean = false,
    val coins: Int = 0,
    val hits: Int = 0,
    val shots: Int = 0,
    val devilShots: Int = 0,
    val hp: Int = 0,
    val levelUp: Boolean = false,
    val roundComplete: Boolean = false,
    val round: Int = 1,
    val level: Int = 1
)

data class TtsEngineInfo(
    val name: String,
    val label: String
)

data class TtsVoiceInfo(
    val name: String,
    val displayName: String,
    val language: String
)

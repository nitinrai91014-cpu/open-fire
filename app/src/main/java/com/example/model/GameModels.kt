package com.example.model

enum class Screen {
    WELCOME,
    MENU,
    GUIDE,
    PROFILE,
    ABOUT,
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

val DEFAULT_GUNS = listOf(
    Gun("gunRifle", 34, 6, "fire"),
    Gun("gunShotgun", 50, 4, "shotgun"),
    Gun("gunPistol", 24, 10, "pistol")
)

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
    // Strength / balance customization
    val playerMaxHp: Int = 200,
    val playerDamageMult: Float = 1.0f,
    val devilMaxHp: Int = 200,
    val devilDamageMult: Float = 1.0f,
    val soundTheme: SoundTheme = SoundTheme.TACTICAL,
    val hapticsEnabled: Boolean = true
)

data class GameStats(
    val games: Int = 0,
    val wins: Int = 0,
    val kills: Int = 0,
    val coins: Int = 100,
    val level: Int = (wins / 10).coerceAtLeast(0)
) {
    val needWinsForNext: Int
        get() = 10 - (wins % 10)
}

data class GameState(
    val playerHp: Int = 200,
    val playerMaxHp: Int = 200,
    val devilHp: Int = 200,
    val devilMaxHp: Int = 200,
    val gunIndex: Int = 0,
    val ammo: List<Int> = listOf(6, 4, 10),
    val medkits: Int = 3,
    val inCover: Boolean = false,
    val distance: Float = 100f,
    val devilShots: Int = 0,
    val devilHits: Int = 0,
    val devilDamage: Int = 0,
    val playerShots: Int = 0,
    val playerHits: Int = 0,
    val devilCoverHp: Int = 0,
    val devilCoverMax: Int = 6,
    val devilCoversLeft: Int = 1,
    val devilMedkits: Int = 1,
    val devilState: String = "advance", // "advance", "retreat", "flank"
    val devilHitsTaken: Int = 0,
    val devilReactAt: Int = 4,
    val devilRetreats: Int = 0,
    val log: String = "",
    val hitStreak: Int = 0
)

data class ResultData(
    val won: Boolean = false,
    val coins: Int = 0,
    val hits: Int = 0,
    val shots: Int = 0,
    val devilShots: Int = 0,
    val hp: Int = 0,
    val levelUp: Boolean = false,
    val level: Int = 0
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

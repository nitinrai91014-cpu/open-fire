package com.blindtechnexus.openfire.model

enum class Screen {
    WELCOME,
    MENU,
    SHOP,
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

enum class SoundTheme(val title: String) {
    TACTICAL("Tactical Royale"),
    CYBER("Sci-Fi Cyber Warfare"),
    STEALTH("Night Ops / Stealth")
}

/**
 * The five items of the in-game quick menu, in the order they are cycled by a
 * two-finger swipe up or down: medikit, cover, glue wall, exit and recover.
 *
 * The old "play" item was replaced by Recovery, which repairs every damaged
 * weapon for [Combat.GUN_RECOVERY_COST] coins.
 */
enum class BattleMenuItem(
    val nameKey: String,
    val defence: DefenceItem?
) {
    MEDKIT("medkit", DefenceItem.MEDKIT),
    COVER("cover", DefenceItem.COVER),
    GLUE_WALL("glooWall", DefenceItem.GLUE_WALL),
    EXIT("exitGame", null),
    RECOVER("recoverWeapon", null);

    companion object {
        /**
         * The menu starts on Recover. It is the safe default: while every
         * weapon works it only announces that nothing is damaged and never
         * spends a coin.
         */
        val RECOVER_INDEX: Int = RECOVER.ordinal

        fun at(index: Int): BattleMenuItem {
            val size = BattleMenuItem.entries.size
            return BattleMenuItem.entries[((index % size) + size) % size]
        }
    }
}

data class GameSettings(
    val name: String = "",
    val lang: String = "en", // "en", "hi", "ne"
    val ttsEnginePackage: String = "",
    val voiceName: String = "",
    val ttsRate: Float = 1.0f,
    val ttsVolume: Float = 1.0f,
    val musicVolume: Float = 0.5f,
    val sfxVolume: Float = 1.0f,
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
    val levelWins: Int = 0,
    /** Lifetime completed rounds; unlike [round], this never resets between matches. */
    val completedRounds: Int = 0
) {
    /** Wins still required to reach the next level (always 1..10). */
    val needWinsForNext: Int
        get() = (Progression.WINS_PER_LEVEL - levelWins)
            .coerceIn(1, Progression.WINS_PER_LEVEL)

    companion object {
        /**
         * A completely wiped profile: every counter is zero, the inventory is
         * empty and level and round are back to 1.
         */
        fun cleared(): GameStats = GameStats(
            games = 0,
            wins = 0,
            kills = 0,
            coins = 0,
            round = 1,
            level = 1,
            levelWins = 0,
            completedRounds = 0
        )
    }
}

/**
 * Complete, immutable snapshot of a battle.
 *
 * Health, weapons, ammo and defences of both fighters live here so the
 * interface can render the whole battle from one state object.
 */
data class GameState(
    val playerHp: Int = 200,
    val playerMaxHp: Int = 200,
    val robertHp: Int = 200,
    val robertMaxHp: Int = 200,
    val round: Int = 1,
    val level: Int = 1,

    /** Weapon ids owned for this battle, in switch order. */
    val weapons: List<String> = Weapon.defaults().map { it.id },
    val weaponIndex: Int = 0,
    val ammo: List<Int> = Weapon.defaults().map { it.magazine },
    /** Which weapons are currently damaged and need Recovery. */
    val damaged: List<Boolean> = Weapon.defaults().map { false },

    val distance: Float = Combat.MAX_RANGE,

    /** A cover blocks 11 hits; the 12th breaks it. */
    val coverActive: Boolean = false,
    val coverHits: Int = 0,
    /** A glue wall stops everything except three consecutive sniper hits. */
    val glueWallActive: Boolean = false,
    val glueWallProgress: Int = 0,

    val robertCoverActive: Boolean = false,
    val robertCoverHits: Int = 0,
    val robertGlueWallActive: Boolean = false,
    val robertGlueWallProgress: Int = 0,
    val robertWeapons: List<String> = emptyList(),
    val robertWeaponIndex: Int = 0,
    val robertAmmo: List<Int> = emptyList(),
    val robertDamaged: List<Boolean> = emptyList(),
    val robertMedkits: Int = 0,
    val robertCovers: Int = 0,
    val robertGlueWalls: Int = 0,
    /** True when the Robert may spend its next defence. */
    val robertDefenceArmed: Boolean = true,
    val robertConsecutiveHits: Int = 0,
    /** Shots Robert has fired during the current exchange of hits. */
    val robertExchangeShots: Int = 0,
    /**
     * Sideways offset of Robert in metres for positional audio. Zero means
     * directly head-on (the normal fight). Only a flank or a dodge moves
     * him sideways; running straight never pans him left or right.
     */
    val robertLateral: Float = 0f,
    val robertState: String = "advance", // "advance", "retreat", "flank", "missileFlee"

    /** True while a guided missile is in the air (either fighter's). */
    val missileInFlight: Boolean = false,
    /** True when the missile in the air was launched by the player. */
    val missileFromPlayer: Boolean = false,
    /** True when Robert will fire his anti-missile interceptor at impact. */
    val missileInterceptPlanned: Boolean = false,
    /** Missile powers Robert may still spend this game (rounds 4 and 5). */
    val robertMissilePowers: Int = 0,
    /** Anti-missile interceptors Robert may still fire this game. */
    val robertAntiMissiles: Int = 0,
    /**
     * The band Robert randomly prefers to fire from ([FireRange.id]):
     * 40%-70%, 90%-100%, or the 0%-40% deception band.
     */
    val robertFireRange: String = FireRange.MID.id,
    /** Deception shots still to fire from the 0%-40% band (one or two). */
    val robertDeceptionShots: Int = 0,
    val robertHits: Int = 0,
    val robertShots: Int = 0,
    val robertDamage: Int = 0,
    val playerShots: Int = 0,
    val playerHits: Int = 0,
    val consecutiveHeavyHits: Int = 0,
    /** Consecutive point-blank hits the player has landed on Robert. */
    val playerHeavyHits: Int = 0,

    /** One second chance per fight for the player. */
    val playerSecondChanceUsed: Boolean = false,

    /** Index of the highlighted quick-menu item ([BattleMenuItem]). */
    val menuIndex: Int = BattleMenuItem.RECOVER_INDEX,
    /** True while the player is navigating the menu: hits then cost 2 HP. */
    val menuActive: Boolean = false,
    /** Item waiting for the second confirming tap ("Tap again to buy"). */
    val pendingPurchaseId: String? = null,

    val log: String = "",
    val matchWon: Boolean = false
) {
    val activeWeapon: Weapon
        get() = Weapon.fromId(weapons.getOrNull(weaponIndex)) ?: Weapon.SHORT_GUN

    val activeWeaponDamaged: Boolean
        get() = damaged.getOrElse(weaponIndex) { false }

    val activeAmmo: Int
        get() = ammo.getOrElse(weaponIndex) { 0 }

    val anyWeaponDamaged: Boolean
        get() = damaged.any { it }

    val menuItem: BattleMenuItem
        get() = BattleMenuItem.at(menuIndex)

    val robertWeapon: Weapon?
        get() = Weapon.fromId(robertWeapons.getOrNull(robertWeaponIndex))

    val robertAmmoNow: Int
        get() = robertAmmo.getOrElse(robertWeaponIndex) { 0 }

    val robertFleeing: Boolean
        get() = robertState == "retreat"

    /** The band Robert picked for himself, ignoring forced point blank. */
    val robertRange: FireRange
        get() = FireRange.fromId(robertFireRange)
}

data class ResultData(
    val won: Boolean = false,
    val coins: Int = 0,
    val hits: Int = 0,
    val shots: Int = 0,
    val robertShots: Int = 0,
    val hp: Int = 0,
    val levelUp: Boolean = false,
    val roundComplete: Boolean = false,
    val matchWon: Boolean = false,
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

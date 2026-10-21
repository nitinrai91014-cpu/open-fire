package com.blindtechnexus.openfire.data

import android.content.Context
import android.content.SharedPreferences
import com.blindtechnexus.openfire.model.ControlMode
import com.blindtechnexus.openfire.model.GameSettings
import com.blindtechnexus.openfire.model.GameStats
import com.blindtechnexus.openfire.model.Progression
import com.blindtechnexus.openfire.model.SoundTheme

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("open_fire_prefs", Context.MODE_PRIVATE)

    fun loadSettings(): GameSettings {
        val modeStr = prefs.getString("of_mode", "BUTTONS") ?: "BUTTONS"
        val mode = try {
            ControlMode.valueOf(modeStr)
        } catch (_: Exception) {
            ControlMode.BUTTONS
        }
        val themeStr = prefs.getString("of_sound_theme", "TACTICAL") ?: "TACTICAL"
        val soundTheme = try {
            SoundTheme.valueOf(themeStr)
        } catch (_: Exception) {
            SoundTheme.TACTICAL
        }

        return GameSettings(
            name = prefs.getString("of_name", "") ?: "",
            lang = prefs.getString("of_lang", "en") ?: "en",
            mode = mode,
            ttsEnginePackage = prefs.getString("of_tts_engine", "") ?: "",
            voiceName = prefs.getString("of_voice", "") ?: "",
            ttsRate = prefs.getFloat("of_rate", 1.0f),
            ttsVolume = prefs.getFloat("of_ttsvol", 1.0f),
            musicVolume = prefs.getFloat("of_musicvol", 0.5f),
            sfxVolume = prefs.getFloat("of_sfxvol", 1.0f),
            playerMaxHp = prefs.getInt("of_player_max_hp", 200),
            devilMaxHp = prefs.getInt("of_devil_max_hp", 200),
            soundTheme = soundTheme,
            hapticsEnabled = prefs.getBoolean("of_haptics", true)
        )
    }

    fun saveSettings(s: GameSettings) {
        prefs.edit()
            .putString("of_name", s.name)
            .putString("of_lang", s.lang)
            .putString("of_mode", s.mode.name)
            .putString("of_tts_engine", s.ttsEnginePackage)
            .putString("of_voice", s.voiceName)
            .putFloat("of_rate", s.ttsRate)
            .putFloat("of_ttsvol", s.ttsVolume)
            .putFloat("of_musicvol", s.musicVolume)
            .putFloat("of_sfxvol", s.sfxVolume)
            .putInt("of_player_max_hp", s.playerMaxHp)
            .putInt("of_devil_max_hp", s.devilMaxHp)
            .putString("of_sound_theme", s.soundTheme.name)
            .putBoolean("of_haptics", s.hapticsEnabled)
            .apply()
    }

    fun loadStats(): GameStats {
        val wins = prefs.getInt("of_wins", 0)
        val savedLevel = prefs.getInt("of_level", 1)
        val round = prefs.getInt("of_round", 1)
        // Keep level in 1..30 range
        val level = savedLevel.coerceIn(1, Progression.LEVELS_PER_ROUND)
        // Saves written before the explicit per-level counter existed are
        // migrated from the lifetime win count, so the 10-wins ladder keeps
        // working without resetting anybody's progress.
        val migratedLevelWins = wins % Progression.WINS_PER_LEVEL
        val savedLevelWins = if (prefs.contains("of_level_wins")) {
            prefs.getInt("of_level_wins", migratedLevelWins)
        } else {
            migratedLevelWins
        }
        val levelWins = savedLevelWins.coerceIn(0, Progression.WINS_PER_LEVEL - 1)
        return Progression.normalize(
            GameStats(
                games = prefs.getInt("of_games", 0),
                wins = wins,
                kills = prefs.getInt("of_kills", 0),
                coins = prefs.getInt("of_coins", 100),
                round = round.coerceAtLeast(1),
                level = level,
                levelWins = levelWins
            )
        )
    }

    fun saveStats(s: GameStats) {
        val normalized = Progression.normalize(s)
        prefs.edit()
            .putInt("of_games", normalized.games)
            .putInt("of_wins", normalized.wins)
            .putInt("of_kills", normalized.kills)
            .putInt("of_coins", normalized.coins)
            .putInt("of_round", normalized.round)
            .putInt("of_level", normalized.level)
            .putInt("of_level_wins", normalized.levelWins)
            .apply()
    }

    /**
     * Erases every stored statistic and writes the cleared profile back.
     * Only gameplay data is touched: the player's name and audio/TTS
     * preferences are kept so the app remains usable.
     */
    fun clearStats(cleared: GameStats = GameStats.cleared()) {
        prefs.edit()
            .remove("of_games")
            .remove("of_wins")
            .remove("of_kills")
            .remove("of_coins")
            .remove("of_round")
            .remove("of_level")
            .remove("of_level_wins")
            .apply()
        saveStats(cleared)
    }
}

package com.blindtechnexus.openfire.data

import android.content.Context
import android.content.SharedPreferences
import com.blindtechnexus.openfire.model.ControlMode
import com.blindtechnexus.openfire.model.GameSettings
import com.blindtechnexus.openfire.model.GameStats
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
            playerDamageMult = prefs.getFloat("of_player_dmg_mult", 1.0f),
            devilMaxHp = prefs.getInt("of_devil_max_hp", 200),
            devilDamageMult = prefs.getFloat("of_devil_dmg_mult", 1.0f),
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
            .putFloat("of_player_dmg_mult", s.playerDamageMult)
            .putInt("of_devil_max_hp", s.devilMaxHp)
            .putFloat("of_devil_dmg_mult", s.devilDamageMult)
            .putString("of_sound_theme", s.soundTheme.name)
            .putBoolean("of_haptics", s.hapticsEnabled)
            .apply()
    }

    fun loadStats(): GameStats {
        val wins = prefs.getInt("of_wins", 0)
        val level = (wins / 10).coerceAtLeast(0)
        return GameStats(
            games = prefs.getInt("of_games", 0),
            wins = wins,
            kills = prefs.getInt("of_kills", 0),
            coins = prefs.getInt("of_coins", 100),
            level = level
        )
    }

    fun saveStats(s: GameStats) {
        prefs.edit()
            .putInt("of_games", s.games)
            .putInt("of_wins", s.wins)
            .putInt("of_kills", s.kills)
            .putInt("of_coins", s.coins)
            .putInt("of_level", s.level)
            .apply()
    }
}

package com.blindtechnexus.openfire

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.blindtechnexus.openfire.audio.AudioManager
import com.blindtechnexus.openfire.data.PreferencesManager
import com.blindtechnexus.openfire.model.GameSettings
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The music setting must survive screen changes. Starting (or re-starting) the
 * background track is a playback action only — it may never overwrite the
 * stored level with the screen-scaled value.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AudioVolumePersistenceTest {

    @Test
    fun startingMusicNeverChangesTheStoredMusicVolume() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val audio = AudioManager(context)

        audio.musicVolume = 0.8f
        assertEquals(0.8f, audio.musicVolume, 0.0001f)

        audio.startMusic()
        assertEquals(0.8f, audio.musicVolume, 0.0001f)

        // Navigating back to the menu / into a new game starts the track again.
        audio.startMusic()
        audio.startMusic()
        assertEquals(0.8f, audio.musicVolume, 0.0001f)

        audio.musicVolume = 0.3f
        assertEquals(0.3f, audio.musicVolume, 0.0001f)
        audio.startMusic()
        assertEquals(0.3f, audio.musicVolume, 0.0001f)

        audio.stopMusic()
        audio.release()
    }

    @Test
    fun savedVolumeSettingsSurviveANewPreferencesInstance() {
        // Simulates Settings -> Save -> leave the app -> start another game:
        // a brand new PreferencesManager must read back exactly the saved
        // music, SFX and TTS levels.
        val context = ApplicationProvider.getApplicationContext<Context>()
        val saved = GameSettings(musicVolume = 0.8f, sfxVolume = 0.35f, ttsVolume = 0.6f)
        PreferencesManager(context).saveSettings(saved)

        val loaded = PreferencesManager(context).loadSettings()
        assertEquals(0.8f, loaded.musicVolume, 0.0001f)
        assertEquals(0.35f, loaded.sfxVolume, 0.0001f)
        assertEquals(0.6f, loaded.ttsVolume, 0.0001f)

        val audio = AudioManager(context)
        audio.musicVolume = loaded.musicVolume
        assertEquals(0.8f, audio.musicVolume, 0.0001f)
        audio.startMusic()
        assertEquals(0.8f, audio.musicVolume, 0.0001f)
        audio.release()
    }

    @Test
    fun storedVolumesAreClampedNotScaled() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val audio = AudioManager(context)

        audio.musicVolume = 1.7f
        assertEquals(1.0f, audio.musicVolume, 0.0001f)

        audio.sfxVolume = -0.4f
        assertEquals(0.0f, audio.sfxVolume, 0.0001f)

        audio.release()
    }
}

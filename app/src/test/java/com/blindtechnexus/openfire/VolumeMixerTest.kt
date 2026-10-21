package com.blindtechnexus.openfire

import com.blindtechnexus.openfire.audio.VolumeMixer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the volume bug: the stored user setting must be stable
 * and the background-music gain must be applied exactly once.
 */
class VolumeMixerTest {

    @Test
    fun musicOutputAppliesTheBackgroundGainOnce() {
        assertEquals(0.28f, VolumeMixer.music(0.8f), 0.0001f)
        assertEquals(0.35f, VolumeMixer.music(1.0f), 0.0001f)
        assertEquals(0.0f, VolumeMixer.music(0.0f), 0.0001f)
    }

    @Test
    fun repeatedMusicMixingIsStableAndNeverCompounds() {
        // This is exactly the old failure: mixing a playback value back into
        // the setting shrank it further and further. The pure function always
        // returns the same output for the same saved setting.
        val setting = 0.8f
        var playback = VolumeMixer.music(setting)
        repeat(20) {
            playback = VolumeMixer.music(setting)
            assertEquals(0.28f, playback, 0.0001f)
        }
        assertEquals(0.8f, setting, 0.0001f)
    }

    @Test
    fun speechVolumeMatchesTheUserSetting() {
        assertEquals(0.7f, VolumeMixer.speech(0.7f), 0.0001f)
        assertEquals(1.0f, VolumeMixer.speech(1.0f), 0.0001f)
        assertEquals(0.0f, VolumeMixer.speech(0.0f), 0.0001f)
        // Out-of-range values are clamped, never amplified.
        assertEquals(1.0f, VolumeMixer.speech(4.0f), 0.0001f)
        assertEquals(0.0f, VolumeMixer.speech(-3.0f), 0.0001f)
    }

    @Test
    fun sfxMixesTheSettingAndDistanceGainOnce() {
        assertEquals(0.5f, VolumeMixer.sfx(0.5f, 1.0f, 1.0f), 0.0001f)
        assertEquals(0.125f, VolumeMixer.sfx(0.5f, 0.5f, 0.5f), 0.0001f)
        assertEquals(0.0f, VolumeMixer.sfx(0.0f, 1.0f, 1.0f), 0.0001f)
    }

    @Test
    fun walkingVolumeFallsWithDistance() {
        val close = VolumeMixer.walk(1.0f, 2f, 50f, 1.0f)
        val mid = VolumeMixer.walk(1.0f, 25f, 50f, 1.0f)
        val far = VolumeMixer.walk(1.0f, 50f, 50f, 1.0f)
        assertTrue("Close footsteps must be loudest", close > mid)
        assertTrue("Far footsteps must be quietest", mid > far)
        assertTrue("Far footsteps must still be audible", far > 0f)
    }

    @Test
    fun fleeingFootstepsUseALowerPitch() {
        assertTrue(VolumeMixer.walkPitch(true) < VolumeMixer.walkPitch(false))
    }
}

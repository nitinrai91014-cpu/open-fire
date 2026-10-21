package com.blindtechnexus.openfire.audio

/**
 * Pure volume arithmetic shared by every audio channel.
 *
 * Keeping this logic in one small, testable object is what prevents the
 * "music volume silently reverts to the old lower level" bug: the user's
 * setting is never overwritten by a screen-scaled playback value.
 *
 *  - [music]  applies the constant background gain to the user's music setting.
 *  - [speech] returns the user's TTS setting unchanged.
 *  - [sfx]    applies the user's SFX setting and the 3D distance gain to the
 *             sound's own base volume.
 */
object VolumeMixer {
    /** Background music always sits under the sound effects. */
    const val MUSIC_BACKGROUND_GAIN = 0.35f

    fun clamp(value: Float): Float =
        if (value.isNaN()) 0f else value.coerceIn(0f, 1f)

    /** Effective music player volume for a stored 0..1 music setting. */
    fun music(setting: Float): Float = clamp(clamp(setting) * MUSIC_BACKGROUND_GAIN)

    /** Effective speech volume for a stored 0..1 TTS setting. */
    fun speech(setting: Float): Float = clamp(setting)

    /** Effective SFX volume for a base volume and a 0..1 distance gain. */
    fun sfx(setting: Float, base: Float, distanceGain: Float): Float =
        clamp(clamp(base) * clamp(setting) * clamp(distanceGain))

    /**
     * Walking-loop volume from the current distance. The footsteps are almost
     * inaudible at the far edge of the arena and swell as the devil closes in,
     * then fade away again when it flees.
     */
    fun walk(setting: Float, distance: Float, maxRange: Float, distanceGain: Float): Float {
        val proximity = (1f - clamp(distance / maxRange.coerceAtLeast(1f))).coerceIn(0f, 1f)
        val base = (0.05f + proximity * proximity * 0.95f).coerceIn(0.05f, 1f)
        return sfx(setting, base, distanceGain)
    }

    /** Doppler-style pitch: footsteps rise when closing in and drop when fleeing. */
    fun walkPitch(fleeing: Boolean): Float = if (fleeing) 0.85f else 1.06f
}

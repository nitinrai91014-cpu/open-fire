package com.blindtechnexus.openfire.model

/**
 * The real, measured length of every sound that drives a game timer.
 *
 * These values are read straight from the shipped assets in
 * `app/src/main/assets/sounds`, so a flight, a countdown or an explosion
 * always ends exactly when its sound ends instead of guessing.
 */
object SoundDurations {
    /** missile-sound.mp3 */
    const val MISSILE_MS = 14_800L

    /** explosion-sound.mp3: the tail every blast waits for. */
    const val EXPLOSION_MS = 6_100L

    /** atom-bom-sound.mp3 */
    const val ATOM_BOMB_MS = 7_100L

    /** grenade-sound.mp3 */
    const val GRENADE_MS = 9_100L

    /** timer-sound.mp3 (looped until the chosen duration elapses) */
    const val TIMER_LOOP_MS = 4_300L

    /** mp-40-gun.mp3 (looped during a continuous burst) */
    const val MP40_MS = 1_300L

    /** machine-gun.mp3 (looped during a continuous burst) */
    const val MACHINE_GUN_MS = 1_600L

    /** AWM-sound.mp3 */
    const val AWM_MS = 2_600L

    /** level-complete.mp3 */
    const val LEVEL_COMPLETE_MS = 2_600L

    /** round-complete.mp3 */
    const val ROUND_COMPLETE_MS = 28_200L

    /** glue-wall-crack.mp3, reused as the boundary wall impact */
    const val WALL_MS = 2_800L

    /** walk.mp3 */
    const val WALK_MS = 9_800L
}

package com.blindtechnexus.openfire.model

/**
 * Every action a gesture can trigger. Keeping the mapping pure makes it
 * unit-testable and guarantees one consistent gesture contract:
 *
 *  - One finger:  up fire, down reload,
 *                 right next weapon, left previous weapon.
 *  - Two fingers: up / down move the quick menu selection, right own status,
 *                 left enemy status.
 *  - Three or more fingers: ignored.
 *  - A single tap activates the highlighted quick-menu item.
 */
enum class GameAction {
    NONE,
    CONTINUE,
    BACK_TO_MENU,
    JUMP,
    OPEN_PARA,
    LAND,
    FIRE,
    RELOAD,
    NEXT_WEAPON,
    PREV_WEAPON,
    MENU_NEXT,
    MENU_PREV,
    ACTIVATE_MENU,
    STATUS_OWN,
    STATUS_ENEMY,
    EXIT_BATTLE,
    PLAY_AGAIN
}

object GestureRouter {
    /** Routes the supported one- and two-finger battle gestures. */
    fun route(screen: Screen, direction: String, fingers: Int): GameAction = when (screen) {
        Screen.READER -> when (direction) {
            "up" -> GameAction.CONTINUE
            "down" -> GameAction.BACK_TO_MENU
            else -> GameAction.NONE
        }
        Screen.JUMP -> when (direction) {
            "up" -> GameAction.JUMP
            "down" -> GameAction.BACK_TO_MENU
            else -> GameAction.NONE
        }
        Screen.PARA -> when (direction) {
            "up" -> GameAction.OPEN_PARA
            "down" -> GameAction.BACK_TO_MENU
            else -> GameAction.NONE
        }
        Screen.LAND -> when (direction) {
            "up" -> GameAction.LAND
            "down" -> GameAction.BACK_TO_MENU
            else -> GameAction.NONE
        }
        Screen.BATTLE -> when {
            fingers <= 0 -> GameAction.NONE
            fingers >= 3 -> GameAction.NONE
            fingers == 2 -> when (direction) {
                "up" -> GameAction.MENU_NEXT
                "down" -> GameAction.MENU_PREV
                "right" -> GameAction.STATUS_OWN
                "left" -> GameAction.STATUS_ENEMY
                else -> GameAction.NONE
            }
            else -> when (direction) {
                "up" -> GameAction.FIRE
                "down" -> GameAction.RELOAD
                "right" -> GameAction.NEXT_WEAPON
                "left" -> GameAction.PREV_WEAPON
                else -> GameAction.NONE
            }
        }
        Screen.RESULT -> when (direction) {
            "up" -> GameAction.PLAY_AGAIN
            "down" -> GameAction.BACK_TO_MENU
            else -> GameAction.NONE
        }
        else -> GameAction.NONE
    }

    /** A single tap only means something inside a battle. */
    fun tap(screen: Screen): GameAction =
        if (screen == Screen.BATTLE) GameAction.ACTIVATE_MENU else GameAction.NONE
}

/**
 * The four running directions of the 100 m battle zone.
 *
 * The top and bottom portions of the battle screen run towards and away from
 * the enemy; the center portion is split in half, so holding its left half
 * runs left and holding its right half runs right.
 */
enum class MoveDirection(val id: String) {
    FORWARD("forward"),
    BACKWARD("backward"),
    LEFT("left"),
    RIGHT("right");

    /** True for the two sideways directions of the center portion. */
    val lateral: Boolean
        get() = this == LEFT || this == RIGHT

    companion object {
        fun fromId(id: String?): MoveDirection =
            entries.firstOrNull { it.id == id } ?: FORWARD
    }
}

/**
 * Pressure-sensitive running.
 *
 * Holding the top or bottom third of the battle screen runs; the hold is
 * recognised after [LONG_PRESS_TIMEOUT_MS] (much sooner than the platform
 * long-press default) and the reported touch pressure drives the speed:
 * the stronger the press, the faster the run. Devices that report no
 * usable pressure (a constant or non-positive value from the first sample)
 * fall back to a firm default speed, so running always works.
 */
object RunPressure {
    /** How quickly a held touch becomes a run. */
    const val LONG_PRESS_TIMEOUT_MS = 200L

    /** Speed multiplier for the lightest / strongest recognised press. */
    const val MIN_SPEED_FACTOR = 0.45f
    const val MAX_SPEED_FACTOR = 2.2f

    /** Speed used while no pressure is known (pre-press / unknown sensor). */
    const val DEFAULT_SPEED_FACTOR = 1.0f

    /** Pressures below this are treated as the lightest touch. */
    const val LIGHT_PRESSURE = 0.15f

    /** Pressures at (or above) this are treated as a full-force press. */
    const val FULL_PRESSURE = 1.0f

    /**
     * The player's walking speed in metres per second at the default press.
     * Every running speed level is expressed as a multiple of it, which is
     * what lets the fast level match Robert's real fleeing speed exactly.
     */
    const val BASE_SPEED_MPS = 6.5f

    /** One movement step per 100 ms tick at the default speed. */
    const val BASE_STEP_METRES = 0.65f

    /**
     * Maps a raw touch pressure (0..1 on most devices, sometimes larger)
     * to a running speed multiplier. The mapping is deliberately strong:
     * pressing notably harder almost doubles the running speed.
     */
    fun speedFactor(pressure: Float): Float {
        if (pressure.isNaN() || pressure <= 0f) return DEFAULT_SPEED_FACTOR
        val normalized = ((pressure - LIGHT_PRESSURE) / (FULL_PRESSURE - LIGHT_PRESSURE))
            .coerceIn(0f, 1f)
        return MIN_SPEED_FACTOR + normalized * (MAX_SPEED_FACTOR - MIN_SPEED_FACTOR)
    }
}

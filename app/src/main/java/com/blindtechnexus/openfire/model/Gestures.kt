package com.blindtechnexus.openfire.model

/**
 * Every action a gesture can trigger. Keeping the mapping pure makes it
 * unit-testable and guarantees one consistent gesture contract:
 *
 *  - One finger:  up fire, down reload, right next weapon, left previous weapon.
 *  - Two fingers: up / down move the quick menu selection, right own status,
 *                 left enemy status.
 *  - Three or more fingers: no action at all (the old three-finger controls
 *                 were removed).
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
            // Three-finger controls were removed entirely.
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

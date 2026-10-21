package com.blindtechnexus.openfire.model

/**
 * Every action a gesture can trigger. Keeping the mapping pure makes it
 * unit-testable and guarantees one consistent gesture contract:
 *
 *  - One finger:  up fire, down reload, right next gun, left previous gun.
 *  - Two fingers: up Gloo Wall (cover, 100 coins), down medkit,
 *                 right own status, left enemy status.
 *  - Three fingers: up toggle cover, down exit the battle,
 *                   left recover the damaged gun (300 coins).
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
    NEXT_GUN,
    PREV_GUN,
    GLOO_WALL,
    MEDKIT,
    TOGGLE_COVER,
    EXIT_BATTLE,
    RECOVER_GUN,
    STATUS_OWN,
    STATUS_ENEMY,
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
            fingers >= 3 -> when (direction) {
                "up" -> GameAction.TOGGLE_COVER
                "down" -> GameAction.EXIT_BATTLE
                "left" -> GameAction.RECOVER_GUN
                else -> GameAction.NONE
            }
            fingers == 2 -> when (direction) {
                "up" -> GameAction.GLOO_WALL
                "down" -> GameAction.MEDKIT
                "right" -> GameAction.STATUS_OWN
                "left" -> GameAction.STATUS_ENEMY
                else -> GameAction.NONE
            }
            else -> when (direction) {
                "up" -> GameAction.FIRE
                "down" -> GameAction.RELOAD
                "right" -> GameAction.NEXT_GUN
                "left" -> GameAction.PREV_GUN
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
}

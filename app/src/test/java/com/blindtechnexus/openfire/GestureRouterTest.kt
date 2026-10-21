package com.blindtechnexus.openfire

import com.blindtechnexus.openfire.model.GameAction
import com.blindtechnexus.openfire.model.GestureRouter
import com.blindtechnexus.openfire.model.Screen
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Locks down the battle gesture contract:
 *  - One finger:  up fire, down reload, right/left switch gun.
 *  - Two fingers: up Gloo Wall (cover, 100 coins), down medkit, right/left status.
 *  - Three fingers: up toggle cover, down exit.
 */
class GestureRouterTest {

    @Test
    fun oneFingerBattleGestures() {
        assertEquals(GameAction.FIRE, GestureRouter.route(Screen.BATTLE, "up", 1))
        assertEquals(GameAction.RELOAD, GestureRouter.route(Screen.BATTLE, "down", 1))
        assertEquals(GameAction.NEXT_GUN, GestureRouter.route(Screen.BATTLE, "right", 1))
        assertEquals(GameAction.PREV_GUN, GestureRouter.route(Screen.BATTLE, "left", 1))
    }

    @Test
    fun twoFingerSwipeUpDeploysGlooWall() {
        assertEquals(GameAction.GLOO_WALL, GestureRouter.route(Screen.BATTLE, "up", 2))
        assertEquals(GameAction.MEDKIT, GestureRouter.route(Screen.BATTLE, "down", 2))
        assertEquals(GameAction.STATUS_OWN, GestureRouter.route(Screen.BATTLE, "right", 2))
        assertEquals(GameAction.STATUS_ENEMY, GestureRouter.route(Screen.BATTLE, "left", 2))
    }

    @Test
    fun threeFingerBattleGestures() {
        assertEquals(GameAction.TOGGLE_COVER, GestureRouter.route(Screen.BATTLE, "up", 3))
        assertEquals(GameAction.EXIT_BATTLE, GestureRouter.route(Screen.BATTLE, "down", 3))
        // Extra fingers beyond three behave the same way.
        assertEquals(GameAction.TOGGLE_COVER, GestureRouter.route(Screen.BATTLE, "up", 4))
    }

    @Test
    fun flightAndResultGestures() {
        assertEquals(GameAction.CONTINUE, GestureRouter.route(Screen.READER, "up", 1))
        assertEquals(GameAction.BACK_TO_MENU, GestureRouter.route(Screen.READER, "down", 1))
        assertEquals(GameAction.JUMP, GestureRouter.route(Screen.JUMP, "up", 1))
        assertEquals(GameAction.OPEN_PARA, GestureRouter.route(Screen.PARA, "up", 1))
        assertEquals(GameAction.LAND, GestureRouter.route(Screen.LAND, "up", 1))
        assertEquals(GameAction.PLAY_AGAIN, GestureRouter.route(Screen.RESULT, "up", 1))
        assertEquals(GameAction.BACK_TO_MENU, GestureRouter.route(Screen.RESULT, "down", 1))
    }

    @Test
    fun menuScreensIgnoreGestures() {
        assertEquals(GameAction.NONE, GestureRouter.route(Screen.MENU, "up", 1))
        assertEquals(GameAction.NONE, GestureRouter.route(Screen.SETTINGS, "down", 2))
        assertEquals(GameAction.NONE, GestureRouter.route(Screen.BATTLE, "up", 0))
    }
}

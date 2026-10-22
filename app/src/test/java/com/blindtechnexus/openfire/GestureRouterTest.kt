package com.blindtechnexus.openfire

import com.blindtechnexus.openfire.model.GameAction
import com.blindtechnexus.openfire.model.GestureRouter
import com.blindtechnexus.openfire.model.Screen
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Locks down the new battle gesture contract:
 *  - One finger: up fire, down reload, right/left switch weapon.
 *  - Two fingers: up/down move the quick menu, right/left speak the status.
 *  - Three or more fingers: nothing (the old controls were removed).
 *  - A single tap activates the highlighted quick-menu item.
 */
class GestureRouterTest {

    @Test
    fun oneFingerBattleGestures() {
        assertEquals(GameAction.FIRE, GestureRouter.route(Screen.BATTLE, "up", 1))
        assertEquals(GameAction.RELOAD, GestureRouter.route(Screen.BATTLE, "down", 1))
        assertEquals(GameAction.NEXT_WEAPON, GestureRouter.route(Screen.BATTLE, "right", 1))
        assertEquals(GameAction.PREV_WEAPON, GestureRouter.route(Screen.BATTLE, "left", 1))
    }

    @Test
    fun twoFingersMoveTheQuickMenuOrSpeakStatus() {
        assertEquals(GameAction.MENU_NEXT, GestureRouter.route(Screen.BATTLE, "up", 2))
        assertEquals(GameAction.MENU_PREV, GestureRouter.route(Screen.BATTLE, "down", 2))
        assertEquals(GameAction.STATUS_OWN, GestureRouter.route(Screen.BATTLE, "right", 2))
        assertEquals(GameAction.STATUS_ENEMY, GestureRouter.route(Screen.BATTLE, "left", 2))
    }

    @Test
    fun threeFingerControlsAreRemoved() {
        assertEquals(GameAction.NONE, GestureRouter.route(Screen.BATTLE, "up", 3))
        assertEquals(GameAction.NONE, GestureRouter.route(Screen.BATTLE, "down", 3))
        assertEquals(GameAction.NONE, GestureRouter.route(Screen.BATTLE, "left", 3))
        assertEquals(GameAction.NONE, GestureRouter.route(Screen.BATTLE, "right", 4))
    }

    @Test
    fun aSingleTapActivatesTheQuickMenuItemOnlyInBattle() {
        assertEquals(GameAction.ACTIVATE_MENU, GestureRouter.tap(Screen.BATTLE))
        assertEquals(GameAction.NONE, GestureRouter.tap(Screen.MENU))
        assertEquals(GameAction.NONE, GestureRouter.tap(Screen.RESULT))
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
        assertEquals(GameAction.NONE, GestureRouter.route(Screen.SHOP, "down", 2))
        assertEquals(GameAction.NONE, GestureRouter.route(Screen.SETTINGS, "down", 2))
        assertEquals(GameAction.NONE, GestureRouter.route(Screen.BATTLE, "up", 0))
    }
}

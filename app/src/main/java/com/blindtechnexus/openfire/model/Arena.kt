package com.blindtechnexus.openfire.model

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

/**
 * One movement attempt inside the 100 m battle zone.
 *
 * [blockedByWall] is true when the boundary stopped the fighter completely
 * (nothing moved at all); [touchedWall] is true when the fighter still moved
 * but the boundary shortened the step. Both cases keep the fighter inside
 * the zone: nobody ever runs beyond the 100 m perimeter.
 */
data class ArenaMove(
    val x: Float,
    val y: Float,
    val blockedByWall: Boolean,
    val touchedWall: Boolean = false
) {
    /** True when the fighter really changed its position. */
    val moved: Boolean
        get() = !blockedByWall
}

/**
 * The battle zone: a square perimeter of exactly [Combat.MAX_RANGE] metres
 * on every side (100 m by 100 m).
 *
 * Both fighters live inside this zone. Their real two-dimensional positions
 * decide the fighting distance, so every existing proximity rule keeps
 * working unchanged while the game now supports movement in all four
 * directions: forward and backward (toward and away from the enemy) plus
 * left and right along the width of the zone.
 *
 * Touching any of the four boundaries is a wall hit: the fighter stops, the
 * wall sound plays and (for the player) the word "Wall" is announced. Robert
 * detects the same wall and turns back instead of pressing against it.
 */
object Arena {
    /** Width and depth of the zone in metres. */
    const val SIZE = Combat.MAX_RANGE

    /** Coordinate bounds of the zone. */
    const val MIN = 0f
    const val MAX = SIZE

    /** A coordinate this close to a bound counts as touching the wall. */
    const val WALL_EPSILON = 0.05f

    /** Both fighters start on the centre line, at opposite edges. */
    val PLAYER_START_X = SIZE / 2f
    val PLAYER_START_Y = MIN
    val ROBERT_START_X = SIZE / 2f
    val ROBERT_START_Y = MAX

    fun clampX(value: Float): Float =
        if (value.isNaN()) PLAYER_START_X else value.coerceIn(MIN, MAX)

    fun clampY(value: Float): Float =
        if (value.isNaN()) PLAYER_START_Y else value.coerceIn(MIN, MAX)

    /** Keeps a stored position inside the zone (used when loading a state). */
    fun clamp(x: Float, y: Float): Pair<Float, Float> = Pair(clampX(x), clampY(y))

    /**
     * Real distance in metres between two positions, limited to the zone
     * size so the 0%..100% proximity scale always stays meaningful.
     */
    fun distanceBetween(ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = clampX(ax) - clampX(bx)
        val dy = clampY(ay) - clampY(by)
        return hypot(dx, dy).coerceIn(0f, SIZE)
    }

    /** Distance from a bomb destination, used by the 50 m bomb rules. */
    fun distanceTo(bombX: Float, bombY: Float, x: Float, y: Float): Float =
        distanceBetween(bombX, bombY, x, y)

    /** True while the position sits on (or beyond) one of the four walls. */
    fun atWall(x: Float, y: Float): Boolean {
        val sx = clampX(x)
        val sy = clampY(y)
        return sx <= MIN + WALL_EPSILON || sx >= MAX - WALL_EPSILON ||
            sy <= MIN + WALL_EPSILON || sy >= MAX - WALL_EPSILON
    }

    /**
     * Moves [metres] along the (dx, dy) direction, clamped to the zone.
     * A zero-length direction never moves and never reports a wall.
     */
    fun moveBy(fromX: Float, fromY: Float, dx: Float, dy: Float, metres: Float): ArenaMove {
        val startX = clampX(fromX)
        val startY = clampY(fromY)
        val length = hypot(dx, dy)
        val step = abs(metres)
        if (length <= 0.0001f || step <= 0f) {
            return ArenaMove(startX, startY, blockedByWall = false, touchedWall = false)
        }
        val ux = dx / length
        val uy = dy / length
        val wantedX = startX + ux * step
        val wantedY = startY + uy * step
        val nextX = clampX(wantedX)
        val nextY = clampY(wantedY)
        val touched = abs(nextX - wantedX) > WALL_EPSILON || abs(nextY - wantedY) > WALL_EPSILON
        val blocked = touched && abs(nextX - startX) <= WALL_EPSILON &&
            abs(nextY - startY) <= WALL_EPSILON
        return ArenaMove(nextX, nextY, blockedByWall = blocked, touchedWall = touched)
    }

    /** Moves towards a target position (forward running). */
    fun moveToward(fromX: Float, fromY: Float, toX: Float, toY: Float, metres: Float): ArenaMove =
        moveBy(fromX, fromY, clampX(toX) - clampX(fromX), clampY(toY) - clampY(fromY), metres)

    /** Moves away from a target position (backward running, fleeing). */
    fun moveAway(fromX: Float, fromY: Float, toX: Float, toY: Float, metres: Float): ArenaMove =
        moveBy(fromX, fromY, clampX(fromX) - clampX(toX), clampY(fromY) - clampY(toY), metres)

    /**
     * Sideways running along the width of the zone: right increases X,
     * left decreases it. This is the gesture-controlled strafe.
     */
    fun moveLateral(fromX: Float, fromY: Float, right: Boolean, metres: Float): ArenaMove =
        moveBy(fromX, fromY, if (right) 1f else -1f, 0f, metres)

    /**
     * The position that sits exactly [distance] metres away from an anchor,
     * along the line the fighter already occupies. Robert uses it to hold a
     * firing band, and it keeps the classic one-dimensional distance rules
     * working inside the real two-dimensional zone.
     */
    fun pointAtDistance(
        anchorX: Float,
        anchorY: Float,
        fromX: Float,
        fromY: Float,
        distance: Float
    ): Pair<Float, Float> {
        val ax = clampX(anchorX)
        val ay = clampY(anchorY)
        val fx = clampX(fromX)
        val fy = clampY(fromY)
        val dx = fx - ax
        val dy = fy - ay
        val length = hypot(dx, dy)
        val wanted = distance.coerceIn(0f, SIZE)
        if (length <= 0.0001f) {
            // Sitting exactly on the anchor: step back along the depth axis.
            return Pair(ax, clampY(ay + wanted))
        }
        return Pair(clampX(ax + dx / length * wanted), clampY(ay + dy / length * wanted))
    }

    /**
     * The direction that takes a fleeing fighter furthest away from a bomb
     * destination: straight out of the blast radius, preferring the depth
     * axis because that is where the room usually is.
     */
    fun escapeDirection(bombX: Float, bombY: Float, x: Float, y: Float): Pair<Float, Float> {
        val dx = clampX(x) - clampX(bombX)
        val dy = clampY(y) - clampY(bombY)
        if (abs(dx) <= 0.0001f && abs(dy) <= 0.0001f) return Pair(0f, 1f)
        return Pair(dx, dy)
    }

    /**
     * Robert's answer to a wall: the exact opposite of the heading he was
     * using, so he turns around and runs back into the open zone instead of
     * grinding against the boundary.
     */
    fun turnBackDirection(dx: Float, dy: Float): Pair<Float, Float> {
        if (abs(dx) <= 0.0001f && abs(dy) <= 0.0001f) return Pair(0f, -1f)
        return Pair(-dx, -dy)
    }

    /** How much room is left towards the far edge of the zone (metres). */
    fun roomToRun(y: Float): Float = (MAX - clampY(y)).coerceIn(0f, SIZE)

    /** Normalises a stored position pair, so a corrupt save never escapes the zone. */
    fun normalize(x: Float, y: Float, fallbackX: Float, fallbackY: Float): Pair<Float, Float> {
        val nx = if (x.isNaN()) fallbackX else x
        val ny = if (y.isNaN()) fallbackY else y
        return Pair(clampX(nx), clampY(ny))
    }

    /** True when two positions are effectively the same spot. */
    fun sameSpot(ax: Float, ay: Float, bx: Float, by: Float): Boolean =
        abs(clampX(ax) - clampX(bx)) <= WALL_EPSILON && abs(clampY(ay) - clampY(by)) <= WALL_EPSILON
}

/**
 * Position helpers on [GameState]. Every movement goes through these so the
 * fighting distance and the sideways audio offset always stay in sync with
 * the real two-dimensional positions.
 */
fun GameState.withPositions(
    playerX: Float = this.playerX,
    playerY: Float = this.playerY,
    robertX: Float = this.robertX,
    robertY: Float = this.robertY
): GameState {
    val px = Arena.clampX(playerX)
    val py = Arena.clampY(playerY)
    val rx = Arena.clampX(robertX)
    val ry = Arena.clampY(robertY)
    return copy(
        playerX = px,
        playerY = py,
        robertX = rx,
        robertY = ry,
        distance = Arena.distanceBetween(px, py, rx, ry),
        robertLateral = rx - px
    )
}

/** Moves the player and reports the wall state of that move. */
data class FighterMove(
    val state: GameState,
    val blockedByWall: Boolean,
    val touchedWall: Boolean
) {
    val moved: Boolean
        get() = !blockedByWall
}

/** Runs the player forward (towards Robert) or backward (away from him). */
fun GameState.movePlayer(forward: Boolean, metres: Float): FighterMove {
    val move = if (forward) {
        Arena.moveToward(playerX, playerY, robertX, robertY, metres)
    } else {
        Arena.moveAway(playerX, playerY, robertX, robertY, metres)
    }
    return FighterMove(
        state = withPositions(playerX = move.x, playerY = move.y),
        blockedByWall = move.blockedByWall,
        touchedWall = move.touchedWall
    )
}

/** Runs the player left or right along the width of the zone. */
fun GameState.movePlayerLateral(right: Boolean, metres: Float): FighterMove {
    val move = Arena.moveLateral(playerX, playerY, right, metres)
    return FighterMove(
        state = withPositions(playerX = move.x, playerY = move.y),
        blockedByWall = move.blockedByWall,
        touchedWall = move.touchedWall
    )
}

/**
 * Places Robert exactly [distance] metres from the player, inside the zone.
 * When the boundary shortens that step the move is reported as a wall hit,
 * which is what makes him detect the wall and turn back.
 */
fun GameState.placeRobertAt(distance: Float): FighterMove {
    val wanted = distance.coerceIn(0f, Arena.SIZE)
    val target = Arena.pointAtDistance(playerX, playerY, robertX, robertY, wanted)
    val beforeX = Arena.clampX(robertX)
    val beforeY = Arena.clampY(robertY)
    val next = withPositions(robertX = target.first, robertY = target.second)
    val stuck = Arena.sameSpot(beforeX, beforeY, target.first, target.second)
    val short = abs(next.distance - wanted) > max(Arena.WALL_EPSILON, 0.001f)
    val blocked = stuck || (short && Arena.atWall(target.first, target.second))
    return FighterMove(next, blockedByWall = blocked, touchedWall = blocked || short)
}

/** Moves Robert along an explicit direction (strafe, flee from a bomb). */
fun GameState.moveRobert(dx: Float, dy: Float, metres: Float): FighterMove {
    val move = Arena.moveBy(robertX, robertY, dx, dy, metres)
    return FighterMove(
        state = withPositions(robertX = move.x, robertY = move.y),
        blockedByWall = move.blockedByWall,
        touchedWall = move.touchedWall
    )
}

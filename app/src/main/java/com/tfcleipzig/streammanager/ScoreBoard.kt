package com.tfcleipzig.streammanager

enum class Side { A, B }

/**
 * The two scores plus the order in which goals were scored, so the last goal can be undone
 * without knowing which side it was for.
 */
class ScoreBoard(
    teamA: Int = 0,
    teamB: Int = 0,
    private val maxHistory: Int = MAX_HISTORY,
) {
    var teamA: Int = teamA
        private set
    var teamB: Int = teamB
        private set

    private val history = ArrayDeque<Side>()

    val canUndo: Boolean
        get() = history.isNotEmpty()

    fun score(side: Side): Int =
        when (side) {
            Side.A -> teamA
            Side.B -> teamB
        }

    fun goal(side: Side) {
        when (side) {
            Side.A -> teamA++
            Side.B -> teamB++
        }
        history.addLast(side)
        if (history.size > maxHistory) history.removeFirst()
    }

    /** Takes back the most recent goal. Returns its side, or null if there is nothing to undo. */
    fun undo(): Side? {
        val side = history.removeLastOrNull() ?: return null
        when (side) {
            Side.A -> teamA = maxOf(0, teamA - 1)
            Side.B -> teamB = maxOf(0, teamB - 1)
        }
        return side
    }

    /** Sets both scores and forgets the history, e.g. after a reset in the settings. */
    fun reset(
        teamA: Int,
        teamB: Int,
    ) {
        this.teamA = teamA
        this.teamB = teamB
        history.clear()
    }

    companion object {
        const val MAX_HISTORY = 100
    }
}

/**
 * Drops a second touch on the same side that follows the previous one within [minIntervalMs].
 * No real goal follows that fast; a bouncing finger or a palm landing with several contacts does.
 */
class GoalTouchFilter(
    private val minIntervalMs: Long = MIN_INTERVAL_MS,
) {
    private val lastAccepted = mutableMapOf<Side, Long>()

    fun accept(
        side: Side,
        timeMs: Long,
    ): Boolean {
        val last = lastAccepted[side]
        if (last != null && timeMs - last < minIntervalMs) return false
        lastAccepted[side] = timeMs
        return true
    }

    companion object {
        const val MIN_INTERVAL_MS = 150L
    }
}

package com.tfcleipzig.streammanager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoreBoardTest {
    @Test
    fun `goals count per side`() {
        val board = ScoreBoard()
        board.goal(Side.A)
        board.goal(Side.A)
        board.goal(Side.B)
        assertEquals(2, board.teamA)
        assertEquals(1, board.teamB)
    }

    @Test
    fun `undo takes back the most recent goal regardless of side`() {
        val board = ScoreBoard()
        board.goal(Side.A)
        board.goal(Side.B)
        assertEquals(Side.B, board.undo())
        assertEquals(1, board.teamA)
        assertEquals(0, board.teamB)
        assertEquals(Side.A, board.undo())
        assertEquals(0, board.teamA)
    }

    @Test
    fun `undo with no history does nothing`() {
        val board = ScoreBoard(3, 4)
        assertFalse(board.canUndo)
        assertNull(board.undo())
        assertEquals(3, board.teamA)
        assertEquals(4, board.teamB)
    }

    @Test
    fun `reset clears history`() {
        val board = ScoreBoard()
        board.goal(Side.A)
        board.reset(0, 0)
        assertFalse(board.canUndo)
    }

    @Test
    fun `history is bounded`() {
        val board = ScoreBoard(maxHistory = 2)
        repeat(3) { board.goal(Side.A) }
        board.undo()
        board.undo()
        assertNull(board.undo())
        assertEquals(1, board.teamA)
    }

    @Test
    fun `touch filter drops a second touch on the same side within the interval`() {
        val filter = GoalTouchFilter(minIntervalMs = 150)
        assertTrue(filter.accept(Side.A, 1000))
        assertFalse(filter.accept(Side.A, 1100))
        assertTrue(filter.accept(Side.A, 1150))
    }

    @Test
    fun `touch filter treats sides independently`() {
        val filter = GoalTouchFilter(minIntervalMs = 150)
        assertTrue(filter.accept(Side.A, 1000))
        assertTrue(filter.accept(Side.B, 1010))
    }
}

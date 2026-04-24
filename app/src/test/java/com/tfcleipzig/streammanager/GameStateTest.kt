package com.tfcleipzig.streammanager

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class GameStateTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun defaultValues() {
        val state = GameState()
        assertEquals("", state.teamA)
        assertEquals("", state.teamB)
        assertEquals("", state.teamAPlayer)
        assertEquals("", state.teamBPlayer)
        assertEquals(SettingsManager.DEFAULT_EVENT_NAME, state.eventName)
    }

    @Test
    fun serializationRoundTrip() {
        val state =
            GameState(
                teamA = "TFC Leipzig",
                teamB = "TTC Erfurt",
                teamAPlayer = "Max Mustermann",
                teamBPlayer = "Erika Musterfrau",
                eventName = "Test Event",
            )
        val encoded = json.encodeToString(state)
        val decoded = json.decodeFromString<GameState>(encoded)
        assertEquals(state, decoded)
    }

    @Test
    fun deserializationIgnoresUnknownKeys() {
        val jsonStr = """{"teamA":"A","teamB":"B","teamAPlayer":"","teamBPlayer":"","eventName":"E","extra":"ignored"}"""
        val state = json.decodeFromString<GameState>(jsonStr)
        assertEquals("A", state.teamA)
        assertEquals("B", state.teamB)
    }
}

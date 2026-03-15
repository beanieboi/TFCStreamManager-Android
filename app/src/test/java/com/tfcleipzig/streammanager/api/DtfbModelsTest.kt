package com.tfcleipzig.streammanager.api

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class DtfbModelsTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun playerSerializationRoundTrip() {
        val player = Player(spielerId = "123", nachname = "Müller", vorname = "Thomas")
        val encoded = json.encodeToString(player)
        val decoded = json.decodeFromString<Player>(encoded)
        assertEquals(player, decoded)
    }

    @Test
    fun playerDeserializationFromJson() {
        val jsonStr = """{"spieler_id":"42","nachname":"Schmidt","vorname":"Anna"}"""
        val player = json.decodeFromString<Player>(jsonStr)
        assertEquals("42", player.spielerId)
        assertEquals("Schmidt", player.nachname)
        assertEquals("Anna", player.vorname)
    }

    @Test
    fun teamEntrySerializationRoundTrip() {
        val entry = TeamEntry(teamId = "99", teamname = "TFC Leipzig")
        val encoded = json.encodeToString(entry)
        val decoded = json.decodeFromString<TeamEntry>(encoded)
        assertEquals(entry, decoded)
    }

    @Test
    fun leagueResponseDeserialization() {
        val jsonStr = """{"tabelle":{"tabelle":[{"team_id":"1","teamname":"Team A"},{"team_id":"2","teamname":"Team B"}]}}"""
        val response = json.decodeFromString<LeagueResponse>(jsonStr)
        assertEquals(2, response.tabelle.tabelle.size)
        assertEquals("Team A", response.tabelle.tabelle[0].teamname)
    }

    @Test
    fun teamDetailsResponseDeserialization() {
        val jsonStr = """
        {
            "data": {
                "team": {"team_id": "10", "teamname": "TFC Leipzig"},
                "mitglieder": [
                    {"spieler_id": "1", "nachname": "Doe", "vorname": "John"},
                    {"spieler_id": "2", "nachname": "Doe", "vorname": "Jane"}
                ]
            }
        }
        """.trimIndent()
        val response = json.decodeFromString<TeamDetailsResponse>(jsonStr)
        assertEquals("TFC Leipzig", response.data.team.teamname)
        assertEquals(2, response.data.mitglieder.size)
        assertEquals("John", response.data.mitglieder[0].vorname)
    }
}

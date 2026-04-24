package com.tfcleipzig.streammanager

import com.tfcleipzig.streammanager.api.Player
import com.tfcleipzig.streammanager.api.TeamEntry
import java.util.concurrent.ConcurrentHashMap

object TeamDataStore {
    @Volatile
    private var teams: List<TeamEntry> = emptyList()
    private val teamPlayers = ConcurrentHashMap<String, List<Player>>()

    fun setTeams(newTeams: List<TeamEntry>) {
        teams = newTeams.toList()
    }

    fun getTeams(): List<TeamEntry> = teams

    fun setTeamPlayers(
        teamId: String,
        players: List<Player>,
    ) {
        teamPlayers[teamId] = players.toList()
    }

    fun getTeamPlayers(teamId: String): List<Player>? = teamPlayers[teamId]

    fun hasTeams(): Boolean = teams.isNotEmpty()

    fun hasPlayersForTeam(teamId: String): Boolean = teamPlayers.containsKey(teamId)

    fun clear() {
        teams = emptyList()
        teamPlayers.clear()
    }
}

package com.tfcleipzig.streammanager.api

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.json.*

@Serializable data class LeagueResponse(val tabelle: Tabelle)

@Serializable data class Tabelle(val tabelle: List<TeamEntry>)

@Serializable data class TeamEntry(@SerialName("team_id") val teamId: String, val teamname: String)

@Serializable data class TeamDetailsResponse(val data: TeamDetailsData)

@Serializable data class TeamDetailsData(val team: TeamInfo, val mitglieder: List<Player>)

@Serializable data class TeamInfo(@SerialName("team_id") val teamId: String, val teamname: String)

@Serializable
data class Player(
        @SerialName("spieler_id") val spielerId: String,
        val nachname: String,
        val vorname: String,
)

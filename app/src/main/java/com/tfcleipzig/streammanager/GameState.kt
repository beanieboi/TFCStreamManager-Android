package com.tfcleipzig.streammanager

import kotlinx.serialization.Serializable

@Serializable
data class GameState(
    val teamA: String = "",
    val teamB: String = "",
    val teamAPlayer: String = "",
    val teamBPlayer: String = "",
    val eventName: String = SettingsManager.DEFAULT_EVENT_NAME,
)

package com.tfcleipzig.streammanager

import android.content.Context
import android.util.Log
import android.content.SharedPreferences
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object SettingsManager {
        private const val TAG = "SettingsManager"
        private lateinit var prefs: SharedPreferences
        private val json = Json { ignoreUnknownKeys = true }

        private const val KEY_LEAGUE_URL = "league_url"
        private const val KEY_EVENT_NAME = "event_name"
        private const val KEY_GAME_STATE = "game_state"

        const val DEFAULT_URL =
                "https://mtfv.de/ligabetrieb/aktuelle-saison?format=json"
        const val DEFAULT_EVENT_NAME = "MTFV Landesliga 2025"

        fun init(context: Context) {
                if (!::prefs.isInitialized) {
                        Log.d(TAG, "Initializing SettingsManager")
                        prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                }
        }

        fun saveGameSettings(settings: GameState) {
                Log.d(TAG, "Saving game settings: teamA=${settings.teamA}, teamB=${settings.teamB}")
                prefs.edit().putString(KEY_GAME_STATE, json.encodeToString(settings)).apply()
        }

        fun getGameSettings(): GameState {
                val stored = prefs.getString(KEY_GAME_STATE, null)
                val state = if (stored != null) {
                        try {
                                json.decodeFromString<GameState>(stored)
                        } catch (e: Exception) {
                                Log.e(TAG, "Failed to parse saved game state", e)
                                GameState()
                        }
                } else {
                        GameState(eventName = getEventName())
                }
                Log.d(TAG, "Getting game settings: teamA=${state.teamA}, teamB=${state.teamB}")
                return state
        }

        fun saveLeagueUrl(url: String) {
                Log.d(TAG, "Saving league URL: $url")
                prefs.edit().putString(KEY_LEAGUE_URL, url).apply()
        }

        fun getLeagueUrl(): String {
                val url = prefs.getString(KEY_LEAGUE_URL, DEFAULT_URL) ?: DEFAULT_URL
                Log.d(TAG, "Loading league URL: $url")
                return url
        }

        fun saveEventName(eventName: String) {
                Log.d(TAG, "Saving event name: $eventName")
                prefs.edit().putString(KEY_EVENT_NAME, eventName).apply()
        }

        fun getEventName(): String {
                val eventName =
                        prefs.getString(KEY_EVENT_NAME, DEFAULT_EVENT_NAME) ?: DEFAULT_EVENT_NAME
                Log.d(TAG, "Loading event name: $eventName")
                return eventName
        }

        fun clearSettings() {
                Log.d(TAG, "Clearing all settings")
                prefs.edit().clear().putString(KEY_LEAGUE_URL, DEFAULT_URL).apply()
        }
}

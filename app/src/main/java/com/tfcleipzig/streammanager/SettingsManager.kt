package com.tfcleipzig.streammanager

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

object SettingsManager {
        private lateinit var prefs: SharedPreferences
        private var gameState: GameState = GameState()
        private val TAG = "SettingsManager"

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
                gameState = settings
        }

        fun getGameSettings(): GameState {
                Log.d(
                        TAG,
                        "Getting game settings: teamA=${gameState.teamA}, teamB=${gameState.teamB}"
                )
                return gameState
        }

        fun saveLeagueUrl(url: String) {
                Log.d(TAG, "Saving league URL: $url")
                prefs.edit().apply {
                        putString("league_url", url)
                        apply()
                }
        }

        fun getLeagueUrl(): String {
                val url = prefs.getString("league_url", DEFAULT_URL) ?: DEFAULT_URL
                Log.d(TAG, "Loading league URL: $url")
                return url
        }

        fun saveEventName(eventName: String) {
                Log.d(TAG, "Saving event name: $eventName")
                prefs.edit().apply {
                        putString("event_name", eventName)
                        apply()
                }
        }

        fun getEventName(): String {
                val eventName =
                        prefs.getString("event_name", DEFAULT_EVENT_NAME) ?: DEFAULT_EVENT_NAME
                Log.d(TAG, "Loading event name: $eventName")
                return eventName
        }

        fun clearSettings() {
                Log.d(TAG, "Clearing all settings")
                val editor = prefs.edit()
                editor.clear()
                editor.putString("league_url", DEFAULT_URL)
                editor.apply()

                // Reset game settings to default
                gameState = GameState()
        }
}

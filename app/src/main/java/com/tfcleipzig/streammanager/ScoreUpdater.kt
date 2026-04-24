package com.tfcleipzig.streammanager

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class ScoreUpdater {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val client =
        OkHttpClient
            .Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()

    private val json = Json { encodeDefaults = true }

    @Serializable
    private data class ScorePayload(
        val teamAScore: Int,
        val teamBScore: Int,
        val teamAName: String,
        val teamBName: String,
        val teamAPlayer: String,
        val teamBPlayer: String,
        val eventName: String,
    )

    fun updateScores(
        host: String,
        port: Int,
        teamAScore: Int,
        teamBScore: Int,
        gameState: GameState,
    ) {
        Log.d(TAG, "Updating scores: $host:$port, teamA: $teamAScore, teamB: $teamBScore")

        scope.launch {
            try {
                val payload =
                    ScorePayload(
                        teamAScore = teamAScore,
                        teamBScore = teamBScore,
                        teamAName = gameState.teamA,
                        teamBName = gameState.teamB,
                        teamAPlayer = gameState.teamAPlayer,
                        teamBPlayer = gameState.teamBPlayer,
                        eventName = gameState.eventName,
                    )

                val body =
                    json
                        .encodeToString(payload)
                        .toRequestBody("application/json".toMediaType())

                val request =
                    Request
                        .Builder()
                        .url("http://$host:$port/scores")
                        .post(body)
                        .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    Log.d(TAG, "Scores updated successfully")
                } else {
                    Log.e(TAG, "Failed to update scores: HTTP ${response.code}")
                }
                response.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error updating scores", e)
            }
        }
    }

    companion object {
        private const val TAG = "ScoreUpdater"
    }
}

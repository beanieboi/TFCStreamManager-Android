package com.tfcleipzig.streammanager

import android.util.Log
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ScoreUpdater {
    private val TAG = "ScoreUpdater"

    private data class GameInfo(
            val teamA: String,
            val teamB: String,
            val teamAPlayer: String,
            val teamBPlayer: String,
            val eventName: String
    )

    fun updateScores(
            host: String,
            port: Int,
            teamAScore: Int,
            teamBScore: Int,
            gameState: GameState,
    ) {
        Log.d(TAG, "Updating scores: $host:$port, teamA: $teamAScore, teamB: $teamBScore")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL("http://$host:$port/scores")
                val connection = url.openConnection() as HttpURLConnection
                setupConnection(connection)

                val jsonPayload =
                        createJsonPayload(
                                teamAScore,
                                teamBScore,
                                gameState.teamA,
                                gameState.teamB,
                                gameState.teamAPlayer,
                                gameState.teamBPlayer,
                                gameState.eventName
                        )
                Log.d(TAG, "Sending payload: $jsonPayload")

                sendPayload(connection, jsonPayload)
                handleResponse(connection)
            } catch (e: Exception) {
                Log.e(TAG, "Error updating scores", e)
            }
        }
    }

    private fun setupConnection(connection: HttpURLConnection) {
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.doOutput = true
    }

    private fun createJsonPayload(
            teamAScore: Int,
            teamBScore: Int,
            teamA: String,
            teamB: String,
            teamAPlayer: String,
            teamBPlayer: String,
            eventName: String
    ): String {
        return """
        {
            "teamAScore": $teamAScore,
            "teamBScore": $teamBScore,
            "teamAName": "$teamA",
            "teamBName": "$teamB",
            "teamAPlayer": "$teamAPlayer",
            "teamBPlayer": "$teamBPlayer",
            "eventName": "$eventName"
        }
        """.trimIndent()
    }

    private fun sendPayload(connection: HttpURLConnection, jsonPayload: String) {
        OutputStreamWriter(connection.outputStream).use { writer ->
            writer.write(jsonPayload)
            writer.flush()
        }
    }

    private fun handleResponse(connection: HttpURLConnection) {
        val responseCode = connection.responseCode
        if (responseCode == HttpURLConnection.HTTP_OK) {
            Log.d(TAG, "Scores updated successfully")
        } else {
            Log.e(TAG, "Failed to update scores: HTTP $responseCode")
        }
        connection.disconnect()
    }
}

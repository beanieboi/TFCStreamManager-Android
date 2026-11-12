package com.tfcleipzig.streammanager

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

data class Endpoint(
    val host: String,
    val port: Int,
)

data class ScoreSnapshot(
    val teamAScore: Int,
    val teamBScore: Int,
    val gameState: GameState,
)

/**
 * Sends score snapshots to the server one at a time, newest wins.
 *
 * Snapshots submitted while a request is in flight collapse into the latest one, so the server
 * never receives an older score after a newer one. A failed send is retried with backoff until it
 * succeeds or a newer snapshot replaces it. [onSyncChange] reports, on the main thread, whether
 * the latest score has reached the server.
 */
class ScoreUpdater(
    private val onSyncChange: (synced: Boolean) -> Unit,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pending = Channel<ScoreSnapshot>(Channel.CONFLATED)
    private val endpointChanged = Channel<Unit>(Channel.CONFLATED)

    private val client =
        OkHttpClient
            .Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .callTimeout(4, TimeUnit.SECONDS)
            .build()

    private val json = Json { encodeDefaults = true }

    /** Where to send scores; null while the server is not running. Set from the main thread. */
    @Volatile
    var endpoint: Endpoint? = null
        set(value) {
            field = value
            endpointChanged.trySend(Unit)
        }

    private var synced: Boolean? = null

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

    init {
        scope.launch { sendLoop() }
    }

    fun submit(snapshot: ScoreSnapshot) {
        pending.trySend(snapshot)
    }

    fun close() {
        scope.cancel()
    }

    private suspend fun sendLoop() {
        var latest: ScoreSnapshot? = null
        var backoff = INITIAL_BACKOFF_MS
        while (scope.isActive) {
            val snapshot = latest ?: pending.receive()
            latest = pending.tryReceive().getOrNull() ?: snapshot
            val target = endpoint
            if (target == null) {
                Log.d(TAG, "No running server, holding ${latest.teamAScore}:${latest.teamBScore}")
                reportSync(false)
                latest = waitForNewerOrEndpoint(timeoutMs = null) ?: latest
                continue
            }
            if (send(target, latest)) {
                reportSync(true)
                latest = null
                backoff = INITIAL_BACKOFF_MS
            } else {
                reportSync(false)
                latest = waitForNewerOrEndpoint(timeoutMs = backoff) ?: latest
                backoff = minOf(backoff * 2, MAX_BACKOFF_MS)
            }
        }
    }

    /** Waits for a newer snapshot (returned), an endpoint change or the timeout (null). */
    private suspend fun waitForNewerOrEndpoint(timeoutMs: Long?): ScoreSnapshot? {
        val wait: suspend () -> ScoreSnapshot? = {
            select {
                pending.onReceive { it }
                endpointChanged.onReceive { null }
            }
        }
        return if (timeoutMs == null) wait() else withTimeoutOrNull(timeoutMs) { wait() }
    }

    private fun send(
        target: Endpoint,
        snapshot: ScoreSnapshot,
    ): Boolean =
        try {
            val gameState = snapshot.gameState
            val payload =
                ScorePayload(
                    teamAScore = snapshot.teamAScore,
                    teamBScore = snapshot.teamBScore,
                    teamAName = gameState.teamA,
                    teamBName = gameState.teamB,
                    teamAPlayer = gameState.teamAPlayer,
                    teamBPlayer = gameState.teamBPlayer,
                    eventName = gameState.eventName,
                )
            val request =
                Request
                    .Builder()
                    .url("http://${target.host}:${target.port}/scores")
                    .post(
                        json
                            .encodeToString(ScorePayload.serializer(), payload)
                            .toRequestBody("application/json".toMediaType()),
                    ).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Failed to update scores: HTTP ${response.code}")
                }
                response.isSuccessful
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating scores", e)
            false
        }

    private suspend fun reportSync(value: Boolean) {
        if (synced == value) return
        synced = value
        withContext(Dispatchers.Main) { onSyncChange(value) }
    }

    companion object {
        private const val TAG = "ScoreUpdater"
        private const val INITIAL_BACKOFF_MS = 500L
        private const val MAX_BACKOFF_MS = 5000L
    }
}

package com.tfcleipzig.streammanager.api

import android.util.Log
import com.tfcleipzig.streammanager.SettingsManager
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.OkHttpClient
import okhttp3.Request

class DtfbClient {
    private val client =
            OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .build()
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    private val tag = "DtfbClient"

    suspend fun getLeagueTeams(): Result<List<TeamEntry>> {
        return withContext(Dispatchers.IO) {
            try {
                val url = SettingsManager.getLeagueUrl()
                Log.d(tag, "Requesting URL: $url")

                val request =
                        Request.Builder().url(url).addHeader("Accept", "application/json").build()

                val response = client.newCall(request).execute()
                val responseBody = response.body.string()

                Log.d(tag, "Response code: ${response.code}")
                Log.d(tag, "Response body length: ${responseBody.length}")
                Log.d(tag, "Response body preview: ${responseBody.takeLast(100)}")

                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                            Exception("Failed to fetch data: ${response.code}")
                    )
                }

                val parsedJson: JsonElement =
                        json.parseToJsonElement(responseBody)
                Log.d(tag, "Parsed JSON: $parsedJson")

                val leagueResponse =
                        json.decodeFromString<List<LeagueResponse>>(responseBody).first()
                Result.success(leagueResponse.tabelle.tabelle)
            } catch (e: Exception) {
                Log.e(tag, "Error fetching teams", e)
                Result.failure(e)
            }
        }
    }

    suspend fun getTeamPlayers(teamId: String): Result<List<Player>> {
        return withContext(Dispatchers.IO) {
            try {
                val url =
                        "https://mtfv.de/ligabetrieb/aktuelle-saison?task=team_details&id=$teamId&format=json"
                Log.d(tag, "Requesting team details URL: $url")

                val request =
                        Request.Builder().url(url).addHeader("Accept", "application/json").build()

                val response = client.newCall(request).execute()
                val responseBody = response.body.string()

                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                            Exception("Failed to fetch team details: ${response.code}")
                    )
                }

                val teamDetails = json.decodeFromString<TeamDetailsResponse>(responseBody)
                Result.success(teamDetails.data.mitglieder)
            } catch (e: Exception) {
                Log.e(tag, "Error fetching team players", e)
                Result.failure(e)
            }
        }
    }
}

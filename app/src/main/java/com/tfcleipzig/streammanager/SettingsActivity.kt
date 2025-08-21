package com.tfcleipzig.streammanager

import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.tfcleipzig.streammanager.api.DtfbClient
import com.tfcleipzig.streammanager.api.TeamEntry
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {
    private lateinit var urlInput: TextInputEditText
    private lateinit var eventNameInput: TextInputEditText
    private lateinit var teamASelect: AutoCompleteTextView
    private lateinit var teamBSelect: AutoCompleteTextView
    private val TAG = "SettingsActivity"
    private var teams: List<TeamEntry> = emptyList()
    private lateinit var gameState: GameState

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_settings)

        // Load the current game settings
        gameState = SettingsManager.getGameSettings()
        Log.d(
                TAG,
                "Loaded initial game settings: left=${gameState.teamA}, right=${gameState.teamB}"
        )

        // Initialize views first
        initializeViews()
        // Then restore settings and setup listeners
        restoreSettings()
        setupButtons()
        setupWindowInsets()
    }

    private fun initializeViews() {
        // Initialize URL input
        urlInput = findViewById(R.id.urlInput)

        // Initialize event name input
        eventNameInput = findViewById(R.id.eventNameInput)
        eventNameInput.setOnClickListener {
            eventNameInput.isFocusableInTouchMode = true
            eventNameInput.isFocusable = true
            eventNameInput.requestFocus()
        }

        // Initialize team selects
        teamASelect = findViewById(R.id.teamASelect)
        teamBSelect = findViewById(R.id.teamBSelect)
    }

    private fun restoreSettings() {
        // Load saved URL
        urlInput.setText(SettingsManager.getLeagueUrl())

        // Load saved event name
        eventNameInput.setText(SettingsManager.getEventName())

        // Check if we have teams in the store
        if (TeamDataStore.hasTeams()) {
            teams = TeamDataStore.getTeams()
            updateTeamDropdowns()
        } else {
            // Disable all dropdowns until teams are loaded
            findViewById<TextInputLayout>(R.id.teamALayout).isEnabled = false
            findViewById<TextInputLayout>(R.id.teamBLayout).isEnabled = false

            // Load teams from network
            loadTeams()
        }
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.settings)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun setupButtons() {
        findViewById<ImageButton>(R.id.closeButton).setOnClickListener {
            // Save event name before closing
            val newEventName = eventNameInput.text.toString()
            SettingsManager.saveEventName(newEventName)
            gameState = gameState.copy(eventName = newEventName)
            SettingsManager.saveGameSettings(gameState)
            setResult(RESULT_OK)
            finish()
        }

        findViewById<Button>(R.id.loadTeamsButton).setOnClickListener {
            // Reset UI state
            findViewById<TextInputLayout>(R.id.teamALayout).isEnabled = false
            findViewById<TextInputLayout>(R.id.teamBLayout).isEnabled = false
            // Load fresh data
            loadTeams()
        }

        // Add reset scores button handler
        findViewById<Button>(R.id.resetScoresButton).setOnClickListener {
            Log.d(TAG, "Resetting scores")
            // Reset scores in ScoreManager
            ScoreManager(this).saveScores(0, 0)
            setResult(RESULT_OK)
            Toast.makeText(this, "Scores reset to 0", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.clearSettingsButton).setOnClickListener {
            Log.d(TAG, "Clearing all settings")
            // Clear all settings
            SettingsManager.clearSettings()
            TeamDataStore.clear()
            ScoreManager(this).clearScores()

            // Reset UI
            urlInput.setText(SettingsManager.getLeagueUrl())
            eventNameInput.setText(SettingsManager.getEventName())
            teamASelect.setText("", false)
            teamBSelect.setText("", false)

            // Disable team dropdowns until teams are loaded
            findViewById<TextInputLayout>(R.id.teamALayout).isEnabled = false
            findViewById<TextInputLayout>(R.id.teamBLayout).isEnabled = false

            setResult(RESULT_OK)
            Toast.makeText(this, "All settings cleared", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadTeams() {
        // First check if we already have teams loaded
        if (TeamDataStore.hasTeams()) {
            teams = TeamDataStore.getTeams()
            updateTeamDropdowns()
            return
        }

        lifecycleScope.launch {
            try {
                val dtfbClient = DtfbClient(this@SettingsActivity)

                dtfbClient
                        .getLeagueTeams()
                        .onSuccess { loadedTeams ->
                            teams = loadedTeams
                            TeamDataStore.setTeams(loadedTeams)

                            // Load players for all teams
                            loadedTeams.forEach { team ->
                                dtfbClient
                                        .getTeamPlayers(team.teamId)
                                        .onSuccess { players ->
                                            Log.d(
                                                    TAG,
                                                    "Loaded ${players.size} players for team ${team.teamname}"
                                            )
                                            TeamDataStore.setTeamPlayers(team.teamId, players)
                                        }
                                        .onFailure { e ->
                                            Log.e(
                                                    TAG,
                                                    "Failed to load players for team ${team.teamname}",
                                                    e
                                            )
                                        }
                            }

                            updateTeamDropdowns()
                        }
                        .onFailure { e -> Log.e(TAG, "Failed to load teams", e) }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading teams", e)
            }
        }
    }

    private fun updateTeamDropdowns() {
        // Get all team names for the adapters
        val teamNames = teams.map { it.teamname }
        Log.d(TAG, "Setting up team dropdowns with ${teamNames.size} teams")

        // Create adapters with all team names
        val leftAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, teamNames)
        val rightAdapter =
                ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, teamNames)

        // Enable team dropdowns since we have teams loaded
        findViewById<TextInputLayout>(R.id.teamALayout).isEnabled = true
        findViewById<TextInputLayout>(R.id.teamBLayout).isEnabled = true

        // Set adapters
        teamASelect.setAdapter(leftAdapter)
        teamBSelect.setAdapter(rightAdapter)

        // Restore previous selections if they exist
        if (gameState.teamA.isNotEmpty()) {
            Log.d(TAG, "Restoring team A: ${gameState.teamA}")
            teamASelect.setText(gameState.teamA, false)
        }
        if (gameState.teamB.isNotEmpty()) {
            Log.d(TAG, "Restoring team B: ${gameState.teamB}")
            teamBSelect.setText(gameState.teamB, false)
        }

        // Team selection listeners
        teamASelect.setOnItemClickListener { _, _, position, _ ->
            val selectedTeam = teams[position]
            Log.d(TAG, "Selected team A: ${selectedTeam.teamname}")
            gameState = gameState.copy(teamA = selectedTeam.teamname)
            SettingsManager.saveGameSettings(gameState)
        }

        teamBSelect.setOnItemClickListener { _, _, position, _ ->
            val selectedTeam = teams[position]
            Log.d(TAG, "Selected team B: ${selectedTeam.teamname}")
            gameState = gameState.copy(teamB = selectedTeam.teamname)
            SettingsManager.saveGameSettings(gameState)
        }
    }

    override fun onBackPressed() {
        // Save event name when back button is pressed
        val newEventName = eventNameInput.text.toString()
        SettingsManager.saveEventName(newEventName)
        gameState = gameState.copy(eventName = newEventName)
        SettingsManager.saveGameSettings(gameState)
        setResult(RESULT_OK)
        super.onBackPressed()
    }
}

package com.tfcleipzig.streammanager

import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.tfcleipzig.streammanager.api.DtfbClient
import com.tfcleipzig.streammanager.api.TeamEntry
import com.tfcleipzig.streammanager.databinding.ActivitySettingsBinding
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var scoreManager: ScoreManager
    private var teams: List<TeamEntry> = emptyList()
    private lateinit var gameState: GameState
    private var isLoadingTeams = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        scoreManager = ScoreManager(this)
        gameState = SettingsManager.getGameSettings()
        Log.d(TAG, "Loaded initial game settings: left=${gameState.teamA}, right=${gameState.teamB}")

        restoreSettings()
        setupButtons()
        setupWindowInsets()
        setupBackNavigation()
    }

    private fun restoreSettings() {
        binding.urlInput.setText(SettingsManager.getLeagueUrl())
        binding.eventNameInput.setText(SettingsManager.getEventName())
        binding.eventNameInput.setOnClickListener {
            binding.eventNameInput.isFocusableInTouchMode = true
            binding.eventNameInput.isFocusable = true
            binding.eventNameInput.requestFocus()
        }

        if (TeamDataStore.hasTeams()) {
            teams = TeamDataStore.getTeams()
            updateTeamDropdowns()
        } else {
            binding.teamALayout.isEnabled = false
            binding.teamBLayout.isEnabled = false
            loadTeams()
        }
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.settings) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun setupButtons() {
        binding.closeButton.setOnClickListener {
            saveAndFinish()
        }

        binding.loadTeamsButton.setOnClickListener {
            binding.teamALayout.isEnabled = false
            binding.teamBLayout.isEnabled = false
            TeamDataStore.clear()
            loadTeams()
        }

        binding.resetScoresButton.setOnClickListener {
            Log.d(TAG, "Resetting scores")
            scoreManager.saveScores(0, 0)
            setResult(RESULT_OK)
            Toast.makeText(this, "Scores reset to 0", Toast.LENGTH_SHORT).show()
        }

        binding.clearSettingsButton.setOnClickListener {
            Log.d(TAG, "Clearing all settings")
            SettingsManager.clearSettings()
            TeamDataStore.clear()
            scoreManager.clearScores()

            binding.urlInput.setText(SettingsManager.getLeagueUrl())
            binding.eventNameInput.setText(SettingsManager.getEventName())
            binding.teamASelect.setText("", false)
            binding.teamBSelect.setText("", false)
            binding.teamALayout.isEnabled = false
            binding.teamBLayout.isEnabled = false

            gameState = GameState()
            setResult(RESULT_OK)
            Toast.makeText(this, "All settings cleared", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveAndFinish() {
        val newEventName = binding.eventNameInput.text.toString()
        SettingsManager.saveEventName(newEventName)
        gameState = gameState.copy(eventName = newEventName)
        SettingsManager.saveGameSettings(gameState)
        setResult(RESULT_OK)
        finish()
    }

    private fun loadTeams() {
        if (isLoadingTeams) return
        isLoadingTeams = true

        if (TeamDataStore.hasTeams()) {
            teams = TeamDataStore.getTeams()
            updateTeamDropdowns()
            isLoadingTeams = false
            return
        }

        lifecycleScope.launch {
            try {
                val dtfbClient = DtfbClient()

                dtfbClient.getLeagueTeams()
                        .onSuccess { loadedTeams ->
                            teams = loadedTeams
                            TeamDataStore.setTeams(loadedTeams)

                            loadedTeams.forEach { team ->
                                dtfbClient.getTeamPlayers(team.teamId)
                                        .onSuccess { players ->
                                            Log.d(TAG, "Loaded ${players.size} players for team ${team.teamname}")
                                            TeamDataStore.setTeamPlayers(team.teamId, players)
                                        }
                                        .onFailure { e ->
                                            Log.e(TAG, "Failed to load players for team ${team.teamname}", e)
                                        }
                            }

                            updateTeamDropdowns()
                            Toast.makeText(this@SettingsActivity, "Teams loaded", Toast.LENGTH_SHORT).show()
                        }
                        .onFailure { e ->
                            Log.e(TAG, "Failed to load teams", e)
                            Toast.makeText(this@SettingsActivity, "Failed to load teams: ${e.message}", Toast.LENGTH_LONG).show()
                        }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading teams", e)
                Toast.makeText(this@SettingsActivity, "Error loading teams: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                isLoadingTeams = false
            }
        }
    }

    private fun updateTeamDropdowns() {
        val teamNames = teams.map { it.teamname }
        Log.d(TAG, "Setting up team dropdowns with ${teamNames.size} teams")

        val leftAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, teamNames)
        val rightAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, teamNames)

        binding.teamALayout.isEnabled = true
        binding.teamBLayout.isEnabled = true

        binding.teamASelect.setAdapter(leftAdapter)
        binding.teamBSelect.setAdapter(rightAdapter)

        if (gameState.teamA.isNotEmpty()) {
            binding.teamASelect.setText(gameState.teamA, false)
        }
        if (gameState.teamB.isNotEmpty()) {
            binding.teamBSelect.setText(gameState.teamB, false)
        }

        binding.teamASelect.setOnItemClickListener { _, _, position, _ ->
            val selectedTeam = teams[position]
            if (selectedTeam.teamname == gameState.teamB) {
                Toast.makeText(this, "Team already selected as Team B", Toast.LENGTH_SHORT).show()
                binding.teamASelect.setText(gameState.teamA, false)
                return@setOnItemClickListener
            }
            Log.d(TAG, "Selected team A: ${selectedTeam.teamname}")
            gameState = gameState.copy(teamA = selectedTeam.teamname, teamAPlayer = "")
            SettingsManager.saveGameSettings(gameState)
        }

        binding.teamBSelect.setOnItemClickListener { _, _, position, _ ->
            val selectedTeam = teams[position]
            if (selectedTeam.teamname == gameState.teamA) {
                Toast.makeText(this, "Team already selected as Team A", Toast.LENGTH_SHORT).show()
                binding.teamBSelect.setText(gameState.teamB, false)
                return@setOnItemClickListener
            }
            Log.d(TAG, "Selected team B: ${selectedTeam.teamname}")
            gameState = gameState.copy(teamB = selectedTeam.teamname, teamBPlayer = "")
            SettingsManager.saveGameSettings(gameState)
        }
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                saveAndFinish()
            }
        })
    }

    companion object {
        private const val TAG = "SettingsActivity"
    }
}

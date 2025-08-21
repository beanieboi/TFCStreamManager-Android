package com.tfcleipzig.streammanager

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GestureDetectorCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton

class MainActivity : AppCompatActivity() {
    private lateinit var nsdHelper: NsdHelper
    private lateinit var statusDot: View
    private lateinit var teamAScore: TextView
    private lateinit var teamBScore: TextView
    private val scoreUpdater = ScoreUpdater()
    private lateinit var scoreManager: ScoreManager
    private lateinit var settingsManager: SettingsManager
    private var gameState: GameState = GameState()
    private val TAG = "MainActivity"
    private var selectedGame: Int = 1 // Default to D1

    private val gameButtons = mutableMapOf<Int, Button>()

    private lateinit var teamAPlayer1Select: AutoCompleteTextView
    private lateinit var teamAPlayer2Select: AutoCompleteTextView
    private lateinit var teamBPlayer1Select: AutoCompleteTextView
    private lateinit var teamBPlayer2Select: AutoCompleteTextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // Initialize SettingsManager
        SettingsManager.init(this)

        initializeManagers()
        initializeViews()
        setupScoreHandlers()
        setupNsdHelper()
        setupWindowInsets()
        setupSettingsButton()
    }

    private fun initializeManagers() {
        scoreManager = ScoreManager(this)
        gameState = SettingsManager.getGameSettings()
    }

    private fun initializeViews() {
        statusDot = findViewById(R.id.statusDot)
        teamAScore = findViewById(R.id.teamAScore)
        teamBScore = findViewById(R.id.teamBScore)

        // Restore saved scores
        val (savedTeamAScore, savedTeamBScore) = scoreManager.getScores()
        teamAScore.text = savedTeamAScore.toString()
        teamBScore.text = savedTeamBScore.toString()

        // Initialize player dropdowns
        teamAPlayer1Select = findViewById(R.id.teamAPlayer1Select)
        teamAPlayer2Select = findViewById(R.id.teamAPlayer2Select)
        teamBPlayer1Select = findViewById(R.id.teamBPlayer1Select)
        teamBPlayer2Select = findViewById(R.id.teamBPlayer2Select)

        // Update player dropdowns with current teams
        updatePlayerDropdowns()
    }

    private fun setupNsdHelper() {
        nsdHelper = NsdHelper(this) { status -> updateStatusDot(status.contains("Connected")) }
        nsdHelper.startDiscovery()
    }

    private fun setupSettingsButton() {
        findViewById<FloatingActionButton>(R.id.settingsButton).setOnClickListener {
            startActivityForResult(
                    Intent(this, SettingsActivity::class.java),
                    SETTINGS_REQUEST_CODE
            )
        }
    }

    private fun setupScoreHandlers() {
        setupScoreGestureDetector(teamAScore)
        setupScoreGestureDetector(teamBScore)
    }

    private fun setupScoreGestureDetector(scoreView: TextView) {
        val gestureDetector =
                GestureDetectorCompat(
                        this,
                        object : GestureDetector.SimpleOnGestureListener() {
                            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                                incrementScore(scoreView)
                                return true
                            }

                            override fun onDoubleTap(e: MotionEvent): Boolean {
                                decrementScore(scoreView)
                                return true
                            }
                        }
                )

        scoreView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }
    }

    private fun incrementScore(scoreView: TextView) {
        val currentScore = scoreView.text.toString().toInt()
        scoreView.text = (currentScore + 1).toString()
        saveAndSendScoreUpdate()
    }

    private fun decrementScore(scoreView: TextView) {
        val currentScore = scoreView.text.toString().toInt()
        if (currentScore > 0) {
            scoreView.text = (currentScore - 1).toString()
            saveAndSendScoreUpdate()
        }
    }

    private fun saveAndSendScoreUpdate() {
        val teamAValue = teamAScore.text.toString().toIntOrNull() ?: 0
        val teamBValue = teamBScore.text.toString().toIntOrNull() ?: 0
        scoreManager.saveScores(teamAValue, teamBValue)

        val (host, port) = nsdHelper.getConnectionDetails()
        if (host != null && port != null) {
            scoreUpdater.updateScores(
                    host = host,
                    port = port,
                    teamAScore = teamAValue,
                    teamBScore = teamBValue,
                    gameState = gameState,
            )
        }
    }

    private fun updateStatusDot(isConnected: Boolean) {
        runOnUiThread {
            statusDot.setBackgroundColor(
                    ContextCompat.getColor(
                            this,
                            if (isConnected) R.color.status_connected
                            else R.color.status_disconnected
                    )
            )
        }
    }

    private fun updatePlayerDropdowns() {
        Log.d(TAG, "Updating player dropdowns")
        // Get the teams from TeamDataStore
        val teams = TeamDataStore.getTeams()
        Log.d(TAG, "Found ${teams.size} teams")

        // Find team IDs for selected teams
        val teamAId = teams.find { it.teamname == gameState.teamA }?.teamId
        val teamBId = teams.find { it.teamname == gameState.teamB }?.teamId
        Log.d(TAG, "Looking for players for teams: teamA=$teamAId, teamB=$teamBId")

        // Update team A players
        teamAId?.let { teamId ->
            Log.d(TAG, "Processing team A players")
            if (TeamDataStore.hasPlayersForTeam(teamId)) {
                TeamDataStore.getTeamPlayers(teamId)?.let { players ->
                    Log.d(TAG, "Found ${players.size} players for team A")
                    val playerNames =
                            listOf("No player") + players.map { "${it.vorname} ${it.nachname}" }
                    Log.d(TAG, "Player names with 'No player' option: $playerNames")
                    val adapter =
                            ArrayAdapter(
                                    this,
                                    android.R.layout.simple_dropdown_item_1line,
                                    playerNames
                            )
                    teamAPlayer1Select.setAdapter(adapter)
                    teamAPlayer2Select.setAdapter(adapter)

                    // Restore previous selections if they exist
                    if (gameState.teamAPlayer.contains("/")) {
                        val (player1, player2) = gameState.teamAPlayer.split("/")
                        teamAPlayer1Select.setText(player1, false)
                        teamAPlayer2Select.setText(player2, false)
                        teamAPlayer2Select.isEnabled = true
                    } else if (gameState.teamAPlayer.isNotEmpty()) {
                        teamAPlayer1Select.setText(gameState.teamAPlayer, false)
                        teamAPlayer2Select.setText("No player", false)
                        teamAPlayer2Select.isEnabled = true
                    } else {
                        teamAPlayer1Select.setText("No player", false)
                        teamAPlayer2Select.setText("No player", false)
                        teamAPlayer2Select.isEnabled = false
                    }

                    // Set up listeners
                    teamAPlayer1Select.setOnItemClickListener { _, _, position, _ ->
                        val selectedPlayer = playerNames[position]
                        val teamAPlayer2 = teamAPlayer2Select.text.toString()

                        if (selectedPlayer == "No player") {
                            gameState = gameState.copy(teamAPlayer = "")
                            teamAPlayer2Select.setText("No player", false)
                            teamAPlayer2Select.isEnabled = false
                        } else {
                            val combinedPlayers =
                                    if (teamAPlayer2.isNotEmpty() && teamAPlayer2 != "No player") {
                                        "$selectedPlayer / $teamAPlayer2"
                                    } else {
                                        selectedPlayer
                                    }
                            gameState = gameState.copy(teamAPlayer = combinedPlayers)
                            teamAPlayer2Select.isEnabled = true
                        }

                        SettingsManager.saveGameSettings(gameState)
                        saveAndSendScoreUpdate()
                    }

                    teamAPlayer2Select.setOnItemClickListener { _, _, position, _ ->
                        val selectedPlayer = playerNames[position]
                        val teamAPlayer1 = teamAPlayer1Select.text.toString()

                        if (selectedPlayer == "No player") {
                            gameState = gameState.copy(teamAPlayer = teamAPlayer1)
                        } else {
                            val combinedPlayers = "$teamAPlayer1 / $selectedPlayer"
                            gameState = gameState.copy(teamAPlayer = combinedPlayers)
                        }

                        SettingsManager.saveGameSettings(gameState)
                        saveAndSendScoreUpdate()
                    }
                }
            } else {
                Log.d(TAG, "No players found for team A")
            }
        }

        // Update team B players
        teamBId?.let { teamId ->
            Log.d(TAG, "Processing team B players")
            if (TeamDataStore.hasPlayersForTeam(teamId)) {
                TeamDataStore.getTeamPlayers(teamId)?.let { players ->
                    Log.d(TAG, "Found ${players.size} players for team B")
                    val playerNames =
                            listOf("No player") + players.map { "${it.vorname} ${it.nachname}" }
                    Log.d(TAG, "Player names with 'No player' option: $playerNames")
                    val adapter =
                            ArrayAdapter(
                                    this,
                                    android.R.layout.simple_dropdown_item_1line,
                                    playerNames
                            )
                    teamBPlayer1Select.setAdapter(adapter)
                    teamBPlayer2Select.setAdapter(adapter)

                    // Restore previous selections if they exist
                    if (gameState.teamBPlayer.contains("/")) {
                        val (player1, player2) = gameState.teamBPlayer.split("/")
                        teamBPlayer1Select.setText(player1, false)
                        teamBPlayer2Select.setText(player2, false)
                        teamBPlayer2Select.isEnabled = true
                    } else if (gameState.teamBPlayer.isNotEmpty()) {
                        teamBPlayer1Select.setText(gameState.teamBPlayer, false)
                        teamBPlayer2Select.setText("No player", false)
                        teamBPlayer2Select.isEnabled = true
                    } else {
                        teamBPlayer1Select.setText("No player", false)
                        teamBPlayer2Select.setText("No player", false)
                        teamBPlayer2Select.isEnabled = false
                    }

                    // Set up listeners
                    teamBPlayer1Select.setOnItemClickListener { _, _, position, _ ->
                        val selectedPlayer = playerNames[position]
                        val teamBPlayer2 = teamBPlayer2Select.text.toString()

                        if (selectedPlayer == "No player") {
                            gameState = gameState.copy(teamBPlayer = "")
                            teamBPlayer2Select.setText("No player", false)
                            teamBPlayer2Select.isEnabled = false
                        } else {
                            val combinedPlayers =
                                    if (teamBPlayer2.isNotEmpty() && teamBPlayer2 != "No player") {
                                        "$selectedPlayer / $teamBPlayer2"
                                    } else {
                                        selectedPlayer
                                    }
                            gameState = gameState.copy(teamBPlayer = combinedPlayers)
                            teamBPlayer2Select.isEnabled = true
                        }

                        SettingsManager.saveGameSettings(gameState)
                        saveAndSendScoreUpdate()
                    }

                    teamBPlayer2Select.setOnItemClickListener { _, _, position, _ ->
                        val selectedPlayer = playerNames[position]
                        val teamBPlayer1 = teamBPlayer1Select.text.toString()

                        if (selectedPlayer == "No player") {
                            gameState = gameState.copy(teamBPlayer = teamBPlayer1)
                        } else {
                            val combinedPlayers = "$teamBPlayer1 / $selectedPlayer"
                            gameState = gameState.copy(teamBPlayer = combinedPlayers)
                        }

                        SettingsManager.saveGameSettings(gameState)
                        saveAndSendScoreUpdate()
                    }
                }
            } else {
                Log.d(TAG, "No players found for team B")
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.d(TAG, "onActivityResult - requestCode: $requestCode, resultCode: $resultCode")
        if (requestCode == SETTINGS_REQUEST_CODE) {
            Log.d(TAG, "Settings request code matched")
            gameState = SettingsManager.getGameSettings()
            if (resultCode == RESULT_OK) {
                Log.d(TAG, "Result OK - updating scores and players")
                val (savedTeamAScore, savedTeamBScore) = scoreManager.getScores()
                teamAScore.text = savedTeamAScore.toString()
                teamBScore.text = savedTeamBScore.toString()
                updatePlayerDropdowns() // Update player dropdowns when returning from settings
                saveAndSendScoreUpdate()
            } else {
                Log.d(TAG, "Result not OK - resultCode: $resultCode")
            }
        } else {
            Log.d(TAG, "Unknown request code: $requestCode")
        }
    }

    override fun onDestroy() {
        nsdHelper.stopDiscovery()
        super.onDestroy()
    }

    private companion object {
        const val SETTINGS_REQUEST_CODE = 1
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }
}

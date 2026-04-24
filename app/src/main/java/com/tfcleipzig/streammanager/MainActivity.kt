package com.tfcleipzig.streammanager

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.tfcleipzig.streammanager.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var nsdHelper: NsdHelper
    private val scoreUpdater = ScoreUpdater()
    private lateinit var scoreManager: ScoreManager
    private var gameState: GameState = GameState()

    private lateinit var teamADropdownHelper: PlayerDropdownHelper
    private lateinit var teamBDropdownHelper: PlayerDropdownHelper

    private val settingsLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            Log.d(TAG, "Settings result: ${result.resultCode}")
            gameState = SettingsManager.getGameSettings()
            if (result.resultCode == RESULT_OK) {
                val (savedTeamAScore, savedTeamBScore) = scoreManager.getScores()
                binding.teamAScore.text = savedTeamAScore.toString()
                binding.teamBScore.text = savedTeamBScore.toString()
                updatePlayerDropdowns()
                saveAndSendScoreUpdate()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        SettingsManager.init(this)
        initializeManagers()
        initializeViews()
        setupScoreHandlers()
        setupNsdHelper()
        setupWindowInsets()

        binding.settingsButton.setOnClickListener {
            settingsLauncher.launch(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun initializeManagers() {
        scoreManager = ScoreManager(this)
        gameState = SettingsManager.getGameSettings()
    }

    private fun initializeViews() {
        val (savedTeamAScore, savedTeamBScore) = scoreManager.getScores()
        binding.teamAScore.text = savedTeamAScore.toString()
        binding.teamBScore.text = savedTeamBScore.toString()

        teamADropdownHelper =
            PlayerDropdownHelper(
                context = this,
                player1Select = binding.teamAPlayer1Select,
                player2Select = binding.teamAPlayer2Select,
            ) { players ->
                gameState = gameState.copy(teamAPlayer = players)
                SettingsManager.saveGameSettings(gameState)
                saveAndSendScoreUpdate()
            }

        teamBDropdownHelper =
            PlayerDropdownHelper(
                context = this,
                player1Select = binding.teamBPlayer1Select,
                player2Select = binding.teamBPlayer2Select,
            ) { players ->
                gameState = gameState.copy(teamBPlayer = players)
                SettingsManager.saveGameSettings(gameState)
                saveAndSendScoreUpdate()
            }

        updatePlayerDropdowns()
    }

    private fun setupNsdHelper() {
        nsdHelper = NsdHelper(this) { status -> updateStatusDot(status.contains("Connected")) }
        nsdHelper.startDiscovery()
    }

    private fun setupScoreHandlers() {
        setupScoreGestureDetector(binding.teamAScore)
        setupScoreGestureDetector(binding.teamBScore)
    }

    private fun setupScoreGestureDetector(scoreView: TextView) {
        val gestureDetector =
            GestureDetector(
                this,
                object : GestureDetector.SimpleOnGestureListener() {
                    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                        val current = scoreView.text.toString().toIntOrNull() ?: 0
                        scoreView.text = (current + 1).toString()
                        saveAndSendScoreUpdate()
                        return true
                    }

                    override fun onDoubleTap(e: MotionEvent): Boolean {
                        val current = scoreView.text.toString().toIntOrNull() ?: 0
                        if (current > 0) {
                            scoreView.text = (current - 1).toString()
                            saveAndSendScoreUpdate()
                        }
                        return true
                    }
                },
            )

        scoreView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }
    }

    private fun saveAndSendScoreUpdate() {
        val teamAValue =
            binding.teamAScore.text
                .toString()
                .toIntOrNull() ?: 0
        val teamBValue =
            binding.teamBScore.text
                .toString()
                .toIntOrNull() ?: 0
        scoreManager.saveScores(teamAValue, teamBValue)

        val (host, port) = nsdHelper.getConnectionDetails()
        Log.d(TAG, "saveAndSendScoreUpdate: host=$host, port=$port, scores=$teamAValue-$teamBValue")
        if (host != null && port != null) {
            scoreUpdater.updateScores(
                host = host,
                port = port,
                teamAScore = teamAValue,
                teamBScore = teamBValue,
                gameState = gameState,
            )
        } else {
            Log.w(TAG, "No server connection - score update not sent")
        }
    }

    private fun updateStatusDot(isConnected: Boolean) {
        runOnUiThread {
            binding.statusDot.setBackgroundColor(
                ContextCompat.getColor(
                    this,
                    if (isConnected) {
                        R.color.status_connected
                    } else {
                        R.color.status_disconnected
                    },
                ),
            )
        }
    }

    private fun updatePlayerDropdowns() {
        val teams = TeamDataStore.getTeams()
        val teamAId = teams.find { it.teamname == gameState.teamA }?.teamId
        val teamBId = teams.find { it.teamname == gameState.teamB }?.teamId

        teamADropdownHelper.setup(teamAId, gameState.teamAPlayer)
        teamBDropdownHelper.setup(teamBId, gameState.teamBPlayer)
    }

    override fun onDestroy() {
        nsdHelper.stopDiscovery()
        super.onDestroy()
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}

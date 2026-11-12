package com.tfcleipzig.streammanager

import android.animation.ObjectAnimator
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.ColorRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import com.tfcleipzig.streammanager.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var nsdHelper: NsdHelper
    private lateinit var scoreUpdater: ScoreUpdater
    private lateinit var scoreManager: ScoreManager
    private lateinit var scoreBoard: ScoreBoard
    private var gameState: GameState = GameState()
    private var serverStatus: ServerStatus = ServerStatus.NOT_FOUND

    private val touchFilter = GoalTouchFilter()
    private val flashAnimators = mutableMapOf<Side, ObjectAnimator>()
    private var vibrator: Vibrator? = null

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
                scoreBoard.reset(savedTeamAScore, savedTeamBScore)
                renderScores()
                updatePlayerDropdowns()
                saveAndSendScore()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        SettingsManager.init(this)
        vibrator =
            getSystemService(VibratorManager::class.java)
                ?.defaultVibrator
                ?.takeIf { it.hasVibrator() }
        scoreUpdater = ScoreUpdater { synced -> binding.syncWarning.isVisible = !synced }
        initializeManagers()
        initializeViews()
        setupScoreHandlers()
        setupNsdHelper()
        setupWindowInsets()

        binding.settingsButton.setOnClickListener {
            settingsLauncher.launch(Intent(this, SettingsActivity::class.java))
        }
        binding.undoButton.setOnClickListener { undoLastGoal() }
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
    }

    private fun initializeManagers() {
        scoreManager = ScoreManager(this)
        gameState = SettingsManager.getGameSettings()
        val (savedTeamAScore, savedTeamBScore) = scoreManager.getScores()
        scoreBoard = ScoreBoard(savedTeamAScore, savedTeamBScore)
    }

    private fun initializeViews() {
        renderScores()

        teamADropdownHelper =
            PlayerDropdownHelper(
                context = this,
                player1Select = binding.teamAPlayer1Select,
                player2Select = binding.teamAPlayer2Select,
            ) { players ->
                gameState = gameState.copy(teamAPlayer = players)
                SettingsManager.saveGameSettings(gameState)
                saveAndSendScore()
            }

        teamBDropdownHelper =
            PlayerDropdownHelper(
                context = this,
                player1Select = binding.teamBPlayer1Select,
                player2Select = binding.teamBPlayer2Select,
            ) { players ->
                gameState = gameState.copy(teamBPlayer = players)
                SettingsManager.saveGameSettings(gameState)
                saveAndSendScore()
            }

        updatePlayerDropdowns()
    }

    private fun setupNsdHelper() {
        nsdHelper = NsdHelper(this) { status -> runOnUiThread { onServerStatus(status) } }
        nsdHelper.startDiscovery()
    }

    private fun onServerStatus(status: ServerStatus) {
        val wasRunning = serverStatus == ServerStatus.RUNNING
        serverStatus = status
        updateStatusDot(status)

        val (host, port) = nsdHelper.getConnectionDetails()
        scoreUpdater.endpoint = if (host != null && port != null) Endpoint(host, port) else null

        // A server that just started knows nothing yet; give it the current score.
        if (status == ServerStatus.RUNNING && !wasRunning) {
            saveAndSendScore()
        }
    }

    private fun setupScoreHandlers() {
        setupScoreView(binding.teamAScore, Side.A)
        setupScoreView(binding.teamBScore, Side.B)
    }

    /**
     * A goal counts the moment a finger lands. There is no tap, double-tap or long-press
     * detection to wait for, and moving the finger afterwards does not matter. Corrections go
     * through the Undo button.
     */
    private fun setupScoreView(
        scoreView: TextView,
        side: Side,
    ) {
        scoreView.setOnClickListener { scoreGoal(side) }
        scoreView.setOnTouchListener { view, event ->
            val isDown =
                event.actionMasked == MotionEvent.ACTION_DOWN ||
                    event.actionMasked == MotionEvent.ACTION_POINTER_DOWN
            if (isDown && touchFilter.accept(side, event.eventTime)) {
                view.performClick()
            }
            true
        }
    }

    private fun scoreGoal(side: Side) {
        scoreBoard.goal(side)
        renderScores()
        giveFeedback(side, R.color.goal_flash, VibrationEffect.EFFECT_HEAVY_CLICK)
        saveAndSendScore()
    }

    private fun undoLastGoal() {
        val side = scoreBoard.undo() ?: return
        renderScores()
        giveFeedback(side, R.color.undo_flash, VibrationEffect.EFFECT_DOUBLE_CLICK)
        saveAndSendScore()
    }

    private fun renderScores() {
        binding.teamAScore.text = scoreBoard.teamA.toString()
        binding.teamBScore.text = scoreBoard.teamB.toString()
        binding.teamAScore.contentDescription = getString(R.string.team_a_score, scoreBoard.teamA)
        binding.teamBScore.contentDescription = getString(R.string.team_b_score, scoreBoard.teamB)
        binding.undoButton.isEnabled = scoreBoard.canUndo
    }

    private fun giveFeedback(
        side: Side,
        @ColorRes flashColor: Int,
        vibrationEffect: Int,
    ) {
        vibrator?.vibrate(VibrationEffect.createPredefined(vibrationEffect))

        val view = scoreView(side)
        flashAnimators[side]?.cancel()
        flashAnimators[side] =
            ObjectAnimator
                .ofArgb(view, "backgroundColor", ContextCompat.getColor(this, flashColor), Color.TRANSPARENT)
                .setDuration(FLASH_DURATION_MS)
                .also { it.start() }
    }

    private fun scoreView(side: Side): View =
        when (side) {
            Side.A -> binding.teamAScore
            Side.B -> binding.teamBScore
        }

    private fun saveAndSendScore() {
        scoreManager.saveScores(scoreBoard.teamA, scoreBoard.teamB)
        scoreUpdater.submit(ScoreSnapshot(scoreBoard.teamA, scoreBoard.teamB, gameState))
    }

    private fun updateStatusDot(status: ServerStatus) {
        val color =
            when (status) {
                ServerStatus.RUNNING -> R.color.status_connected
                ServerStatus.STARTED -> R.color.status_started
                ServerStatus.NOT_FOUND -> R.color.status_disconnected
            }
        binding.statusDot.setBackgroundColor(ContextCompat.getColor(this, color))
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
        scoreUpdater.close()
        super.onDestroy()
    }

    /** Full screen, so the system bars and the edge back gesture stay out of the score halves. */
    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars =
                insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
                )
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val FLASH_DURATION_MS = 300L
    }
}

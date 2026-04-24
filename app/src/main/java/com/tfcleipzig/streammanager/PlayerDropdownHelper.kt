package com.tfcleipzig.streammanager

import android.content.Context
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView

class PlayerDropdownHelper(
    private val context: Context,
    private val player1Select: AutoCompleteTextView,
    private val player2Select: AutoCompleteTextView,
    private val onPlayersChanged: (String) -> Unit,
) {
    companion object {
        private const val NO_PLAYER = "No player"
    }

    private var playerNames: List<String> = emptyList()

    fun setup(
        teamId: String?,
        currentPlayerString: String,
    ) {
        if (teamId == null || !TeamDataStore.hasPlayersForTeam(teamId)) {
            player1Select.setAdapter(null)
            player2Select.setAdapter(null)
            return
        }

        val players = TeamDataStore.getTeamPlayers(teamId) ?: return
        playerNames = listOf(NO_PLAYER) + players.map { "${it.vorname} ${it.nachname}" }

        val adapter =
            ArrayAdapter(
                context,
                android.R.layout.simple_dropdown_item_1line,
                playerNames,
            )
        player1Select.setAdapter(adapter)
        player2Select.setAdapter(adapter)

        restoreSelections(currentPlayerString)
        setupListeners()
    }

    private fun restoreSelections(playerString: String) {
        when {
            playerString.contains("/") -> {
                val (player1, player2) = playerString.split("/", limit = 2).map { it.trim() }
                player1Select.setText(player1, false)
                player2Select.setText(player2, false)
                player2Select.isEnabled = true
            }
            playerString.isNotEmpty() -> {
                player1Select.setText(playerString, false)
                player2Select.setText(NO_PLAYER, false)
                player2Select.isEnabled = true
            }
            else -> {
                player1Select.setText(NO_PLAYER, false)
                player2Select.setText(NO_PLAYER, false)
                player2Select.isEnabled = false
            }
        }
    }

    private fun setupListeners() {
        player1Select.setOnItemClickListener { _, _, position, _ ->
            val selected = playerNames[position]
            val player2Text = player2Select.text.toString()

            if (selected == NO_PLAYER) {
                player2Select.setText(NO_PLAYER, false)
                player2Select.isEnabled = false
                onPlayersChanged("")
            } else {
                player2Select.isEnabled = true
                val combined =
                    if (player2Text.isNotEmpty() && player2Text != NO_PLAYER) {
                        "$selected / $player2Text"
                    } else {
                        selected
                    }
                onPlayersChanged(combined)
            }
        }

        player2Select.setOnItemClickListener { _, _, position, _ ->
            val selected = playerNames[position]
            val player1Text = player1Select.text.toString()

            if (selected == NO_PLAYER) {
                onPlayersChanged(player1Text)
            } else {
                onPlayersChanged("$player1Text / $selected")
            }
        }
    }
}

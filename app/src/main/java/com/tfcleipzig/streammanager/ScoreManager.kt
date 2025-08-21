package com.tfcleipzig.streammanager

import android.content.Context
import android.content.SharedPreferences

class ScoreManager(context: Context) {
    private val prefs: SharedPreferences =
            context.getSharedPreferences("scores", Context.MODE_PRIVATE)

    fun saveScores(leftScore: Int, rightScore: Int) {
        prefs.edit().apply {
            putInt("left_score", leftScore)
            putInt("right_score", rightScore)
            apply()
        }
    }

    fun getScores(): Pair<Int, Int> {
        val leftScore = prefs.getInt("left_score", 0)
        val rightScore = prefs.getInt("right_score", 0)
        return Pair(leftScore, rightScore)
    }

    fun resetScores() {
        prefs.edit().apply {
            putInt("left_score", 0)
            putInt("right_score", 0)
            apply()
        }
    }

    fun clearScores() {
        prefs.edit().clear().apply()
    }
}

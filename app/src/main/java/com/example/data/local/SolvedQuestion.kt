package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "solved_questions")
data class SolvedQuestion(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val subject: String = "सामान्य (General)",
    val questionText: String = "",
    val imagePath: String? = null,
    val solutionText: String = "",
    val shortAnswer: String = "",
    val isFavorite: Boolean = false
)

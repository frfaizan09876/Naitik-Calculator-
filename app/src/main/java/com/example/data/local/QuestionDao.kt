package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface QuestionDao {
    @Query("SELECT * FROM solved_questions ORDER BY timestamp DESC")
    fun getAllQuestions(): Flow<List<SolvedQuestion>>

    @Query("SELECT * FROM solved_questions WHERE id = :id LIMIT 1")
    suspend fun getQuestionById(id: Long): SolvedQuestion?

    @Query("SELECT * FROM solved_questions WHERE subject = :subject ORDER BY timestamp DESC")
    fun getQuestionsBySubject(subject: String): Flow<List<SolvedQuestion>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuestion(question: SolvedQuestion): Long

    @Update
    suspend fun updateQuestion(question: SolvedQuestion)

    @Delete
    suspend fun deleteQuestion(question: SolvedQuestion)

    @Query("DELETE FROM solved_questions WHERE id = :id")
    suspend fun deleteQuestionById(id: Long)

    @Query("DELETE FROM solved_questions")
    suspend fun clearAll()
}

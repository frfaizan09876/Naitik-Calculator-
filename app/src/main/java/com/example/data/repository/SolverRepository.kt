package com.example.data.repository

import android.content.Context
import android.net.Uri
import com.example.BuildConfig
import com.example.data.api.GeminiApiClient
import com.example.data.local.AppDatabase
import com.example.data.local.SolvedQuestion
import com.example.data.model.Content
import com.example.data.model.GenerateContentRequest
import com.example.data.model.GenerationConfig
import com.example.data.model.InlineData
import com.example.data.model.Part
import com.example.util.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class SolverRepository(private val context: Context) {

    private val db = AppDatabase.getDatabase(context)
    private val questionDao = db.questionDao()
    private val apiService = GeminiApiClient.service

    val allQuestions: Flow<List<SolvedQuestion>> = questionDao.getAllQuestions()

    suspend fun getQuestionById(id: Long): SolvedQuestion? = questionDao.getQuestionById(id)

    suspend fun solveQuestion(
        imageUri: Uri?,
        questionText: String,
        subject: String,
        promptStyle: String,
        customApiKey: String? = null
    ): Result<SolvedQuestion> = withContext(Dispatchers.IO) {
        try {
            val effectiveKey = when {
                !customApiKey.isNullOrBlank() -> customApiKey.trim()
                BuildConfig.GEMINI_API_KEY.isNotBlank() && BuildConfig.GEMINI_API_KEY != "MY_GEMINI_API_KEY" -> BuildConfig.GEMINI_API_KEY
                else -> ""
            }

            if (effectiveKey.isBlank()) {
                return@withContext Result.failure(
                    IllegalStateException("Gemini API Key missing. Please provide your Gemini API Key in Settings.")
                )
            }

            val parts = mutableListOf<Part>()

            // Build system & prompt instructions
            val promptBuilder = StringBuilder()
            promptBuilder.append("You are 'GyanLens' (ज्ञान लेंस), a friendly, highly intelligent teacher and homework question solver.\n")
            promptBuilder.append("A student has asked for your help with a question.\n")
            if (subject != "सभी विषय (All)") {
                promptBuilder.append("Target Subject: $subject\n")
            }
            if (promptStyle.isNotBlank()) {
                promptBuilder.append("Student's Preferred Style: $promptStyle\n")
            }
            if (questionText.isNotBlank()) {
                promptBuilder.append("Student's Note/Question: \"$questionText\"\n")
            }

            promptBuilder.append("""
                Analyze the question thoroughly (from the attached image and/or text).
                Please structure your solution in simple, easy-to-understand Hindi and English (Hinglish where helpful):
                
                ### 📌 संक्षिप्त उत्तर (Direct Answer)
                [Provide the final answer clearly in 1-2 lines]
                
                ### 🔍 विषय और अध्याय (Subject & Concept)
                [Identify the subject, topic, and core concept]
                
                ### 📝 चरण-दर-चरण समाधान (Step-by-Step Solution)
                [Break down the solution step by step with clear explanations and formulas]
                
                ### 💡 मुख्य सूत्र व नियम (Key Formulas & Rules)
                [List any formula, theorem, or grammatical rule applied]
                
                ### ✨ समझने का आसान तरीका (Pro Tip / Easy Trick)
                [A short practical tip to solve similar questions easily]
            """.trimIndent())

            parts.add(Part(text = promptBuilder.toString()))

            // If an image was supplied, convert to Base64 inlineData
            var savedImagePath: String? = null
            if (imageUri != null) {
                val base64 = ImageUtils.uriToBase64(context, imageUri)
                if (base64 != null) {
                    parts.add(
                        Part(
                            inlineData = InlineData(
                                mimeType = "image/jpeg",
                                data = base64
                            )
                        )
                    )
                }
                savedImagePath = ImageUtils.saveImageToInternalStorage(context, imageUri)
            }

            val request = GenerateContentRequest(
                contents = listOf(Content(parts = parts)),
                generationConfig = GenerationConfig(
                    temperature = 0.2f,
                    topP = 0.95f,
                    maxOutputTokens = 3000
                )
            )

            val response = apiService.generateContent(effectiveKey, request)
            val candidate = response.candidates?.firstOrNull()
            val solutionText = candidate?.content?.parts?.mapNotNull { it.text }?.joinToString("\n") ?: ""

            if (solutionText.isBlank()) {
                val blockReason = response.promptFeedback?.blockReason ?: "No response generated"
                return@withContext Result.failure(Exception("Gemini returned empty answer ($blockReason)"))
            }

            // Extract short answer preview from solution
            val shortAnswer = extractShortAnswer(solutionText)

            val solved = SolvedQuestion(
                timestamp = System.currentTimeMillis(),
                subject = subject,
                questionText = questionText.ifBlank { "फोटो सवाल (Photo Question)" },
                imagePath = savedImagePath,
                solutionText = solutionText,
                shortAnswer = shortAnswer
            )

            val savedId = questionDao.insertQuestion(solved)
            val finalSaved = solved.copy(id = savedId)

            Result.success(finalSaved)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun askFollowUp(
        previousSolution: String,
        followUpDoubt: String,
        customApiKey: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val effectiveKey = when {
                !customApiKey.isNullOrBlank() -> customApiKey.trim()
                BuildConfig.GEMINI_API_KEY.isNotBlank() && BuildConfig.GEMINI_API_KEY != "MY_GEMINI_API_KEY" -> BuildConfig.GEMINI_API_KEY
                else -> ""
            }

            if (effectiveKey.isBlank()) {
                return@withContext Result.failure(IllegalStateException("API Key missing"))
            }

            val prompt = """
                You are GyanLens, helping a student clear a doubt on a previously solved question.
                
                [PREVIOUS SOLUTION]:
                $previousSolution
                
                [STUDENT'S DOUBT / QUESTION]:
                "$followUpDoubt"
                
                Please answer the student's doubt directly, kindly, and step-by-step in easy Hindi/English.
            """.trimIndent()

            val request = GenerateContentRequest(
                contents = listOf(
                    Content(parts = listOf(Part(text = prompt)))
                ),
                generationConfig = GenerationConfig(temperature = 0.3f)
            )

            val response = apiService.generateContent(effectiveKey, request)
            val answer = response.candidates?.firstOrNull()?.content?.parts?.mapNotNull { it.text }?.joinToString("\n") ?: ""

            if (answer.isNotBlank()) {
                Result.success(answer)
            } else {
                Result.failure(Exception("Could not generate follow-up answer"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteQuestion(id: Long) = questionDao.deleteQuestionById(id)

    suspend fun clearHistory() = questionDao.clearAll()

    private fun extractShortAnswer(solution: String): String {
        val lines = solution.lines()
        var foundHeader = false
        val answerBuilder = StringBuilder()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.contains("संक्षिप्त उत्तर") || trimmed.contains("Direct Answer")) {
                foundHeader = true
                continue
            }
            if (foundHeader) {
                if (trimmed.startsWith("#") || trimmed.startsWith("---")) break
                if (trimmed.isNotBlank()) {
                    answerBuilder.append(trimmed).append(" ")
                    if (answerBuilder.length > 150) break
                }
            }
        }

        val result = answerBuilder.toString().trim()
        return if (result.isNotBlank()) result else lines.firstOrNull { it.isNotBlank() && !it.startsWith("#") } ?: "हल उपलब्ध है"
    }
}

package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.SolvedQuestion
import com.example.data.repository.SolverRepository
import com.example.util.TtsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class Screen {
    object Home : Screen()
    data class Solution(val question: SolvedQuestion) : Screen()
    object History : Screen()
    object Settings : Screen()
}

class SolverViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SolverRepository(application)
    private val prefs: SharedPreferences = application.getSharedPreferences("gyanlens_prefs", Context.MODE_PRIVATE)

    val ttsManager = TtsManager(application)

    val history: StateFlow<List<SolvedQuestion>> = repository.allQuestions
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _currentScreen = MutableStateFlow<Screen>(Screen.Home)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    private val _selectedImageUri = MutableStateFlow<Uri?>(null)
    val selectedImageUri: StateFlow<Uri?> = _selectedImageUri.asStateFlow()

    private val _questionText = MutableStateFlow("")
    val questionText: StateFlow<String> = _questionText.asStateFlow()

    private val _selectedSubject = MutableStateFlow("सभी विषय (All)")
    val selectedSubject: StateFlow<String> = _selectedSubject.asStateFlow()

    private val _selectedPromptStyle = MutableStateFlow("कदम दर कदम समझाएं 📝")
    val selectedPromptStyle: StateFlow<String> = _selectedPromptStyle.asStateFlow()

    private val _isSolving = MutableStateFlow(false)
    val isSolving: StateFlow<Boolean> = _isSolving.asStateFlow()

    private val _activeSolution = MutableStateFlow<SolvedQuestion?>(null)
    val activeSolution: StateFlow<SolvedQuestion?> = _activeSolution.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _customApiKey = MutableStateFlow(prefs.getString("custom_api_key", "") ?: "")
    val customApiKey: StateFlow<String> = _customApiKey.asStateFlow()

    // Follow-up Q&A for doubt clearing
    private val _followUpList = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val followUpList: StateFlow<List<Pair<String, String>>> = _followUpList.asStateFlow()

    private val _isAnsweringFollowUp = MutableStateFlow(false)
    val isAnsweringFollowUp: StateFlow<Boolean> = _isAnsweringFollowUp.asStateFlow()

    fun navigateTo(screen: Screen) {
        _currentScreen.value = screen
    }

    fun setSelectedImage(uri: Uri?) {
        _selectedImageUri.value = uri
    }

    fun setQuestionText(text: String) {
        _questionText.value = text
    }

    fun appendVoiceText(text: String) {
        val current = _questionText.value
        _questionText.value = if (current.isBlank()) text else "$current $text"
    }

    fun setSelectedSubject(subject: String) {
        _selectedSubject.value = subject
    }

    fun setSelectedPromptStyle(style: String) {
        _selectedPromptStyle.value = style
    }

    fun setCustomApiKey(key: String) {
        _customApiKey.value = key
        prefs.edit().putString("custom_api_key", key).apply()
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun solve() {
        if (_selectedImageUri.value == null && _questionText.value.isBlank()) {
            _errorMessage.value = "कृपया सवाल की फोटो खींचें या सवाल लिखें/बोलें!"
            return
        }

        viewModelScope.launch {
            _isSolving.value = true
            _errorMessage.value = null

            val result = repository.solveQuestion(
                imageUri = _selectedImageUri.value,
                questionText = _questionText.value,
                subject = _selectedSubject.value,
                promptStyle = _selectedPromptStyle.value,
                customApiKey = _customApiKey.value
            )

            _isSolving.value = false

            result.fold(
                onSuccess = { solved ->
                    _activeSolution.value = solved
                    _followUpList.value = emptyList()
                    _currentScreen.value = Screen.Solution(solved)
                },
                onFailure = { error ->
                    _errorMessage.value = error.localizedMessage ?: "हल करने में समस्या आई। कृपया पुनः प्रयास करें।"
                }
            )
        }
    }

    fun askFollowUpDoubt(doubt: String) {
        val current = _activeSolution.value ?: return
        if (doubt.isBlank()) return

        viewModelScope.launch {
            _isAnsweringFollowUp.value = true
            val result = repository.askFollowUp(
                previousSolution = current.solutionText,
                followUpDoubt = doubt,
                customApiKey = _customApiKey.value
            )
            _isAnsweringFollowUp.value = false

            result.fold(
                onSuccess = { answer ->
                    _followUpList.value = _followUpList.value + (doubt to answer)
                },
                onFailure = { error ->
                    _errorMessage.value = "डाउट का उत्तर नहीं मिल सका: ${error.message}"
                }
            )
        }
    }

    fun viewPastSolution(question: SolvedQuestion) {
        _activeSolution.value = question
        _followUpList.value = emptyList()
        _currentScreen.value = Screen.Solution(question)
    }

    fun deleteHistoryItem(id: Long) {
        viewModelScope.launch {
            repository.deleteQuestion(id)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    fun readSolutionAloud(text: String) {
        ttsManager.speak(text)
    }

    fun stopAudio() {
        ttsManager.stop()
    }

    fun resetInput() {
        _selectedImageUri.value = null
        _questionText.value = ""
    }

    override fun onCleared() {
        super.onCleared()
        ttsManager.shutdown()
    }
}

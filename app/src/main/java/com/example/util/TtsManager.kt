package com.example.util

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class TtsManager(private val context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    init {
        tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
            // Try Hindi first if available, else fall back to English/default
            val hindiLocale = Locale("hi", "IN")
            val result = tts?.setLanguage(hindiLocale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setLanguage(Locale.getDefault())
            }

            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _isSpeaking.value = true
                }

                override fun onDone(utteranceId: String?) {
                    _isSpeaking.value = false
                }

                override fun onError(utteranceId: String?) {
                    _isSpeaking.value = false
                }
            })
        }
    }

    fun speak(text: String, preferHindi: Boolean = true) {
        if (!isInitialized || tts == null) return

        if (_isSpeaking.value) {
            stop()
            return
        }

        // Clean markdown formatting for smoother speech
        val cleanedText = cleanTextForSpeech(text)

        if (preferHindi) {
            val hiLocale = Locale("hi", "IN")
            if (tts?.isLanguageAvailable(hiLocale) != TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.language = hiLocale
            }
        }

        tts?.speak(cleanedText, TextToSpeech.QUEUE_FLUSH, null, "gyanlens_speech_id")
    }

    fun stop() {
        tts?.stop()
        _isSpeaking.value = false
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    private fun cleanTextForSpeech(input: String): String {
        return input
            .replace(Regex("[#*`_~>\\[\\]()]"), " ")
            .replace(Regex("- "), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}

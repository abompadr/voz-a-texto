package com.vozatexto.app.ui

import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vozatexto.app.util.FileSaver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class UiState(
    val status: Status = Status.READY,
    val transcription: String = "",
    val partialText: String = "",
    val snackbarMessage: String? = null,
    val savedFiles: List<File> = emptyList()
)

enum class Status { READY, LISTENING, PROCESSING, ERROR }

class TranscriptionViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var recognizer: SpeechRecognizer? = null

    init {
        refreshFiles()
    }

    fun startListening() {
        val ctx = getApplication<Application>()
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(ctx).also { sr ->
            sr.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    _state.update { it.copy(status = Status.LISTENING, partialText = "") }
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    _state.update { it.copy(status = Status.PROCESSING) }
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull() ?: return
                    _state.update { it.copy(partialText = partial) }
                }
                override fun onResults(results: Bundle?) {
                    val text = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull() ?: ""
                    _state.update {
                        it.copy(
                            status = Status.READY,
                            transcription = buildString {
                                if (it.transcription.isNotEmpty()) append(it.transcription).append(" ")
                                append(text)
                            },
                            partialText = ""
                        )
                    }
                }
                override fun onError(error: Int) {
                    val msg = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH,
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> ctx.getString(
                            com.vozatexto.app.R.string.error_no_speech
                        )
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
                        SpeechRecognizer.ERROR_SERVER,
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> ctx.getString(
                            com.vozatexto.app.R.string.error_unavailable
                        )
                        else -> ctx.getString(com.vozatexto.app.R.string.error_generic, error.toString())
                    }
                    _state.update { it.copy(status = Status.ERROR, snackbarMessage = msg, partialText = "") }
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "es")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        recognizer?.startListening(intent)
    }

    fun stopListening() {
        recognizer?.stopListening()
        _state.update { it.copy(status = Status.PROCESSING) }
    }

    fun save() {
        val text = _state.value.transcription.trim()
        if (text.isEmpty()) {
            _state.update { it.copy(snackbarMessage = getApplication<Application>().getString(com.vozatexto.app.R.string.nothing_to_save)) }
            return
        }
        viewModelScope.launch {
            val filename = FileSaver.save(getApplication(), text)
            refreshFiles()
            _state.update {
                it.copy(snackbarMessage = getApplication<Application>().getString(com.vozatexto.app.R.string.saved_ok, filename))
            }
        }
    }

    fun clearTranscription() {
        _state.update { it.copy(transcription = "", partialText = "", status = Status.READY) }
    }

    fun snackbarShown() {
        _state.update { it.copy(snackbarMessage = null) }
    }

    private fun refreshFiles() {
        _state.update { it.copy(savedFiles = FileSaver.listFiles(getApplication())) }
    }

    override fun onCleared() {
        recognizer?.destroy()
        super.onCleared()
    }
}

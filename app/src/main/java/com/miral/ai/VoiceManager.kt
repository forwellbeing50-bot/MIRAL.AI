package com.miral.ai

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

class VoiceManager(private val context: Context) {

    var isListening by mutableStateOf(false)
    var isSpeaking by mutableStateOf(false)
    var conversationMode by mutableStateOf(false)
    var statusText by mutableStateOf("Mic dabayein aur bolein")
    var voiceNote by mutableStateOf("")

    private val mainHandler = Handler(Looper.getMainLooper())
    private val idleText = "Mic dabayein aur bolein"
    private val maxSilentRounds = 3
    private var silentRounds = 0

    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var urduVoiceFound = false
    private var onResult: ((String) -> Unit)? = null

    init {
        tts = TextToSpeech(context) { status ->
            mainHandler.post {
                val engine = tts
                if (status == TextToSpeech.SUCCESS && engine != null) {
                    ttsReady = true
                    val result = engine.setLanguage(Locale("ur", "PK"))
                    urduVoiceFound = result >= TextToSpeech.LANG_AVAILABLE
                    if (urduVoiceFound) {
                        try {
                            val urduVoices = engine.voices
                                ?.filter { it.locale.language == "ur" }
                                .orEmpty()
                            val pick = urduVoices.firstOrNull { it.name.contains("female", true) }
                                ?: urduVoices.firstOrNull()
                            if (pick != null) engine.voice = pick
                        } catch (e: Exception) {
                            // voice choose na ho sake to default Urdu voice chalegi
                        }
                        engine.setPitch(1.1f)
                        engine.setSpeechRate(0.95f)
                        voiceNote = ""
                    } else {
                        engine.setLanguage(Locale.getDefault())
                        voiceNote = "Urdu voice install nahi hai. Phone Settings > Language > " +
                            "Text-to-speech > Google > Install voice data > Urdu"
                    }
                } else {
                    voiceNote = "Text-to-speech start nahi ho saka."
                }
            }
        }
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                mainHandler.post {
                    isSpeaking = true
                    statusText = "Main bol rahi hoon..."
                }
            }

            override fun onDone(utteranceId: String?) {
                mainHandler.post { finishedSpeaking() }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                mainHandler.post { finishedSpeaking() }
            }
        })
    }

    private fun finishedSpeaking() {
        isSpeaking = false
        if (conversationMode) {
            statusText = "Main sun rahi hoon..."
            scheduleRestart(500)
        } else {
            statusText = idleText
        }
    }

    private fun scheduleRestart(delayMs: Long) {
        mainHandler.postDelayed({ restartListening() }, delayMs)
    }

    private fun restartListening() {
        if (conversationMode && !isListening && !isSpeaking) {
            val callback = onResult ?: return
            startListening(callback)
        }
    }

    fun speak(roman: String, urdu: String) {
        val engine = tts
        if (engine == null || !ttsReady) {
            if (conversationMode) scheduleRestart(500)
            return
        }
        val text = if (urduVoiceFound) urdu else roman
        isSpeaking = true
        statusText = "Main bol rahi hoon..."
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "miral_utterance")
    }

    fun stopSpeaking() {
        tts?.stop()
        isSpeaking = false
    }

    /** Ek baar mic dabane par baat-cheet mode shuru. */
    fun startConversation(onResult: (String) -> Unit) {
        conversationMode = true
        silentRounds = 0
        startListening(onResult)
    }

    /** Baat-cheet mode rokta hai: sunna aur bolna dono band. */
    fun stopConversation() {
        conversationMode = false
        silentRounds = 0
        mainHandler.removeCallbacksAndMessages(null)
        recognizer?.cancel()
        isListening = false
        stopSpeaking()
        statusText = idleText
    }

    fun startListening(onResult: (String) -> Unit) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            conversationMode = false
            statusText = "Is phone par voice recognition maujood nahi (Google app chahiye)"
            return
        }
        stopSpeaking()
        this.onResult = onResult
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    statusText = "Main sun rahi hoon..."
                }

                override fun onBeginningOfSpeech() {}

                override fun onRmsChanged(rmsdB: Float) {}

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    statusText = "Samajh rahi hoon..."
                }

                override fun onError(error: Int) {
                    isListening = false
                    if (!conversationMode && error == SpeechRecognizer.ERROR_CLIENT) return
                    when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH,
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                            if (conversationMode) {
                                silentRounds++
                                if (silentRounds >= maxSilentRounds) {
                                    conversationMode = false
                                    statusText = "Kuch sunai nahi diya, is liye ruk gayi. Mic dabake dobara shuru karein"
                                } else {
                                    statusText = "Main intezar kar rahi hoon..."
                                    scheduleRestart(800)
                                }
                            } else {
                                statusText = "Kuch sunai nahi diya. Mic dabake dobara bolein"
                            }
                        }

                        else -> {
                            conversationMode = false
                            statusText = when (error) {
                                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                                    "Mic ki ijazat chahiye. Phone Settings > Apps > MIRAL AI > Permissions"
                                SpeechRecognizer.ERROR_NETWORK,
                                SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                                    "Internet ka masla hai. Check karke dobara koshish karein"
                                else -> "Voice mein masla aaya (code $error). Dobara koshish karein"
                            }
                        }
                    }
                }

                override fun onResults(results: Bundle?) {
                    isListening = false
                    val text = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                    if (text.isNullOrBlank()) {
                        if (conversationMode) {
                            silentRounds++
                            if (silentRounds >= maxSilentRounds) {
                                conversationMode = false
                                statusText = "Kuch sunai nahi diya, is liye ruk gayi. Mic dabake dobara shuru karein"
                            } else {
                                scheduleRestart(800)
                            }
                        } else {
                            statusText = "Samajh nahi aaya. Dobara bolein"
                        }
                    } else {
                        silentRounds = 0
                        statusText = if (conversationMode) "Soch rahi hoon..." else idleText
                        this@VoiceManager.onResult?.invoke(text)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {}

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ur-PK")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        isListening = true
        statusText = "Main sun rahi hoon..."
        recognizer?.startListening(intent)
    }

    fun stopListening() {
        stopConversation()
    }

    fun shutdown() {
        conversationMode = false
        mainHandler.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        recognizer = null
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}

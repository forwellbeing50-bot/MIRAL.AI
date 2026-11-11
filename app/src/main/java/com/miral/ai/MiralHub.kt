package com.miral.ai

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object MiralHub {

    private var voiceInstance: VoiceManager? = null

    val messages = mutableStateListOf(
        ChatMessage("Assalam o Alaikum! Main Miral hoon. Mujh se kuch bhi poochein.", true)
    )

    var lastHeard by mutableStateOf("")
    var lastReply by mutableStateOf("")
    var backgroundRunning by mutableStateOf(false)
    var thinking by mutableStateOf(false)

    fun voice(context: Context): VoiceManager {
        MiralSettings.init(context)
        val existing = voiceInstance
        if (existing != null) return existing
        val created = VoiceManager(context.applicationContext)
        voiceInstance = created
        return created
    }

    fun peekVoice(): VoiceManager? = voiceInstance

    fun releaseVoice() {
        voiceInstance?.shutdown()
        voiceInstance = null
    }

    fun handleText(text: String, speakReply: Boolean) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        messages.add(ChatMessage(clean, false))

        if (!MiralBrain.isConfigured()) {
            val fallback = miralReply(clean)
            deliver(
                fallback.roman + "\n(Brain abhi set nahi hai: Chat ke upar ⚙ Settings mein jayen)",
                fallback,
                speakReply
            )
            return
        }

        thinking = true
        MiralBrain.ask(messages.toList()) { reply, error ->
            thinking = false
            if (reply != null) {
                deliver(reply.roman, reply, speakReply)
            } else {
                val fallback = miralReply(clean)
                deliver(
                    fallback.roman + "\n(Brain se rabta nahi hua: $error)",
                    fallback,
                    speakReply
                )
            }
        }
    }

    private fun deliver(display: String, reply: Reply, speakReply: Boolean) {
        messages.add(ChatMessage(display, true))
        lastReply = display
        val voice = voiceInstance
        if (speakReply && voice != null && voice.conversationMode) {
            voice.speak(reply.roman, reply.urdu)
        }
    }
}

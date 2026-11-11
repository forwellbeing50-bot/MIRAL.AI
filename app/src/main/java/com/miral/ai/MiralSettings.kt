package com.miral.ai

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object MiralSettings {

    private var prefs: SharedPreferences? = null

    var workerUrl by mutableStateOf("")
        private set
    var appSecret by mutableStateOf("")
        private set
    var flirtLevel by mutableStateOf("flirty")
        private set
    var cloudVoice by mutableStateOf(true)
        private set

    /**
     * HTTP header mein sirf printable ASCII (0x20..0x7E) chal sakta hai.
     * Zero-width space, BOM, smart quotes, Urdu/Unicode characters sab hata diye jate hain.
     */
    fun cleanHeaderValue(raw: String): String =
        raw.filter { it.code in 0x20..0x7E }.trim()

    /** URL mein koi space ya chhupa hua character nahi hona chahiye. */
    fun cleanUrl(raw: String): String =
        raw.filter { it.code in 0x21..0x7E }

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext
            .getSharedPreferences("miral_settings", Context.MODE_PRIVATE)
        prefs = p

        val rawUrl = p.getString("workerUrl", "") ?: ""
        val rawSecret = p.getString("appSecret", "") ?: ""

        workerUrl = cleanUrl(rawUrl)
        appSecret = cleanHeaderValue(rawSecret)

        // Purani kharab saved value ko bhi saaf karke wapas save kar do
        if (workerUrl != rawUrl || appSecret != rawSecret) {
            p.edit()
                .putString("workerUrl", workerUrl)
                .putString("appSecret", appSecret)
                .apply()
        }

        flirtLevel = p.getString("flirtLevel", "flirty") ?: "flirty"
        cloudVoice = p.getBoolean("cloudVoice", true)
    }

    fun updateWorkerUrl(value: String) {
        workerUrl = cleanUrl(value)
        prefs?.edit()?.putString("workerUrl", workerUrl)?.apply()
    }

    fun updateAppSecret(value: String) {
        appSecret = cleanHeaderValue(value)
        prefs?.edit()?.putString("appSecret", appSecret)?.apply()
    }

    fun updateFlirtLevel(value: String) {
        flirtLevel = value
        prefs?.edit()?.putString("flirtLevel", value)?.apply()
    }

    fun updateCloudVoice(value: Boolean) {
        cloudVoice = value
        prefs?.edit()?.putBoolean("cloudVoice", value)?.apply()
    }
}

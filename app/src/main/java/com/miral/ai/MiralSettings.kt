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

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext
            .getSharedPreferences("miral_settings", Context.MODE_PRIVATE)
        prefs = p
        workerUrl = p.getString("workerUrl", "") ?: ""
        appSecret = p.getString("appSecret", "") ?: ""
        flirtLevel = p.getString("flirtLevel", "flirty") ?: "flirty"
        cloudVoice = p.getBoolean("cloudVoice", true)
    }

    fun updateWorkerUrl(value: String) {
        workerUrl = value.trim()
        prefs?.edit()?.putString("workerUrl", workerUrl)?.apply()
    }

    fun updateAppSecret(value: String) {
        appSecret = value.trim()
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

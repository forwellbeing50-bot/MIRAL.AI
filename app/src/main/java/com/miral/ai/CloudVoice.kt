package com.miral.ai

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

object CloudVoice {

    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    fun isEnabled(): Boolean = MiralSettings.cloudVoice && MiralBrain.isConfigured()

    /** Urdu text ki mp3 awaaz laata hai. onDone(file, error) main thread par aata hai. */
    fun fetch(context: Context, text: String, onDone: (File?, String?) -> Unit) {
        val base = MiralSettings.workerUrl.trim().trimEnd('/')
        val secret = MiralSettings.appSecret.trim()
        val cacheDir = context.applicationContext.cacheDir
        val payload = JSONObject().put("text", text).toString()

        executor.execute {
            var file: File? = null
            var error: String? = null
            try {
                val conn = URL("$base/tts").openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 15000
                conn.readTimeout = 30000
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.setRequestProperty("x-miral-key", secret)
                conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }

                val code = conn.responseCode
                if (code in 200..299) {
                    val out = File(cacheDir, "miral_tts_${System.nanoTime()}.mp3")
                    conn.inputStream.use { input ->
                        out.outputStream().use { output -> input.copyTo(output) }
                    }
                    file = out
                } else {
                    val body = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                    error = "code $code ${body.take(120)}"
                }
                conn.disconnect()
            } catch (e: Exception) {
                error = e.message ?: "network"
            }
            main.post { onDone(file, error) }
        }
    }
}

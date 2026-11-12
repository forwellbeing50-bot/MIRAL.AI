package com.miral.ai

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Base64
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors

object CloudVoice {

    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    fun isEnabled(): Boolean = MiralSettings.cloudVoice && MiralBrain.isConfigured()

    /** Urdu text ki awaaz (WAV file) laata hai. onDone(file, error) main thread par aata hai. */
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
                conn.readTimeout = 60000
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.setRequestProperty("x-miral-key", secret)
                conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }

                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""

                if (code in 200..299) {
                    val obj = JSONObject(body)
                    val audio = obj.optString("audio", "")
                    val rate = obj.optInt("rate", 24000)
                    if (audio.isBlank()) {
                        error = "audio khali aaya"
                    } else {
                        val pcm = Base64.decode(audio, Base64.DEFAULT)
                        val out = File(cacheDir, "miral_tts_${System.nanoTime()}.wav")
                        writeWav(out, pcm, rate)
                        file = out
                    }
                } else {
                    error = "code $code ${body.take(200)}"
                }
                conn.disconnect()
            } catch (e: Exception) {
                error = e.message ?: "network"
            }
            main.post { onDone(file, error) }
        }
    }

    private fun writeWav(file: File, pcm: ByteArray, sampleRate: Int) {
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray(Charsets.US_ASCII))
        header.putInt(36 + pcm.size)
        header.put("WAVE".toByteArray(Charsets.US_ASCII))
        header.put("fmt ".toByteArray(Charsets.US_ASCII))
        header.putInt(16)
        header.putShort(1.toShort())
        header.putShort(1.toShort())
        header.putInt(sampleRate)
        header.putInt(sampleRate * 2)
        header.putShort(2.toShort())
        header.putShort(16.toShort())
        header.put("data".toByteArray(Charsets.US_ASCII))
        header.putInt(pcm.size)
        file.outputStream().use {
            it.write(header.array())
            it.write(pcm)
        }
    }
}

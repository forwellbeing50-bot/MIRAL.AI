package com.miral.ai

import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

object MiralBrain {

    private val executor = Executors.newCachedThreadPool()
    private val main = Handler(Looper.getMainLooper())

    fun isConfigured(): Boolean =
        MiralSettings.workerUrl.isNotBlank() && MiralSettings.appSecret.isNotBlank()

    /** history: poori baat-cheet. onDone(jawab, error) main thread par aata hai. */
    fun ask(history: List<ChatMessage>, onDone: (Reply?, String?) -> Unit) {
        val base = MiralSettings.workerUrl.trim().trimEnd('/')
        val secret = MiralSettings.appSecret.trim()
        val payload = buildPayload(history, MiralSettings.flirtLevel)

        executor.execute {
            var reply: Reply? = null
            var error: String? = null
            try {
                val conn = URL("$base/chat").openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 15000
                conn.readTimeout = 40000
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.setRequestProperty("x-miral-key", secret)
                conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }

                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""

                if (code in 200..299) {
                    val obj = JSONObject(text)
                    val urdu = obj.optString("urdu", "")
                    val roman = obj.optString("roman", "")
                    if (urdu.isBlank() && roman.isBlank()) {
                        error = "Khali jawab aaya"
                    } else {
                        reply = Reply(roman.ifBlank { urdu }, urdu.ifBlank { roman })
                    }
                } else {
                    error = "Server error $code: ${text.take(200)}"
                }
                conn.disconnect()
            } catch (e: Exception) {
                error = "Connection masla: ${e.message}"
            }
            main.post { onDone(reply, error) }
        }
    }

    /** Settings screen ke test button ke liye. */
    fun test(onDone: (String) -> Unit) {
        if (!isConfigured()) {
            onDone("❌ Pehle Worker link aur Secret daalein")
            return
        }
        val list = listOf(ChatMessage("salam, apna chhota sa taaruf karao", false))
        ask(list) { reply, error ->
            if (reply != null) {
                onDone("✅ Chal raha hai: ${reply.roman}")
            } else {
                onDone("❌ $error")
            }
        }
    }

    private fun buildPayload(history: List<ChatMessage>, flirt: String): String {
        val trimmed = history.takeLast(12).dropWhile { it.fromMiral }
        val arr = JSONArray()
        for (m in trimmed) {
            val o = JSONObject()
            o.put("role", if (m.fromMiral) "assistant" else "user")
            o.put("content", m.text)
            arr.put(o)
        }
        val root = JSONObject()
        root.put("messages", arr)
        root.put("flirt", flirt)
        root.put(
            "now",
            SimpleDateFormat("EEEE, dd MMMM yyyy, hh:mm a", Locale.getDefault()).format(Date())
        )
        return root.toString()
    }
}

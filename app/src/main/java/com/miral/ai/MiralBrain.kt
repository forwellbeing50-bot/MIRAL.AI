package com.miral.ai

import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

object MiralBrain {

    private val executor = Executors.newCachedThreadPool()
    private val main = Handler(Looper.getMainLooper())

    fun isConfigured(): Boolean {
        val url = MiralSettings.cleanUrl(MiralSettings.workerUrl)
        val secret = MiralSettings.cleanHeaderValue(MiralSettings.appSecret)
        return url.startsWith("https://") && secret.isNotBlank()
    }

    /** Secret zahir kiye baghair sirf diagnostic info: host aur secret ki lambai. */
    fun diagnostics(): String {
        val host = try {
            URL(MiralSettings.cleanUrl(MiralSettings.workerUrl)).host
        } catch (e: Exception) {
            "URL galat hai"
        }
        val len = MiralSettings.cleanHeaderValue(MiralSettings.appSecret).length
        return "Worker: $host | Secret ki lambai: $len"
    }

    fun ask(
        history: List<ChatMessage>,
        onDone: (Reply?, String?) -> Unit
    ) {
        val base = MiralSettings.cleanUrl(MiralSettings.workerUrl).trimEnd('/')
        val secret = MiralSettings.cleanHeaderValue(MiralSettings.appSecret)
        val payload = buildPayload(history, MiralSettings.flirtLevel)

        executor.execute {

            var reply: Reply? = null
            var error: String? = null
            var conn: HttpURLConnection? = null

            try {
                require(secret.isNotBlank()) { "App Secret missing" }

                conn = URL("$base/chat").openConnection() as HttpURLConnection

                conn.requestMethod = "POST"
                conn.connectTimeout = 20000
                conn.readTimeout = 60000
                conn.doOutput = true

                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.setRequestProperty("Accept", "application/json")
                conn.setRequestProperty("x-miral-key", secret)

                conn.outputStream.use {
                    it.write(payload.toByteArray(Charsets.UTF_8))
                }

                val code = conn.responseCode

                val stream = if (code in 200..299) conn.inputStream else conn.errorStream

                val response = stream?.bufferedReader(Charsets.UTF_8)
                    ?.use { it.readText() } ?: ""

                if (code in 200..299) {

                    val obj = JSONObject(response)
                    val urdu = obj.optString("urdu", "")
                    val roman = obj.optString("roman", "")

                    if (urdu.isBlank() && roman.isBlank()) {
                        error = "Chat: khali jawab: ${response.take(300)}"
                    } else {
                        reply = Reply(
                            roman.ifBlank { urdu },
                            urdu.ifBlank { roman }
                        )
                    }

                } else {
                    error = when (code) {
                        401, 403 ->
                            "Chat: secret match nahi hua (code $code). App ka secret aur Cloudflare ke APP_SECRET bilkul same hone chahiye"
                        500 ->
                            "Chat: Worker ke andar masla (code 500): ${response.take(300)}"
                        else ->
                            "Chat: server error $code: ${response.take(300)}"
                    }
                }

            } catch (e: SocketTimeoutException) {
                error = "Chat: server timeout"

            } catch (e: UnknownHostException) {
                error = "Chat: internet/DNS masla, Worker host nahi mila"

            } catch (e: IllegalArgumentException) {
                error = "Chat: Header masla: ${e.message ?: "Unknown"}"

            } catch (e: Exception) {
                error = "Chat: connection masla: ${e.message ?: "Unknown error"}"

            } finally {
                conn?.disconnect()
            }

            main.post {
                onDone(reply, error)
            }
        }
    }

    fun test(onDone: (String) -> Unit) {

        if (!isConfigured()) {
            onDone("❌ Worker URL ya App Secret check karein\n${diagnostics()}")
            return
        }

        val list = listOf(
            ChatMessage("Salam, apna chhota sa taaruf karao", false)
        )

        ask(list) { reply, error ->
            if (reply != null) {
                onDone("✅ MIRAL connected: ${reply.roman}")
            } else {
                onDone("❌ $error\n${diagnostics()}")
            }
        }
    }

    private fun buildPayload(
        history: List<ChatMessage>,
        flirt: String
    ): String {

        val trimmed = history.takeLast(12)
            .dropWhile { it.fromMiral }

        val arr = JSONArray()

        for (message in trimmed) {
            val obj = JSONObject()
            obj.put("role", if (message.fromMiral) "assistant" else "user")
            obj.put("content", message.text)
            arr.put(obj)
        }

        val root = JSONObject()
        root.put("messages", arr)
        root.put("flirt", flirt)
        root.put(
            "now",
            SimpleDateFormat("EEEE, dd MMMM yyyy, hh:mm a", Locale.getDefault())
                .format(Date())
        )

        return root.toString()
    }
}

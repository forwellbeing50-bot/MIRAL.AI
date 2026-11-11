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
    val url = MiralSettings.workerUrl.trim()
    return url.startsWith("https://") &&
        MiralSettings.appSecret.isNotBlank()
}

fun ask(
    history: List<ChatMessage>,
    onDone: (Reply?, String?) -> Unit
) {
    val base = MiralSettings.workerUrl.trim().trimEnd('/')
    val secret = MiralSettings.appSecret
    .trim()
    .replace("\r", "")
    .replace("\n", "")
    val payload = buildPayload(history, MiralSettings.flirtLevel)

    executor.execute {

        var reply: Reply? = null
        var error: String? = null
        var conn: HttpURLConnection? = null

        try {
            val url = URL("$base/chat")

            conn = url.openConnection() as HttpURLConnection

            conn.requestMethod = "POST"
            conn.connectTimeout = 20000
            conn.readTimeout = 60000
            conn.doOutput = true

            conn.setRequestProperty(
                "Content-Type",
                "application/json; charset=utf-8"
            )

            conn.setRequestProperty(
                "Accept",
                "application/json"
            )

            conn.setRequestProperty(
                "x-miral-key",
                secret
            )

            conn.outputStream.use {
                it.write(payload.toByteArray(Charsets.UTF_8))
            }

            val code = conn.responseCode

            val stream = if (code in 200..299) {
                conn.inputStream
            } else {
                conn.errorStream
            }

            val response = stream?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() } ?: ""

            if (code in 200..299) {

                val obj = JSONObject(response)

                val urdu = obj.optString("urdu", "")
                val roman = obj.optString("roman", "")

                if (urdu.isBlank() && roman.isBlank()) {
                    error = "Server ne khali jawab bheja: ${response.take(300)}"
                } else {
                    reply = Reply(
                        roman.ifBlank { urdu },
                        urdu.ifBlank { roman }
                    )
                }

            } else {
                error = "Server error $code: ${response.take(500)}"
            }

        } catch (e: SocketTimeoutException) {

            error = "Server timeout: Worker ne waqt par jawab nahi diya"

        } catch (e: UnknownHostException) {

            error = "Internet/DNS masla: Worker host nahi mila"

        } catch (e: Exception) {

            error = "Connection masla: ${e.message ?: "Unknown error"}"

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
        onDone("❌ Worker URL ya App Secret check karein")
        return
    }

    val list = listOf(
        ChatMessage(
            "Salam, apna chhota sa taaruf karao",
            false
        )
    )

    ask(list) { reply, error ->

        if (reply != null) {
            onDone("✅ MIRAL connected: ${reply.roman}")
        } else {
            onDone("❌ $error")
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

        obj.put(
            "role",
            if (message.fromMiral) "assistant" else "user"
        )

        obj.put("content", message.text)

        arr.put(obj)
    }

    val root = JSONObject()

    root.put("messages", arr)
    root.put("flirt", flirt)

    root.put(
        "now",
        SimpleDateFormat(
            "EEEE, dd MMMM yyyy, hh:mm a",
            Locale.getDefault()
        ).format(Date())
    )

    return root.toString()
}

}

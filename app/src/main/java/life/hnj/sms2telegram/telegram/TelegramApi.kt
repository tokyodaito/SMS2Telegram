package life.hnj.sms2telegram.telegram

import org.json.JSONObject
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object TelegramApi {
    fun sendMessage(botKey: String, chatId: String, text: String): Boolean {
        val endpoint = URL("https://api.telegram.org/bot$botKey/sendMessage")
        val conn = (endpoint.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15000
            readTimeout = 15000
            doInput = true
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
        return try {
            val payload = JSONObject()
                .put("chat_id", chatId)
                .put("text", text)
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(payload.toString())
            }
            val code = conn.responseCode
            code in 200..299
        } finally {
            conn.disconnect()
        }
    }

    fun getUpdates(botKey: String, offset: Long): JSONObject? {
        val endpoint = URL("https://api.telegram.org/bot$botKey/getUpdates?offset=$offset&timeout=0")
        val conn = (endpoint.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 15000
            doInput = true
        }
        return try {
            if (conn.responseCode !in 200..299) {
                return null
            }
            BufferedReader(conn.inputStream.reader()).use { reader ->
                val body = reader.readText()
                JSONObject(body)
            }
        } finally {
            conn.disconnect()
        }
    }
}

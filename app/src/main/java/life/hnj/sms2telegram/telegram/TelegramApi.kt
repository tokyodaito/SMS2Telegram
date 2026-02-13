package life.hnj.sms2telegram.telegram

import org.json.JSONObject
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object TelegramApi {
    data class BotInfo(
        val id: Long,
        val username: String?,
        val firstName: String?,
    )

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

    fun getMe(botKey: String): BotInfo? {
        val endpoint = URL("https://api.telegram.org/bot$botKey/getMe")
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
            val json = readJson(conn) ?: return null
            if (!json.optBoolean("ok", false)) {
                return null
            }
            val result = json.optJSONObject("result") ?: return null
            val id = result.optLong("id", -1L)
            if (id <= 0L) {
                return null
            }
            BotInfo(
                id = id,
                username = result.optString("username").takeIf { it.isNotBlank() },
                firstName = result.optString("first_name").takeIf { it.isNotBlank() },
            )
        } finally {
            conn.disconnect()
        }
    }

    fun getUpdates(botKey: String, offset: Long): JSONObject? {
        return getUpdates(botKey, offset, timeoutSeconds = 0)
    }

    fun getUpdates(botKey: String, offset: Long, timeoutSeconds: Int): JSONObject? {
        val endpoint =
            URL("https://api.telegram.org/bot$botKey/getUpdates?offset=$offset&timeout=$timeoutSeconds")
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
            readJson(conn)
        } finally {
            conn.disconnect()
        }
    }

    fun getUserProfilePhotos(botKey: String, userId: Long): JSONObject? {
        val endpoint =
            URL("https://api.telegram.org/bot$botKey/getUserProfilePhotos?user_id=$userId&limit=1")
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
            readJson(conn)
        } finally {
            conn.disconnect()
        }
    }

    fun getFile(botKey: String, fileId: String): JSONObject? {
        val endpoint = URL("https://api.telegram.org/bot$botKey/getFile?file_id=$fileId")
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
            readJson(conn)
        } finally {
            conn.disconnect()
        }
    }

    fun downloadFile(botKey: String, filePath: String): ByteArray? {
        val endpoint = URL("https://api.telegram.org/file/bot$botKey/$filePath")
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
            conn.inputStream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
    }

    private fun readJson(conn: HttpURLConnection): JSONObject? {
        val stream = runCatching { conn.inputStream }.getOrElse { conn.errorStream ?: return null }
        val body = BufferedReader(stream.reader()).use { it.readText() }
        return runCatching { JSONObject(body) }.getOrNull()
    }
}

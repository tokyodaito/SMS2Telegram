package life.hnj.sms2telegram.users

import org.json.JSONArray
import org.json.JSONObject

object UserJson {
    fun usersFromJson(raw: String?): List<LinkedUser> {
        if (raw.isNullOrBlank()) {
            return emptyList()
        }
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        val out = ArrayList<LinkedUser>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val chatId = obj.optString("chatId", "")
            val userId = obj.optLong("userId", -1)
            val displayName = obj.optString("displayName", "")
            if (chatId.isBlank() || userId <= 0 || displayName.isBlank()) {
                continue
            }
            out.add(
                LinkedUser(
                    chatId = chatId,
                    userId = userId,
                    displayName = displayName,
                    username = obj.optString("username").takeIf { it.isNotBlank() },
                    avatarLocalPath = obj.optString("avatarLocalPath").takeIf { it.isNotBlank() },
                    linkedAt = obj.optLong("linkedAt", System.currentTimeMillis()),
                )
            )
        }
        return out
    }

    fun usersToJson(users: List<LinkedUser>): String {
        val arr = JSONArray()
        users.forEach { u ->
            val obj = JSONObject()
            obj.put("chatId", u.chatId)
            obj.put("userId", u.userId)
            obj.put("displayName", u.displayName)
            if (!u.username.isNullOrBlank()) obj.put("username", u.username)
            if (!u.avatarLocalPath.isNullOrBlank()) obj.put("avatarLocalPath", u.avatarLocalPath)
            obj.put("linkedAt", u.linkedAt)
            arr.put(obj)
        }
        return arr.toString()
    }

    fun botStatusFromJson(raw: String?): BotStatus? {
        if (raw.isNullOrBlank()) {
            return null
        }
        val obj = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        val isValid = obj.optBoolean("isValid", false)
        val botId = obj.optLong("botId", -1).takeIf { it > 0 }
        val botUsername = obj.optString("botUsername").takeIf { it.isNotBlank() }
        val checkedAt = obj.optLong("checkedAt", System.currentTimeMillis())
        val lastError = obj.optString("lastError").takeIf { it.isNotBlank() }
        return BotStatus(
            isValid = isValid,
            botId = botId,
            botUsername = botUsername,
            checkedAt = checkedAt,
            lastError = lastError,
        )
    }

    fun botStatusToJson(status: BotStatus): String {
        val obj = JSONObject()
        obj.put("isValid", status.isValid)
        if (status.botId != null) obj.put("botId", status.botId)
        if (!status.botUsername.isNullOrBlank()) obj.put("botUsername", status.botUsername)
        obj.put("checkedAt", status.checkedAt)
        if (!status.lastError.isNullOrBlank()) obj.put("lastError", status.lastError)
        return obj.toString()
    }
}


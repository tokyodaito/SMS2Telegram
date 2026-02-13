package life.hnj.sms2telegram.telegram

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import life.hnj.sms2telegram.events.EventType
import life.hnj.sms2telegram.settings.SettingsRepository
import org.json.JSONObject

class TelegramControlWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : Worker(appContext, workerParams) {

    override fun doWork(): Result {
        val settings = SettingsRepository(applicationContext)
        runCatching { kotlinx.coroutines.runBlocking { settings.migrateLegacyIfNeeded() } }

        if (!settings.isSyncEnabledBlocking()) {
            return Result.success()
        }

        val botKey = settings.getTelegramBotKeyBlocking()
        if (botKey.isBlank()) {
            return Result.success()
        }

        val offset = settings.getTelegramOffsetBlocking()
        val updates = try {
            TelegramApi.getUpdates(botKey, offset)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch updates", e)
            return Result.retry()
        } ?: return Result.retry()

        if (!updates.optBoolean("ok", false)) {
            return Result.retry()
        }

        val results = updates.optJSONArray("result") ?: return Result.success()
        var maxOffset = offset
        for (i in 0 until results.length()) {
            val item = results.optJSONObject(i) ?: continue
            val updateId = item.optLong("update_id", -1L)
            if (updateId >= 0L) {
                maxOffset = maxOf(maxOffset, updateId + 1)
            }

            val message = item.optJSONObject("message") ?: continue
            val text = message.optString("text", "")
            if (!text.startsWith("/")) {
                continue
            }
            val chatId = extractChatId(message) ?: continue
            if (!settings.isAdminChatAllowedBlocking(chatId)) {
                continue
            }

            val response = handleCommand(text, settings)
            try {
                TelegramApi.sendMessage(botKey, chatId, response)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send control response", e)
            }
        }

        settings.setTelegramOffsetBlocking(maxOffset)
        return Result.success()
    }

    private fun extractChatId(message: JSONObject): String? {
        val chat = message.optJSONObject("chat") ?: return null
        return chat.optLong("id").takeIf { it != 0L }?.toString()
    }

    private fun handleCommand(text: String, settings: SettingsRepository): String {
        val parts = text.trim().split(Regex("\\s+"))
        val command = parts.firstOrNull()?.substringBefore("@")?.lowercase() ?: return helpMessage()
        val arg = parts.getOrNull(1)?.lowercase()

        return when (command) {
            "/help" -> helpMessage()
            "/list_events" -> listEventsMessage()
            "/status" -> statusMessage(settings)
            "/enable" -> toggleEvents(settings, arg, enabled = true)
            "/disable" -> toggleEvents(settings, arg, enabled = false)
            else -> "Unknown command. Use /help"
        }
    }

    private fun toggleEvents(settings: SettingsRepository, arg: String?, enabled: Boolean): String {
        if (arg.isNullOrBlank()) {
            return "Usage: ${if (enabled) "/enable" else "/disable"} <event|all>"
        }

        if (arg == "all") {
            settings.setAllEventsEnabledBlocking(enabled = enabled)
            return if (enabled) "All events enabled" else "All events disabled"
        }

        val type = EventType.fromCliName(arg) ?: return "Unknown event '$arg'. Use /list_events"
        settings.setEventEnabledBlocking(type, enabled)
        return "${type.cliName} ${if (enabled) "enabled" else "disabled"}"
    }

    private fun statusMessage(settings: SettingsRepository): String {
        val sync = settings.isSyncEnabledBlocking()
        val eventStatuses = settings.getEventStatusBlocking()
            .entries
            .sortedBy { it.key.cliName }
            .joinToString("\n") { (type, enabled) ->
                "${type.cliName}: ${if (enabled) "on" else "off"}"
            }
        return "sync: ${if (sync) "on" else "off"}\n$eventStatuses"
    }

    private fun listEventsMessage(): String {
        val names = EventType.entries.joinToString("\n") { it.cliName }
        return "Supported events:\n$names"
    }

    private fun helpMessage(): String {
        return """
            Commands:
            /status
            /list_events
            /enable <event|all>
            /disable <event|all>
            /help
        """.trimIndent()
    }

    companion object {
        private const val TAG = "TelegramControlWorker"
    }
}

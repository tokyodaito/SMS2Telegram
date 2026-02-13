package life.hnj.sms2telegram.events

import android.content.Context
import life.hnj.sms2telegram.settings.SettingsRepository
import life.hnj.sms2telegram.telegram.TelegramTransport
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class EventForwarder {
    fun forward(context: Context, event: PhoneEvent) {
        val repository = SettingsRepository(context)
        if (!repository.canForwardEventBlocking(event.type)) {
            return
        }
        if (isDebounced(event.type)) {
            return
        }
        TelegramTransport.enqueueSend(context, formatMessage(event))
    }

    private fun formatMessage(event: PhoneEvent): String {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        return buildString {
            append("[SMS2Telegram] [")
            append(event.type.name)
            append("]\n")
            append(event.title)
            append("\n")
            append(event.body)
            append("\n")
            append("time: ")
            append(timestamp)
        }
    }

    private fun isDebounced(type: EventType): Boolean {
        if (type !in debouncedTypes) {
            return false
        }
        val now = System.currentTimeMillis()
        val prev = lastEventTs[type]
        if (prev != null && now - prev < DEBOUNCE_MS) {
            return true
        }
        lastEventTs[type] = now
        return false
    }

    companion object {
        private const val DEBOUNCE_MS = 7_000L
        private val debouncedTypes = setOf(EventType.AIRPLANE_MODE, EventType.SIM_STATE)
        private val lastEventTs = ConcurrentHashMap<EventType, Long>()
    }
}

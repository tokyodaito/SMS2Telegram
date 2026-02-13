package life.hnj.sms2telegram.smshandler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsMessage
import android.util.Log
import kotlin.math.max
import life.hnj.sms2telegram.events.EventForwarder
import life.hnj.sms2telegram.events.EventType
import life.hnj.sms2telegram.events.PhoneEvent
import life.hnj.sms2telegram.settings.SettingsRepository

private const val TAG = "SMSHandler"

class SMSReceiver : BroadcastReceiver() {
    private val eventForwarder = EventForwarder()

    override fun onReceive(context: Context, intent: Intent) {
        val bundle = intent.extras ?: return
        val format = bundle.getString("format")
        val pdus = bundle["pdus"] as Array<*>? ?: return
        val simIndex =
            max(bundle.getInt("phone", -1), bundle.getInt("android.telephony.extra.SLOT_INDEX", -1))

        val settingsRepository = SettingsRepository(context)
        val phoneNum = settingsRepository.getSimNumberBlocking(simIndex)

        val messages = pdus.mapNotNull { part ->
            runCatching {
                SmsMessage.createFromPdu(part as ByteArray, format)
            }.getOrNull()
        }
        if (messages.isEmpty()) {
            return
        }

        val mergedMessages = HashMap<String, String>()
        for (msg in messages) {
            val fromAddr = msg.originatingAddress ?: "unknown"
            val body = msg.messageBody ?: ""
            mergedMessages[fromAddr] = mergedMessages.getOrDefault(fromAddr, "") + body
        }

        for ((from, body) in mergedMessages) {
            val event = PhoneEvent(
                type = EventType.SMS,
                title = "New SMS from $from",
                body = "to $phoneNum\n\n$body",
                metadata = mapOf("sim_slot" to simIndex.toString())
            )
            Log.d(TAG, "Forwarding SMS event from $from")
            eventForwarder.forward(context, event)
        }
    }
}

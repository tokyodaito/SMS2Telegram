package life.hnj.sms2telegram.smshandler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import life.hnj.sms2telegram.events.EventForwarder
import life.hnj.sms2telegram.events.EventType
import life.hnj.sms2telegram.events.PhoneEvent

class SystemEventReceiver(
    private val eventForwarder: EventForwarder = EventForwarder(),
) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BATTERY_LOW -> {
                forward(context, EventType.BATTERY_LOW, "Battery low", "Device battery is low")
            }

            Intent.ACTION_POWER_CONNECTED -> {
                forward(
                    context,
                    EventType.POWER_CONNECTED,
                    "Power connected",
                    "Device is charging"
                )
            }

            Intent.ACTION_POWER_DISCONNECTED -> {
                forward(
                    context,
                    EventType.POWER_DISCONNECTED,
                    "Power disconnected",
                    "Device stopped charging"
                )
            }

            Intent.ACTION_AIRPLANE_MODE_CHANGED -> {
                val enabled = intent.getBooleanExtra("state", false)
                val mode = if (enabled) "enabled" else "disabled"
                forward(
                    context,
                    EventType.AIRPLANE_MODE,
                    "Airplane mode changed",
                    "Airplane mode is $mode"
                )
            }

            Intent.ACTION_SHUTDOWN -> {
                forward(
                    context,
                    EventType.SHUTDOWN,
                    "Device shutting down",
                    "Android sent ACTION_SHUTDOWN"
                )
            }
        }
    }

    private fun forward(context: Context, type: EventType, title: String, body: String) {
        eventForwarder.forward(
            context,
            PhoneEvent(
                type = type,
                title = title,
                body = body
            )
        )
    }
}

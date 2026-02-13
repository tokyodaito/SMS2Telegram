package life.hnj.sms2telegram.smshandler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log
import life.hnj.sms2telegram.events.EventForwarder
import life.hnj.sms2telegram.events.EventType
import life.hnj.sms2telegram.events.PhoneEvent

class TelephonyEventReceiver(
    private val eventForwarder: EventForwarder = EventForwarder(),
) : BroadcastReceiver() {
    private var lastCallState = TelephonyManager.CALL_STATE_IDLE
    private var ringingNumber: String? = null
    private var answered = false

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            TelephonyManager.ACTION_PHONE_STATE_CHANGED -> onPhoneStateChanged(context, intent)
            TelephonyManager.ACTION_SIM_CARD_STATE_CHANGED,
            ACTION_SIM_STATE_CHANGED_COMPAT -> onSimStateChanged(context, intent)
        }
    }

    private fun onPhoneStateChanged(context: Context, intent: Intent) {
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER) ?: "unknown"
        val callState = when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> TelephonyManager.CALL_STATE_RINGING
            TelephonyManager.EXTRA_STATE_OFFHOOK -> TelephonyManager.CALL_STATE_OFFHOOK
            TelephonyManager.EXTRA_STATE_IDLE -> TelephonyManager.CALL_STATE_IDLE
            else -> return
        }

        when (callState) {
            TelephonyManager.CALL_STATE_RINGING -> {
                ringingNumber = number
                answered = false
            }

            TelephonyManager.CALL_STATE_OFFHOOK -> {
                if (lastCallState == TelephonyManager.CALL_STATE_RINGING) {
                    answered = true
                }
            }

            TelephonyManager.CALL_STATE_IDLE -> {
                if (lastCallState == TelephonyManager.CALL_STATE_RINGING && !answered) {
                    val caller = ringingNumber ?: "unknown"
                    eventForwarder.forward(
                        context,
                        PhoneEvent(
                            type = EventType.MISSED_CALL,
                            title = "Missed call",
                            body = "from $caller"
                        )
                    )
                    Log.d(TAG, "Forwarded missed call event")
                }
                ringingNumber = null
                answered = false
            }
        }
        lastCallState = callState
    }

    private fun onSimStateChanged(context: Context, intent: Intent) {
        val stateText = intent.getStringExtra("ss")
            ?: intent.extras?.toString()
            ?: "unknown"
        eventForwarder.forward(
            context,
            PhoneEvent(
                type = EventType.SIM_STATE,
                title = "SIM state changed",
                body = stateText
            )
        )
    }

    companion object {
        private const val TAG = "TelephonyEventReceiver"
        private const val ACTION_SIM_STATE_CHANGED_COMPAT = "android.intent.action.SIM_STATE_CHANGED"
    }
}

package life.hnj.sms2telegram.smshandler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log
import life.hnj.sms2telegram.events.EventForwarder
import life.hnj.sms2telegram.events.EventType
import life.hnj.sms2telegram.events.PhoneEvent

class TelephonyEventReceiver : BroadcastReceiver() {
    private val eventForwarder = EventForwarder()

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            TelephonyManager.ACTION_PHONE_STATE_CHANGED -> onPhoneStateChanged(context, intent)
            TelephonyManager.ACTION_SIM_CARD_STATE_CHANGED,
            ACTION_SIM_STATE_CHANGED_COMPAT -> onSimStateChanged(context, intent)
        }
    }

    private fun onPhoneStateChanged(context: Context, intent: Intent) {
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER) ?: "unknown"
        val callState = when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> TelephonyManager.CALL_STATE_RINGING
            TelephonyManager.EXTRA_STATE_OFFHOOK -> TelephonyManager.CALL_STATE_OFFHOOK
            TelephonyManager.EXTRA_STATE_IDLE -> TelephonyManager.CALL_STATE_IDLE
            else -> return
        }

        val runtime = loadRuntime(context)
        var lastState = runtime.lastCallState
        var ringingNumber = runtime.ringingNumber
        var answered = runtime.answered

        when (callState) {
            TelephonyManager.CALL_STATE_RINGING -> {
                ringingNumber = incomingNumber
                answered = false
            }

            TelephonyManager.CALL_STATE_OFFHOOK -> {
                if (lastState == TelephonyManager.CALL_STATE_RINGING) {
                    answered = true
                }
            }

            TelephonyManager.CALL_STATE_IDLE -> {
                if (lastState == TelephonyManager.CALL_STATE_RINGING && !answered) {
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

        lastState = callState
        storeRuntime(context, TelephonyRuntime(lastState, ringingNumber, answered))
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

    private fun loadRuntime(context: Context): TelephonyRuntime {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return TelephonyRuntime(
            lastCallState = prefs.getInt(KEY_LAST_CALL_STATE, TelephonyManager.CALL_STATE_IDLE),
            ringingNumber = prefs.getString(KEY_RINGING_NUMBER, null),
            answered = prefs.getBoolean(KEY_ANSWERED, false)
        )
    }

    private fun storeRuntime(context: Context, runtime: TelephonyRuntime) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putInt(KEY_LAST_CALL_STATE, runtime.lastCallState)
            .putString(KEY_RINGING_NUMBER, runtime.ringingNumber)
            .putBoolean(KEY_ANSWERED, runtime.answered)
            .apply()
    }

    private data class TelephonyRuntime(
        val lastCallState: Int,
        val ringingNumber: String?,
        val answered: Boolean,
    )

    companion object {
        private const val TAG = "TelephonyEventReceiver"
        private const val ACTION_SIM_STATE_CHANGED_COMPAT = "android.intent.action.SIM_STATE_CHANGED"
        private const val PREFS_NAME = "telephony_runtime_state"
        private const val KEY_LAST_CALL_STATE = "last_call_state"
        private const val KEY_RINGING_NUMBER = "ringing_number"
        private const val KEY_ANSWERED = "answered"
    }
}

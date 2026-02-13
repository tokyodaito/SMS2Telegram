package life.hnj.sms2telegram.smshandler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.runBlocking
import life.hnj.sms2telegram.events.EventForwarder
import life.hnj.sms2telegram.events.EventType
import life.hnj.sms2telegram.events.PhoneEvent
import life.hnj.sms2telegram.runtime.SyncRuntimeManager
import life.hnj.sms2telegram.settings.SettingsRepository

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED) {
            return
        }

        val settings = SettingsRepository(context)
        runBlocking {
            settings.migrateLegacyIfNeeded()
        }
        if (!settings.isSyncEnabledBlocking()) {
            return
        }

        SyncRuntimeManager.start(context)
        EventForwarder().forward(
            context,
            PhoneEvent(
                type = EventType.BOOT_COMPLETED,
                title = "Device boot completed",
                body = "System broadcast received"
            )
        )
    }
}

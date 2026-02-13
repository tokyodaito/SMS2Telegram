package life.hnj.sms2telegram.runtime

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.runBlocking
import life.hnj.sms2telegram.events.EventType
import life.hnj.sms2telegram.settings.SettingsRepository
import life.hnj.sms2telegram.smshandler.BootCompletedReceiver
import life.hnj.sms2telegram.smshandler.SMSReceiver
import life.hnj.sms2telegram.smshandler.SystemEventReceiver
import life.hnj.sms2telegram.smshandler.TelephonyEventReceiver
import life.hnj.sms2telegram.telegram.TelegramControlScheduler

object SyncRuntimeManager {
    fun start(context: Context) {
        val appContext = context.applicationContext
        runCatching {
            runBlocking { SettingsRepository(appContext).migrateLegacyIfNeeded() }
        }
        reconfigure(appContext)
    }

    fun stop(context: Context) {
        val appContext = context.applicationContext
        setReceiversEnabled(appContext, enabled = false)
        TelegramControlScheduler.cancel(appContext)
    }

    fun reconfigure(context: Context) {
        val appContext = context.applicationContext
        val settings = SettingsRepository(appContext)
        runCatching {
            runBlocking { settings.migrateLegacyIfNeeded() }
        }
        if (settings.isSyncEnabledBlocking()) {
            setReceiverEnabled(appContext, BootCompletedReceiver::class.java, enabled = true)
            setReceiverEnabled(
                appContext,
                SMSReceiver::class.java,
                enabled = settings.isEventEnabledBlocking(EventType.SMS)
            )
            setReceiverEnabled(
                appContext,
                TelephonyEventReceiver::class.java,
                enabled = settings.isEventEnabledBlocking(EventType.MISSED_CALL) ||
                    settings.isEventEnabledBlocking(EventType.SIM_STATE)
            )
            setReceiverEnabled(
                appContext,
                SystemEventReceiver::class.java,
                enabled = settings.isEventEnabledBlocking(EventType.BATTERY_LOW) ||
                    settings.isEventEnabledBlocking(EventType.POWER_CONNECTED) ||
                    settings.isEventEnabledBlocking(EventType.POWER_DISCONNECTED) ||
                    settings.isEventEnabledBlocking(EventType.AIRPLANE_MODE) ||
                    settings.isEventEnabledBlocking(EventType.SHUTDOWN)
            )
            reconfigureControlWorker(appContext)
        } else {
            setReceiversEnabled(appContext, enabled = false)
            TelegramControlScheduler.cancel(appContext)
        }
    }

    private fun reconfigureControlWorker(context: Context) {
        val settings = SettingsRepository(context)
        if (settings.isRemoteControlEnabledBlocking()) {
            TelegramControlScheduler.ensureScheduled(context)
        } else {
            TelegramControlScheduler.cancel(context)
        }
    }

    private fun setReceiversEnabled(context: Context, enabled: Boolean) {
        RECEIVER_CLASSES.forEach { receiverClass ->
            setReceiverEnabled(context, receiverClass, enabled)
        }
    }

    private fun setReceiverEnabled(context: Context, receiverClass: Class<*>, enabled: Boolean) {
        val pm = context.packageManager
        val state = if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        pm.setComponentEnabledSetting(
            ComponentName(context, receiverClass),
            state,
            PackageManager.DONT_KILL_APP
        )
    }

    private val RECEIVER_CLASSES = listOf(
        SMSReceiver::class.java,
        TelephonyEventReceiver::class.java,
        SystemEventReceiver::class.java,
        BootCompletedReceiver::class.java,
    )
}

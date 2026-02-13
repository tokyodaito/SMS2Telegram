package life.hnj.sms2telegram.runtime

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import life.hnj.sms2telegram.smshandler.SMSHandleForegroundService
import life.hnj.sms2telegram.telegram.TelegramControlScheduler

object SyncRuntimeManager {
    fun start(context: Context) {
        val appContext = context.applicationContext
        val serviceIntent = Intent(appContext, SMSHandleForegroundService::class.java)
        ContextCompat.startForegroundService(appContext, serviceIntent)
        TelegramControlScheduler.ensureScheduled(appContext)
    }

    fun stop(context: Context) {
        val appContext = context.applicationContext
        appContext.stopService(Intent(appContext, SMSHandleForegroundService::class.java))
        TelegramControlScheduler.cancel(appContext)
    }
}

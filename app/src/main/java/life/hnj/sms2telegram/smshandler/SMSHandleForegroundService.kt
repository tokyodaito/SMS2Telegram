package life.hnj.sms2telegram.smshandler

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.os.IBinder
import android.provider.Telephony
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.runBlocking
import life.hnj.sms2telegram.MainActivity
import life.hnj.sms2telegram.R
import life.hnj.sms2telegram.settings.SettingsRepository
import life.hnj.sms2telegram.telegram.TelegramControlScheduler

class SMSHandleForegroundService : Service() {
    private val smsReceiver = SMSReceiver()
    private val telephonyEventReceiver = TelephonyEventReceiver()
    private val systemEventReceiver = SystemEventReceiver()
    private var receiversRegistered = false

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        runCatching {
            runBlocking { SettingsRepository(applicationContext).migrateLegacyIfNeeded() }
        }

        Log.d(TAG, "Starting foreground service and registering receivers")

        val notification = createNotification()
        startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        TelegramControlScheduler.ensureScheduled(applicationContext)

        if (!receiversRegistered) {
            registerSmsReceiver()
            registerTelephonyReceiver()
            registerSystemReceiver()
            receiversRegistered = true
        }

        return START_STICKY
    }

    private fun registerSmsReceiver() {
        ContextCompat.registerReceiver(
            this,
            smsReceiver,
            IntentFilter(Telephony.Sms.Intents.SMS_RECEIVED_ACTION),
            Manifest.permission.BROADCAST_SMS,
            null,
            ContextCompat.RECEIVER_EXPORTED
        )
    }

    private fun registerTelephonyReceiver() {
        val filter = IntentFilter().apply {
            addAction(android.telephony.TelephonyManager.ACTION_PHONE_STATE_CHANGED)
            addAction(android.telephony.TelephonyManager.ACTION_SIM_CARD_STATE_CHANGED)
            addAction("android.intent.action.SIM_STATE_CHANGED")
        }
        ContextCompat.registerReceiver(
            this,
            telephonyEventReceiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED
        )
    }

    private fun registerSystemReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_LOW)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
            addAction(Intent.ACTION_SHUTDOWN)
        }
        ContextCompat.registerReceiver(
            this,
            systemEventReceiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED
        )
    }

    private fun createNotification(): Notification {
        val input = "SMS2Telegram running in the background"
        val notificationIntent = Intent(applicationContext, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE,
        )
        val channelId = createNotificationChannel("SMS2TELEGRAM", "SMS2TelegramService")
        return NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("SMS2Telegram Service")
            .setContentText(input)
            .setContentIntent(pendingIntent)
            .setSmallIcon(R.mipmap.ic_launcher)
            .build()
    }

    override fun onDestroy() {
        if (receiversRegistered) {
            unregisterReceiver(smsReceiver)
            unregisterReceiver(telephonyEventReceiver)
            unregisterReceiver(systemEventReceiver)
            receiversRegistered = false
        }
        super.onDestroy()
    }

    private fun createNotificationChannel(channelId: String, channelName: String): String {
        val channel = NotificationChannel(
            channelId,
            channelName, NotificationManager.IMPORTANCE_DEFAULT
        )
        channel.lightColor = Color.BLUE
        channel.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        val service = this.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        service.createNotificationChannel(channel)
        return channelId
    }

    private companion object {
        const val TAG = "SMSReceiverService"
    }
}

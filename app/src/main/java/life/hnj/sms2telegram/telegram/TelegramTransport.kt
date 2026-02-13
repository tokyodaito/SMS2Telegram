package life.hnj.sms2telegram.telegram

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import life.hnj.sms2telegram.settings.SettingsRepository
import life.hnj.sms2telegram.smshandler.TelegramMessageWorker

object TelegramTransport {
    private const val TAG = "TelegramTransport"

    fun enqueueSend(context: Context, message: String) {
        val repository = SettingsRepository(context)
        val target = repository.getTelegramTargetBlocking()
        if (target == null) {
            Log.w(TAG, "Telegram credentials are not configured, message dropped")
            return
        }

        val input = Data.Builder()
            .putString(TelegramMessageWorker.KEY_BOT_KEY, target.botKey)
            .putString(TelegramMessageWorker.KEY_CHAT_ID, target.chatId)
            .putString(TelegramMessageWorker.KEY_MESSAGE, message)
            .build()
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val work = OneTimeWorkRequestBuilder<TelegramMessageWorker>()
            .setInputData(input)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueue(work)
    }
}

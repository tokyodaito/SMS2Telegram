package life.hnj.sms2telegram.telegram

import android.content.Context
import android.util.Log
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import life.hnj.sms2telegram.settings.SettingsRepository
import life.hnj.sms2telegram.smshandler.TelegramMessageWorker

object TelegramTransport {
    private const val TAG = "TelegramTransport"

    fun enqueueSend(context: Context, message: String) {
        val repository = SettingsRepository(context)
        val botKey = repository.getTelegramBotKeyBlocking()
        val chatId = repository.getTelegramChatIdBlocking()
        if (botKey.isBlank() || chatId.isBlank()) {
            Log.w(TAG, "Telegram credentials are not configured, message dropped")
            return
        }

        val input = Data.Builder()
            .putString(TelegramMessageWorker.KEY_BOT_KEY, botKey)
            .putString(TelegramMessageWorker.KEY_CHAT_ID, chatId)
            .putString(TelegramMessageWorker.KEY_MESSAGE, message)
            .build()
        val work = OneTimeWorkRequestBuilder<TelegramMessageWorker>()
            .setInputData(input)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueue(work)
    }
}

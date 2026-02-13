package life.hnj.sms2telegram.smshandler

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import life.hnj.sms2telegram.telegram.TelegramApi

class TelegramMessageWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : Worker(appContext, workerParams) {

    override fun doWork(): Result {
        val botKey = inputData.getString(KEY_BOT_KEY).orEmpty()
        val chatId = inputData.getString(KEY_CHAT_ID).orEmpty()
        val msg = inputData.getString(KEY_MESSAGE).orEmpty()
        if (botKey.isBlank() || chatId.isBlank() || msg.isBlank()) {
            Log.w(TAG, "Invalid Telegram worker input")
            return Result.failure()
        }

        return try {
            if (TelegramApi.sendMessage(botKey, chatId, msg)) {
                Result.success()
            } else {
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Telegram send failed", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "TelegramMessageWorker"
        const val KEY_BOT_KEY = "bot_key"
        const val KEY_CHAT_ID = "chat_id"
        const val KEY_MESSAGE = "msg"
    }
}

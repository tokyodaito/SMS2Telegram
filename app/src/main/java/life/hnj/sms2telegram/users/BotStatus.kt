package life.hnj.sms2telegram.users

data class BotStatus(
    val isValid: Boolean,
    val botId: Long? = null,
    val botUsername: String? = null,
    val checkedAt: Long = System.currentTimeMillis(),
    val lastError: String? = null,
)

package life.hnj.sms2telegram.users

data class LinkedUser(
    val chatId: String,
    val userId: Long,
    val displayName: String,
    val username: String? = null,
    val avatarLocalPath: String? = null,
    val linkedAt: Long = System.currentTimeMillis(),
)

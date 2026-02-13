package life.hnj.sms2telegram.settings

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.preference.PreferenceDataStore
import androidx.preference.PreferenceManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import life.hnj.sms2telegram.dataStore
import life.hnj.sms2telegram.events.EventType
import life.hnj.sms2telegram.users.BotStatus
import life.hnj.sms2telegram.users.LinkedUser
import life.hnj.sms2telegram.users.UserJson

class SettingsRepository(private val context: Context) {
    private val appContext = context.applicationContext

    suspend fun migrateLegacyIfNeeded() {
        if (getBoolean(MIGRATED_KEY, false)) {
            return
        }
        val legacy = PreferenceManager.getDefaultSharedPreferences(appContext)
        appContext.dataStore.edit { prefs ->
            copyLegacyStringIfMissing(legacy, prefs, TELEGRAM_BOT_KEY)
            copyLegacyStringIfMissing(legacy, prefs, TELEGRAM_CHAT_ID_KEY)
            copyLegacyStringIfMissing(legacy, prefs, SIM0_NUMBER_KEY)
            copyLegacyStringIfMissing(legacy, prefs, SIM1_NUMBER_KEY)
            copyLegacyStringIfMissing(legacy, prefs, ADMIN_CHAT_IDS_KEY)
            prefs[booleanPreferencesKey(MIGRATED_KEY)] = true
        }
    }

    suspend fun isSyncEnabled(): Boolean = getBoolean(SYNC_ENABLED_KEY, false)

    fun isSyncEnabledBlocking(): Boolean = runBlocking { isSyncEnabled() }

    suspend fun setSyncEnabled(value: Boolean) {
        setBoolean(SYNC_ENABLED_KEY, value)
    }

    suspend fun isRemoteControlEnabled(): Boolean = getBoolean(REMOTE_CONTROL_ENABLED_KEY, false)

    fun isRemoteControlEnabledBlocking(): Boolean = runBlocking { isRemoteControlEnabled() }

    suspend fun setRemoteControlEnabled(enabled: Boolean) {
        setBoolean(REMOTE_CONTROL_ENABLED_KEY, enabled)
    }

    suspend fun isPairingActive(): Boolean = getBoolean(PAIRING_ACTIVE_KEY, false)

    fun isPairingActiveBlocking(): Boolean = runBlocking { isPairingActive() }

    suspend fun getPairingCode(): String = getString(PAIRING_CODE_KEY, "")

    fun getPairingCodeBlocking(): String = runBlocking { getPairingCode() }

    suspend fun getPairingExpiresAt(): Long = getLong(PAIRING_EXPIRES_AT_KEY, 0L)

    fun getPairingExpiresAtBlocking(): Long = runBlocking { getPairingExpiresAt() }

    suspend fun startPairing(code: String, expiresAt: Long) {
        appContext.dataStore.edit { prefs ->
            prefs[booleanPreferencesKey(PAIRING_ACTIVE_KEY)] = true
            prefs[stringPreferencesKey(PAIRING_CODE_KEY)] = code
            prefs[longPreferencesKey(PAIRING_EXPIRES_AT_KEY)] = expiresAt
        }
    }

    fun startPairingBlocking(code: String, expiresAt: Long) =
        runBlocking { startPairing(code, expiresAt) }

    suspend fun stopPairing() {
        appContext.dataStore.edit { prefs ->
            prefs[booleanPreferencesKey(PAIRING_ACTIVE_KEY)] = false
            prefs[stringPreferencesKey(PAIRING_CODE_KEY)] = ""
            prefs[longPreferencesKey(PAIRING_EXPIRES_AT_KEY)] = 0L
        }
    }

    fun stopPairingBlocking() = runBlocking { stopPairing() }

    suspend fun getLinkedUsers(): List<LinkedUser> {
        val raw = dataStoreSnapshot()[stringPreferencesKey(LINKED_USERS_JSON_KEY)]
        return UserJson.usersFromJson(raw)
    }

    fun getLinkedUsersBlocking(): List<LinkedUser> = runBlocking { getLinkedUsers() }

    suspend fun upsertLinkedUser(user: LinkedUser) {
        val existing = getLinkedUsers().toMutableList()
        val idx = existing.indexOfFirst { it.chatId == user.chatId }
        if (idx >= 0) {
            existing[idx] = user
        } else {
            existing.add(user)
        }
        appContext.dataStore.edit { prefs ->
            prefs[stringPreferencesKey(LINKED_USERS_JSON_KEY)] = UserJson.usersToJson(existing)
        }
    }

    fun upsertLinkedUserBlocking(user: LinkedUser) = runBlocking { upsertLinkedUser(user) }

    suspend fun getBotStatus(): BotStatus? {
        val raw = dataStoreSnapshot()[stringPreferencesKey(BOT_STATUS_JSON_KEY)]
        return UserJson.botStatusFromJson(raw)
    }

    fun getBotStatusBlocking(): BotStatus? = runBlocking { getBotStatus() }

    suspend fun setBotStatus(status: BotStatus) {
        appContext.dataStore.edit { prefs ->
            prefs[stringPreferencesKey(BOT_STATUS_JSON_KEY)] = UserJson.botStatusToJson(status)
        }
    }

    fun setBotStatusBlocking(status: BotStatus) = runBlocking { setBotStatus(status) }

    suspend fun clearBotStatus() {
        appContext.dataStore.edit { prefs ->
            prefs[stringPreferencesKey(BOT_STATUS_JSON_KEY)] = ""
        }
    }

    suspend fun isEventEnabled(type: EventType): Boolean {
        return getBoolean(eventEnabledKeyName(type), defaultEnabled(type))
    }

    fun isEventEnabledBlocking(type: EventType): Boolean = runBlocking { isEventEnabled(type) }

    fun canForwardEventBlocking(type: EventType): Boolean = runBlocking {
        val snapshot = dataStoreSnapshot()
        val syncEnabled = snapshot[booleanPreferencesKey(SYNC_ENABLED_KEY)] ?: false
        if (!syncEnabled) {
            return@runBlocking false
        }
        val eventEnabled =
            snapshot[booleanPreferencesKey(eventEnabledKeyName(type))] ?: defaultEnabled(type)
        return@runBlocking eventEnabled
    }

    suspend fun setEventEnabled(type: EventType, enabled: Boolean) {
        setBoolean(eventEnabledKeyName(type), enabled)
    }

    fun setEventEnabledBlocking(type: EventType, enabled: Boolean) =
        runBlocking { setEventEnabled(type, enabled) }

    suspend fun setAllEventsEnabled(enabled: Boolean) {
        appContext.dataStore.edit { prefs ->
            EventType.entries.forEach { type ->
                prefs[booleanPreferencesKey(eventEnabledKeyName(type))] = enabled
            }
        }
    }

    fun setAllEventsEnabledBlocking(enabled: Boolean) = runBlocking { setAllEventsEnabled(enabled) }

    fun getEventStatusBlocking(): Map<EventType, Boolean> = runBlocking {
        EventType.entries.associateWith { isEventEnabled(it) }
    }

    suspend fun getTelegramBotKey(): String = getString(TELEGRAM_BOT_KEY, "")

    fun getTelegramBotKeyBlocking(): String = runBlocking { getTelegramBotKey() }

    suspend fun setTelegramBotKey(value: String) = setString(TELEGRAM_BOT_KEY, value)

    suspend fun getTelegramChatId(): String = getString(TELEGRAM_CHAT_ID_KEY, "")

    fun getTelegramChatIdBlocking(): String = runBlocking { getTelegramChatId() }

    fun getTelegramTargetBlocking(): TelegramTarget? = runBlocking {
        val snapshot = dataStoreSnapshot()
        val botKey = snapshot[stringPreferencesKey(TELEGRAM_BOT_KEY)]
            ?: PreferenceManager.getDefaultSharedPreferences(appContext).getString(TELEGRAM_BOT_KEY, "")
            .orEmpty()
        val chatId = snapshot[stringPreferencesKey(TELEGRAM_CHAT_ID_KEY)]
            ?: PreferenceManager.getDefaultSharedPreferences(appContext).getString(TELEGRAM_CHAT_ID_KEY, "")
            .orEmpty()
        if (botKey.isBlank() || chatId.isBlank()) {
            return@runBlocking null
        }
        return@runBlocking TelegramTarget(botKey = botKey, chatId = chatId)
    }

    suspend fun setTelegramChatId(value: String) = setString(TELEGRAM_CHAT_ID_KEY, value)

    suspend fun getAdminChatIdsRaw(): String = getString(ADMIN_CHAT_IDS_KEY, "")

    fun getAdminChatIdsRawBlocking(): String = runBlocking { getAdminChatIdsRaw() }

    fun hasAdminChatsConfiguredBlocking(): Boolean {
        return getAdminChatIdsRawBlocking()
            .split(",", "\n", " ")
            .map { it.trim() }
            .any { it.isNotEmpty() }
    }

    suspend fun setAdminChatIdsRaw(value: String) = setString(ADMIN_CHAT_IDS_KEY, value)

    suspend fun isAdminChatAllowed(chatId: String): Boolean {
        val linked = getLinkedUsers()
        if (linked.isNotEmpty()) {
            return linked.any { it.chatId == chatId.trim() }
        }
        val allowed = parseAdminChatIds()
        if (allowed.isEmpty()) {
            return false
        }
        return allowed.contains(chatId.trim())
    }

    fun isAdminChatAllowedBlocking(chatId: String): Boolean = runBlocking { isAdminChatAllowed(chatId) }

    suspend fun getSimNumber(slot: Int): String {
        return when (slot) {
            0 -> getString(SIM0_NUMBER_KEY, "Please configure phone number in settings")
            1 -> getString(SIM1_NUMBER_KEY, "Please configure phone number in settings")
            else -> "Unsupported feature (please contact the developer)"
        }
    }

    fun getSimNumberBlocking(slot: Int): String = runBlocking { getSimNumber(slot) }

    suspend fun getTelegramOffset(): Long = getLong(TELEGRAM_UPDATES_OFFSET_KEY, 0L)

    fun getTelegramOffsetBlocking(): Long = runBlocking { getTelegramOffset() }

    suspend fun setTelegramOffset(value: Long) {
        appContext.dataStore.edit { prefs ->
            prefs[longPreferencesKey(TELEGRAM_UPDATES_OFFSET_KEY)] = value
        }
    }

    fun setTelegramOffsetBlocking(value: Long) = runBlocking { setTelegramOffset(value) }

    suspend fun getString(key: String, defaultValue: String): String {
        val dsValue = dataStoreSnapshot()[stringPreferencesKey(key)]
        if (dsValue != null) {
            return dsValue
        }
        val legacy = PreferenceManager.getDefaultSharedPreferences(appContext)
        return legacy.getString(key, defaultValue) ?: defaultValue
    }

    fun getStringBlocking(key: String, defaultValue: String): String =
        runBlocking { getString(key, defaultValue) }

    suspend fun setString(key: String, value: String) {
        appContext.dataStore.edit { prefs ->
            prefs[stringPreferencesKey(key)] = value
        }
    }

    fun setStringBlocking(key: String, value: String) = runBlocking { setString(key, value) }

    suspend fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        val dsValue = dataStoreSnapshot()[booleanPreferencesKey(key)]
        return dsValue ?: defaultValue
    }

    fun getBooleanBlocking(key: String, defaultValue: Boolean): Boolean =
        runBlocking { getBoolean(key, defaultValue) }

    suspend fun setBoolean(key: String, value: Boolean) {
        appContext.dataStore.edit { prefs ->
            prefs[booleanPreferencesKey(key)] = value
        }
    }

    fun setBooleanBlocking(key: String, value: Boolean) = runBlocking { setBoolean(key, value) }

    fun isBooleanKey(key: String): Boolean {
        if (key == SYNC_ENABLED_KEY) {
            return true
        }
        return key.startsWith(EVENT_ENABLED_PREFIX)
    }

    private suspend fun parseAdminChatIds(): Set<String> {
        val raw = getAdminChatIdsRaw()
        return raw.split(",", "\n", " ")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
    }

    private suspend fun getLong(key: String, defaultValue: Long): Long {
        return dataStoreSnapshot()[longPreferencesKey(key)] ?: defaultValue
    }

    private fun copyLegacyStringIfMissing(
        legacy: android.content.SharedPreferences,
        prefs: MutablePreferences,
        key: String
    ) {
        val dsKey = stringPreferencesKey(key)
        val hasDsValue = prefs.contains(dsKey)
        if (hasDsValue) {
            return
        }
        val legacyValue = legacy.getString(key, null)
        if (!legacyValue.isNullOrEmpty()) {
            prefs[dsKey] = legacyValue
        }
    }

    private suspend fun dataStoreSnapshot(): Preferences {
        return appContext.dataStore.data.first()
    }

    companion object {
        const val SYNC_ENABLED_KEY = "sms2telegram.settings.tg.enabled"
        const val REMOTE_CONTROL_ENABLED_KEY = "sms2telegram.settings.tg.remote_control.enabled"
        const val PAIRING_ACTIVE_KEY = "sms2telegram.settings.pairing.active"
        const val PAIRING_CODE_KEY = "sms2telegram.settings.pairing.code"
        const val PAIRING_EXPIRES_AT_KEY = "sms2telegram.settings.pairing.expires_at"
        const val LINKED_USERS_JSON_KEY = "sms2telegram.settings.users.json"
        const val BOT_STATUS_JSON_KEY = "sms2telegram.settings.bot.status.json"
        const val TELEGRAM_BOT_KEY = "telegram_bot_key"
        const val TELEGRAM_CHAT_ID_KEY = "telegram_chat_id"
        const val ADMIN_CHAT_IDS_KEY = "telegram_admin_chat_ids"
        const val SIM0_NUMBER_KEY = "sim0_number"
        const val SIM1_NUMBER_KEY = "sim1_number"
        const val TELEGRAM_UPDATES_OFFSET_KEY = "telegram_updates_offset"
        private const val EVENT_ENABLED_PREFIX = "sms2telegram.settings.events."
        private const val MIGRATED_KEY = "sms2telegram.settings.migrated.v1"

        fun eventEnabledKeyName(type: EventType): String {
            return "${EVENT_ENABLED_PREFIX}${type.keySuffix}"
        }

        fun defaultEnabled(type: EventType): Boolean {
            return type == EventType.SMS
        }
    }
}

data class TelegramTarget(
    val botKey: String,
    val chatId: String,
)

class AppPreferenceDataStore(context: Context) : PreferenceDataStore() {
    private val repository = SettingsRepository(context)

    override fun putString(key: String?, value: String?) {
        if (key == null) {
            return
        }
        repository.setStringBlocking(key, value ?: "")
    }

    override fun getString(key: String?, defValue: String?): String {
        if (key == null) {
            return defValue ?: ""
        }
        return repository.getStringBlocking(key, defValue ?: "")
    }

    override fun putBoolean(key: String?, value: Boolean) {
        if (key == null) {
            return
        }
        repository.setBooleanBlocking(key, value)
    }

    override fun getBoolean(key: String?, defValue: Boolean): Boolean {
        if (key == null) {
            return defValue
        }
        return repository.getBooleanBlocking(key, defValue)
    }
}

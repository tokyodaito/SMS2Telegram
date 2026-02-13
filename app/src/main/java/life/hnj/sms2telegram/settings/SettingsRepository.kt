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

    suspend fun isEventEnabled(type: EventType): Boolean {
        return getBoolean(eventEnabledKeyName(type), defaultEnabled(type))
    }

    fun isEventEnabledBlocking(type: EventType): Boolean = runBlocking { isEventEnabled(type) }

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

    suspend fun setTelegramChatId(value: String) = setString(TELEGRAM_CHAT_ID_KEY, value)

    suspend fun getAdminChatIdsRaw(): String = getString(ADMIN_CHAT_IDS_KEY, "")

    fun getAdminChatIdsRawBlocking(): String = runBlocking { getAdminChatIdsRaw() }

    suspend fun setAdminChatIdsRaw(value: String) = setString(ADMIN_CHAT_IDS_KEY, value)

    suspend fun isAdminChatAllowed(chatId: String): Boolean {
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

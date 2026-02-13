package life.hnj.sms2telegram

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import java.io.File
import java.security.SecureRandom
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import life.hnj.sms2telegram.runtime.SyncRuntimeManager
import life.hnj.sms2telegram.settings.SettingsRepository
import life.hnj.sms2telegram.telegram.TelegramApi
import life.hnj.sms2telegram.users.BotStatus
import life.hnj.sms2telegram.ui.UserAdapter

class MainActivity : AppCompatActivity() {
    private lateinit var settingsRepository: SettingsRepository

    private lateinit var botKeyInput: TextInputEditText
    private lateinit var saveKeyBtn: MaterialButton
    private lateinit var checkBtn: MaterialButton
    private lateinit var statusIcon: ImageView
    private lateinit var statusText: TextView

    private lateinit var addUserBtn: MaterialButton
    private lateinit var usersEmptyText: TextView
    private lateinit var usersList: RecyclerView
    private val userAdapter = UserAdapter()

    private lateinit var syncSwitch: SwitchCompat

    private var pairingPollJob: Job? = null
    private var pairingCountdownJob: Job? = null
    private var pairingDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.action_bar))

        settingsRepository = SettingsRepository(applicationContext)

        botKeyInput = findViewById(R.id.bot_key_input)
        saveKeyBtn = findViewById(R.id.btn_save_key)
        checkBtn = findViewById(R.id.btn_check_connection)
        statusIcon = findViewById(R.id.connection_status_icon)
        statusText = findViewById(R.id.connection_status_text)

        addUserBtn = findViewById(R.id.btn_add_user)
        usersEmptyText = findViewById(R.id.users_empty_text)
        usersList = findViewById(R.id.users_list)
        usersList.layoutManager = LinearLayoutManager(this)
        usersList.adapter = userAdapter

        syncSwitch = findViewById(R.id.enable_telegram_sync)

        lifecycleScope.launch {
            withContext(Dispatchers.IO) { settingsRepository.migrateLegacyIfNeeded() }
            refreshAll()
        }

        saveKeyBtn.setOnClickListener {
            val key = botKeyInput.text?.toString().orEmpty().trim()
            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    settingsRepository.setTelegramBotKey(key)
                    settingsRepository.clearBotStatus()
                }
                refreshBotStatus()
                checkConnectionAndPersist()
            }
        }

        checkBtn.setOnClickListener {
            lifecycleScope.launch { checkConnectionAndPersist() }
        }

        addUserBtn.setOnClickListener {
            lifecycleScope.launch { startPairingFlow() }
        }

        syncSwitch.setOnCheckedChangeListener { _, isChecked ->
            lifecycleScope.launch(Dispatchers.IO) {
                settingsRepository.setSyncEnabled(isChecked)
            }
            if (isChecked) {
                SyncRuntimeManager.start(applicationContext)
                Toast.makeText(applicationContext, "Sync enabled", Toast.LENGTH_SHORT).show()
            } else {
                SyncRuntimeManager.stop(applicationContext)
                Toast.makeText(applicationContext, "Sync disabled", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        pairingPollJob?.cancel()
        pairingCountdownJob?.cancel()
        pairingDialog?.dismiss()
        super.onDestroy()
    }

    private suspend fun refreshAll() {
        val botKey = withContext(Dispatchers.IO) { settingsRepository.getTelegramBotKey() }
        botKeyInput.setText(botKey)

        val syncEnabled = withContext(Dispatchers.IO) { settingsRepository.isSyncEnabled() }
        syncSwitch.isChecked = syncEnabled

        refreshBotStatus()
        refreshUsers()
    }

    private suspend fun refreshBotStatus() {
        val status = withContext(Dispatchers.IO) { settingsRepository.getBotStatus() }
        renderBotStatus(status)
    }

    private suspend fun refreshUsers() {
        val users = withContext(Dispatchers.IO) { settingsRepository.getLinkedUsers() }
        userAdapter.submitList(users)
        usersEmptyText.visibility = if (users.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun renderBotStatus(status: BotStatus?) {
        when {
            status == null -> {
                statusIcon.setImageResource(android.R.drawable.presence_invisible)
                statusText.text = getString(R.string.connection_unknown)
                addUserBtn.isEnabled = false
            }

            status.isValid -> {
                statusIcon.setImageResource(android.R.drawable.presence_online)
                val suffix = status.botUsername?.let { "@$it" } ?: "id=${status.botId ?: "?"}"
                statusText.text = "Connected as $suffix"
                addUserBtn.isEnabled = true
            }

            else -> {
                statusIcon.setImageResource(android.R.drawable.presence_busy)
                statusText.text = status.lastError ?: "Connection failed"
                addUserBtn.isEnabled = false
            }
        }
    }

    private suspend fun checkConnectionAndPersist() {
        val key = botKeyInput.text?.toString().orEmpty().trim()
        if (key.isBlank()) {
            Toast.makeText(this, "Bot API key is empty", Toast.LENGTH_SHORT).show()
            return
        }
        statusIcon.setImageResource(android.R.drawable.presence_away)
        statusText.text = "Checking..."
        addUserBtn.isEnabled = false

        val info = withContext(Dispatchers.IO) {
            runCatching { TelegramApi.getMe(key) }.getOrNull()
        }
        val status = if (info != null) {
            BotStatus(isValid = true, botId = info.id, botUsername = info.username, lastError = null)
        } else {
            BotStatus(isValid = false, lastError = "Invalid key or network error")
        }
        withContext(Dispatchers.IO) {
            settingsRepository.setTelegramBotKey(key)
            settingsRepository.setBotStatus(status)
        }
        renderBotStatus(status)
    }

    private suspend fun startPairingFlow() {
        val status = withContext(Dispatchers.IO) { settingsRepository.getBotStatus() }
        if (status?.isValid != true) {
            Toast.makeText(this, "Check bot connection first", Toast.LENGTH_SHORT).show()
            return
        }
        val botKey = botKeyInput.text?.toString().orEmpty().trim()
        if (botKey.isBlank()) {
            Toast.makeText(this, "Bot API key is empty", Toast.LENGTH_SHORT).show()
            return
        }

        val code = generateCode()
        val expiresAt = System.currentTimeMillis() + 5 * 60 * 1000L
        withContext(Dispatchers.IO) {
            settingsRepository.startPairing(code, expiresAt)
        }

        copyToClipboard("SMS2Telegram code", code)
        addUserBtn.isEnabled = false
        showPairingDialog(code, expiresAt)

        pairingPollJob?.cancel()
        pairingCountdownJob?.cancel()
        pairingPollJob = lifecycleScope.launch(Dispatchers.IO) {
            pollForPairing(botKey, code, expiresAt)
        }
        pairingCountdownJob = lifecycleScope.launch {
            updateCountdown(expiresAt)
        }
    }

    private fun showPairingDialog(code: String, expiresAt: Long) {
        val view = layoutInflater.inflate(R.layout.dialog_pair_user, null)
        val codeView = view.findViewById<TextView>(R.id.pairing_code)
        val countdown = view.findViewById<TextView>(R.id.pairing_countdown)
        codeView.text = code
        countdown.text = formatRemaining(expiresAt)

        pairingDialog?.dismiss()
        pairingDialog = AlertDialog.Builder(this)
            .setTitle("Add user")
            .setView(view)
            .setNegativeButton("Cancel") { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) { settingsRepository.stopPairing() }
                pairingPollJob?.cancel()
                pairingCountdownJob?.cancel()
                addUserBtn.isEnabled = true
            }
            .setOnDismissListener {
                lifecycleScope.launch(Dispatchers.IO) { settingsRepository.stopPairing() }
                pairingPollJob?.cancel()
                pairingCountdownJob?.cancel()
                addUserBtn.isEnabled = true
            }
            .create()
        pairingDialog?.show()
    }

    private suspend fun updateCountdown(expiresAt: Long) {
        while (isActive) {
            val remaining = expiresAt - System.currentTimeMillis()
            val dialog = pairingDialog ?: return
            val countdown = dialog.findViewById<TextView>(R.id.pairing_countdown)
            countdown?.text = formatRemaining(expiresAt)
            if (remaining <= 0) {
                break
            }
            delay(1000)
        }
    }

    private suspend fun pollForPairing(botKey: String, code: String, expiresAt: Long) {
        var offset = settingsRepository.getTelegramOffsetBlocking()
        while (isActive && System.currentTimeMillis() < expiresAt) {
            val updates = runCatching { TelegramApi.getUpdates(botKey, offset, timeoutSeconds = 3) }.getOrNull()
            if (updates == null || !updates.optBoolean("ok", false)) {
                delay(1000)
                continue
            }
            val results = updates.optJSONArray("result")
            if (results == null || results.length() == 0) {
                continue
            }

            var maxOffset = offset
            for (i in 0 until results.length()) {
                val item = results.optJSONObject(i) ?: continue
                val updateId = item.optLong("update_id", -1L)
                if (updateId >= 0L) {
                    maxOffset = maxOf(maxOffset, updateId + 1)
                }

                val message = item.optJSONObject("message") ?: continue
                val text = message.optString("text", "").trim()
                if (text != code) {
                    continue
                }
                val chat = message.optJSONObject("chat") ?: continue
                val chatType = chat.optString("type", "")
                if (chatType != "private") {
                    continue
                }
                val chatId = chat.optLong("id", 0L).toString()
                if (chatId == "0") {
                    continue
                }
                val from = message.optJSONObject("from") ?: continue
                val userId = from.optLong("id", -1L)
                if (userId <= 0L) {
                    continue
                }
                val username = from.optString("username").takeIf { it.isNotBlank() }
                val first = from.optString("first_name").takeIf { it.isNotBlank() }
                val last = from.optString("last_name").takeIf { it.isNotBlank() }
                val displayName = listOfNotNull(first, last).joinToString(" ").ifBlank {
                    username ?: "User $userId"
                }

                val avatarPath = runCatching { downloadAvatar(botKey, userId) }.getOrNull()
                val user = LinkedUser(
                    chatId = chatId,
                    userId = userId,
                    displayName = displayName,
                    username = username,
                    avatarLocalPath = avatarPath,
                )
                settingsRepository.upsertLinkedUserBlocking(user)
                settingsRepository.setTelegramOffsetBlocking(maxOffset)
                settingsRepository.stopPairingBlocking()

                runCatching {
                    TelegramApi.sendMessage(botKey, chatId, "Linked successfully. You will now receive events.")
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "User connected: $displayName", Toast.LENGTH_SHORT)
                        .show()
                    pairingDialog?.dismiss()
                    addUserBtn.isEnabled = true
                    refreshUsers()
                }
                return
            }
            offset = maxOffset
            settingsRepository.setTelegramOffsetBlocking(offset)
        }

        settingsRepository.stopPairingBlocking()
        withContext(Dispatchers.Main) {
            Toast.makeText(this@MainActivity, "Pairing timed out", Toast.LENGTH_SHORT).show()
            pairingDialog?.dismiss()
            addUserBtn.isEnabled = true
        }
    }

    private fun downloadAvatar(botKey: String, userId: Long): String? {
        val photos = TelegramApi.getUserProfilePhotos(botKey, userId) ?: return null
        if (!photos.optBoolean("ok", false)) {
            return null
        }
        val result = photos.optJSONObject("result") ?: return null
        val arr = result.optJSONArray("photos") ?: return null
        if (arr.length() == 0) {
            return null
        }
        val sizesArr = arr.optJSONArray(0) ?: return null
        if (sizesArr.length() == 0) {
            return null
        }
        val best = sizesArr.optJSONObject(sizesArr.length() - 1) ?: return null
        val fileId = best.optString("file_id", "")
        if (fileId.isBlank()) {
            return null
        }
        val fileInfo = TelegramApi.getFile(botKey, fileId) ?: return null
        if (!fileInfo.optBoolean("ok", false)) {
            return null
        }
        val filePath = fileInfo.optJSONObject("result")?.optString("file_path", "").orEmpty()
        if (filePath.isBlank()) {
            return null
        }
        val bytes = TelegramApi.downloadFile(botKey, filePath) ?: return null
        val avatarsDir = File(filesDir, "avatars")
        if (!avatarsDir.exists()) {
            avatarsDir.mkdirs()
        }
        val out = File(avatarsDir, "$userId.jpg")
        out.writeBytes(bytes)
        return out.absolutePath
    }

    private fun generateCode(): String {
        val n = SecureRandom().nextInt(1_000_000)
        return String.format(Locale.US, "%06d", n)
    }

    private fun copyToClipboard(label: String, text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(this, "Code copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    private fun formatRemaining(expiresAt: Long): String {
        val remainingMs = (expiresAt - System.currentTimeMillis()).coerceAtLeast(0L)
        val totalSeconds = (remainingMs / 1000).toInt()
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.getDefault(), "Expires in %d:%02d", minutes, seconds)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val toolbar = findViewById<Toolbar>(R.id.action_bar)
        toolbar.inflateMenu(R.menu.actionbar_menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                val intent = Intent(this, SettingsActivity::class.java).apply {
                    action = Intent.CATEGORY_PREFERENCE
                }
                startActivity(intent)
                true
            }

            else -> {
                super.onOptionsItemSelected(item)
            }
        }
    }
}

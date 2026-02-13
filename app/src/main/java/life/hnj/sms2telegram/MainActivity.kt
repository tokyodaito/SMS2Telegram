package life.hnj.sms2telegram

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.appcompat.widget.Toolbar
import kotlinx.coroutines.runBlocking
import life.hnj.sms2telegram.runtime.SyncRuntimeManager
import life.hnj.sms2telegram.settings.SettingsRepository

class MainActivity : AppCompatActivity() {
    private lateinit var settingsRepository: SettingsRepository

    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val denied = result.filterValues { granted -> !granted }.keys
            if (denied.isNotEmpty()) {
                Toast.makeText(
                    applicationContext,
                    "Some permissions are denied: ${denied.joinToString()}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.action_bar))

        settingsRepository = SettingsRepository(applicationContext)
        runBlocking { settingsRepository.migrateLegacyIfNeeded() }
        val syncEnabled = settingsRepository.isSyncEnabledBlocking()

        val toggle = findViewById<SwitchCompat>(R.id.enable_telegram_sync)
        if (syncEnabled) {
            requestAllRequiredPermissions()
            SyncRuntimeManager.reconfigure(applicationContext)
        }
        toggle.isChecked = syncEnabled
        toggle.setOnCheckedChangeListener { _, isChecked ->
            runBlocking { settingsRepository.setSyncEnabled(isChecked) }
            if (isChecked) {
                requestAllRequiredPermissions()
                SyncRuntimeManager.start(applicationContext)
                Toast.makeText(applicationContext, "Sync enabled", Toast.LENGTH_SHORT).show()
            } else {
                SyncRuntimeManager.stop(applicationContext)
                Toast.makeText(applicationContext, "Sync disabled", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun requestAllRequiredPermissions() {
        val missing = PermissionHelper.getMissingDangerousPermissions(applicationContext)
        if (missing.isNotEmpty()) {
            requestPermissionsLauncher.launch(missing.toTypedArray())
        }
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

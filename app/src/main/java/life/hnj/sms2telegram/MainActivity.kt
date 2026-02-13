package life.hnj.sms2telegram

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Build
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import kotlinx.coroutines.runBlocking
import life.hnj.sms2telegram.runtime.SyncRuntimeManager
import life.hnj.sms2telegram.settings.SettingsRepository

class MainActivity : AppCompatActivity() {
    private lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        val requestPermissionLauncher =
            registerForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { isGranted: Boolean ->
                if (isGranted) {
                    // Permission is granted. Continue the action or workflow in your
                    // app.
                } else {
                    Toast.makeText(
                        applicationContext,
                        "A permission was denied. Some events may not be delivered.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }


        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.action_bar))

        settingsRepository = SettingsRepository(applicationContext)
        runBlocking { settingsRepository.migrateLegacyIfNeeded() }
        val syncEnabled = settingsRepository.isSyncEnabledBlocking()

        val toggle = findViewById<SwitchCompat>(R.id.enable_telegram_sync)
        if (syncEnabled) {
            requestPermissions(requestPermissionLauncher)
            SyncRuntimeManager.start(applicationContext)
        }
        toggle.isChecked = syncEnabled
        toggle.setOnCheckedChangeListener { _, isChecked ->
            runBlocking { settingsRepository.setSyncEnabled(isChecked) }
            if (isChecked) {
                requestPermissions(requestPermissionLauncher)
                SyncRuntimeManager.start(applicationContext)
                Toast.makeText(applicationContext, "The service started", Toast.LENGTH_SHORT).show()
            } else {
                SyncRuntimeManager.stop(applicationContext)
                Toast.makeText(applicationContext, "The service stopped", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun requestPermissions(requestPermissionLauncher: ActivityResultLauncher<String>) {
        checkPermission(Manifest.permission.RECEIVE_SMS, requestPermissionLauncher)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            checkPermission(Manifest.permission.POST_NOTIFICATIONS, requestPermissionLauncher)
        }
        checkPermission(Manifest.permission.READ_PHONE_STATE, requestPermissionLauncher)
    }

    private fun checkPermission(
        perm: String,
        requestPermissionLauncher: ActivityResultLauncher<String>
    ) {
        if (ContextCompat.checkSelfPermission(
                applicationContext, perm
            ) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(perm)
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

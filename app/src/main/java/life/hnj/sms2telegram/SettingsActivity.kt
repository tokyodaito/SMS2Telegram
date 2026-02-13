package life.hnj.sms2telegram

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import life.hnj.sms2telegram.events.EventType
import life.hnj.sms2telegram.runtime.SyncRuntimeManager
import life.hnj.sms2telegram.settings.AppPreferenceDataStore
import life.hnj.sms2telegram.settings.SettingsRepository
import life.hnj.sms2telegram.telegram.TelegramTransport


class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)

        setSupportActionBar(findViewById(R.id.action_bar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        supportFragmentManager
            .beginTransaction()
            .replace(R.id.settings, SettingsFragment())
            .commit()
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        private lateinit var settingsRepository: SettingsRepository

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            val horizontalMargin = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 2F, resources.displayMetrics
            )
            val verticalMargin = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 2F, resources.displayMetrics
            )
            val topMargin = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                resources.getDimension(R.dimen.activity_vertical_margin) + 10,
                resources.displayMetrics
            )
            listView.setPadding(
                horizontalMargin.toInt(),
                topMargin.toInt(),
                horizontalMargin.toInt(),
                verticalMargin.toInt(),
            )

            super.onViewCreated(view, savedInstanceState)

            setupRuntimeSwitchListener()
            setupRemoteControlSwitchListener()
            setupPermissionAwareEventListeners()
            setupTestMessageAction()
        }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            settingsRepository = SettingsRepository(requireContext())
            runCatching {
                kotlinx.coroutines.runBlocking { settingsRepository.migrateLegacyIfNeeded() }
            }
            preferenceManager.preferenceDataStore = AppPreferenceDataStore(requireContext())
            setPreferencesFromResource(R.xml.root_preferences, rootKey)
        }

        private fun setupRuntimeSwitchListener() {
            val key = SettingsRepository.SYNC_ENABLED_KEY
            val syncSwitch = findPreference<SwitchPreferenceCompat>(key) ?: return
            syncSwitch.setOnPreferenceChangeListener { _, newValue ->
                val enabled = newValue as? Boolean ?: false
                if (enabled) {
                    SyncRuntimeManager.start(requireContext())
                } else {
                    SyncRuntimeManager.stop(requireContext())
                }
                true
            }
        }

        private fun setupTestMessageAction() {
            val pref = findPreference<Preference>("send_test_message") ?: return
            pref.setOnPreferenceClickListener {
                TelegramTransport.enqueueSend(
                    requireContext(),
                    "[SMS2Telegram] Test message from settings panel"
                )
                true
            }
        }

        private fun setupRemoteControlSwitchListener() {
            val key = SettingsRepository.REMOTE_CONTROL_ENABLED_KEY
            val remoteSwitch = findPreference<SwitchPreferenceCompat>(key) ?: return
            remoteSwitch.setOnPreferenceChangeListener { _, _ ->
                listView.post {
                    SyncRuntimeManager.reconfigure(requireContext())
                }
                true
            }
        }

        private fun setupPermissionAwareEventListeners() {
            setupPermissionRequestOnEnable(
                SettingsRepository.eventEnabledKeyName(EventType.SMS),
                Manifest.permission.RECEIVE_SMS
            )
            setupPermissionRequestOnEnable(
                SettingsRepository.eventEnabledKeyName(EventType.MISSED_CALL),
                Manifest.permission.READ_PHONE_STATE
            )
            setupPermissionRequestOnEnable(
                SettingsRepository.eventEnabledKeyName(EventType.SIM_STATE),
                Manifest.permission.READ_PHONE_STATE
            )
        }

        private fun setupPermissionRequestOnEnable(preferenceKey: String, permission: String) {
            val switch = findPreference<SwitchPreferenceCompat>(preferenceKey) ?: return
            switch.setOnPreferenceChangeListener { _, newValue ->
                val enabled = newValue as? Boolean ?: false
                if (enabled && ContextCompat.checkSelfPermission(
                        requireContext(),
                        permission
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    requestPermissions(arrayOf(permission), PERMISSION_REQUEST_CODE)
                }
                listView.post {
                    SyncRuntimeManager.reconfigure(requireContext())
                }
                true
            }
        }

        companion object {
            private const val PERMISSION_REQUEST_CODE = 1001
        }
    }
}

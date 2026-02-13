package life.hnj.sms2telegram

import android.os.Bundle
import android.util.TypedValue
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
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
    }
}

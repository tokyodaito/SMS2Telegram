package life.hnj.sms2telegram.intro

import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import life.hnj.sms2telegram.MainActivity
import life.hnj.sms2telegram.R
import life.hnj.sms2telegram.settings.SettingsRepository

class IntroActivity : AppCompatActivity() {
    private lateinit var settingsRepository: SettingsRepository

    private var mediaPlayer: MediaPlayer? = null

    private lateinit var bg: ImageView
    private lateinit var donut: SymbolDonutView
    private lateinit var particles: ParticleFieldView
    private lateinit var cta: ShimmerButton

    private lateinit var btnMute: ImageButton
    private lateinit var btnSkip: ImageButton
    private lateinit var btnDisable: MaterialButton

    private var muted: Boolean = false
    private var enabled: Boolean? = null
    private var visible: Boolean = false
    private var effectsStarted: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Fullscreen layout (status/navigation hidden via insets controller).
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_intro)

        settingsRepository = SettingsRepository(applicationContext)

        bg = findViewById(R.id.intro_bg)
        donut = findViewById(R.id.symbol_donut)
        particles = findViewById(R.id.particles)
        cta = findViewById(R.id.cta_button)

        btnMute = findViewById(R.id.btn_mute)
        btnSkip = findViewById(R.id.btn_skip)
        btnDisable = findViewById(R.id.btn_disable_intro)

        applyBestAvailableBackground()

        cta.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateEmitterFromCta()
        }

        btnSkip.setOnClickListener { goToMain() }
        cta.setOnClickListener { goToMain() }

        btnDisable.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                settingsRepository.setIntroEnabled(false)
            }
            goToMain()
        }

        btnMute.setOnClickListener {
            muted = !muted
            applyMuteUi()
            applyMuteToPlayer()
            lifecycleScope.launch(Dispatchers.IO) {
                settingsRepository.setIntroMuted(muted)
            }
        }

        // Load settings without blocking the main thread.
        lifecycleScope.launch {
            val introEnabled = withContext(Dispatchers.IO) { settingsRepository.isIntroEnabled() }
            muted = withContext(Dispatchers.IO) { settingsRepository.isIntroMuted() }
            enabled = introEnabled
            applyMuteUi()
            applyMuteToPlayer()
            if (!introEnabled) {
                goToMain()
                return@launch
            }
            // Ensure emitter rect is updated once the view has its final position.
            cta.post { updateEmitterFromCta() }
            maybeStartEffects()
        }
    }

    override fun onStart() {
        super.onStart()
        visible = true
        hideSystemBars()
        maybeStartEffects()
    }

    override fun onStop() {
        visible = false
        stopMusic()
        stopAnimations()
        super.onStop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemBars()
        }
    }

    private fun hideSystemBars() {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    private fun startAnimations() {
        effectsStarted = true
        donut.start()
        particles.start()
        cta.startEffects()
        updateEmitterFromCta()
    }

    private fun stopAnimations() {
        effectsStarted = false
        cta.stopEffects()
        particles.stop()
        donut.stop()
    }

    private fun maybeStartEffects() {
        val e = enabled
        if (!visible) return
        if (e != true) return
        if (effectsStarted) return
        startAnimations()
        startMusicIfAvailable()
    }

    private fun updateEmitterFromCta() {
        val ctaLoc = IntArray(2)
        val particlesLoc = IntArray(2)
        cta.getLocationOnScreen(ctaLoc)
        particles.getLocationOnScreen(particlesLoc)
        val left = (ctaLoc[0] - particlesLoc[0]).toFloat()
        val top = (ctaLoc[1] - particlesLoc[1]).toFloat()
        particles.setEmitterRect(
            left,
            top,
            left + cta.width.toFloat(),
            top + cta.height.toFloat(),
        )
    }

    private fun startMusicIfAvailable() {
        if (mediaPlayer != null) {
            applyMuteToPlayer()
            return
        }
        val musicRes = resources.getIdentifier("intro_music", "raw", packageName)
        if (musicRes == 0) {
            // No bundled music; hide mute control.
            btnMute.visibility = View.GONE
            return
        }
        val afd = runCatching { resources.openRawResourceFd(musicRes) }.getOrNull() ?: return
        val player = MediaPlayer()
        runCatching {
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            try {
                player.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            } finally {
                runCatching { afd.close() }
            }
            player.isLooping = true
            player.prepare()
            mediaPlayer = player
            applyMuteToPlayer()
            player.start()
        }.onFailure {
            runCatching { player.release() }
        }
    }

    private fun stopMusic() {
        val p = mediaPlayer ?: return
        mediaPlayer = null
        runCatching { p.stop() }
        runCatching { p.release() }
    }

    private fun applyMuteUi() {
        btnMute.setImageResource(if (muted) R.drawable.ic_volume_off else R.drawable.ic_volume_on)
        btnMute.contentDescription =
            getString(if (muted) R.string.intro_unmute else R.string.intro_mute)
    }

    private fun applyMuteToPlayer() {
        val p = mediaPlayer ?: return
        if (muted) {
            p.setVolume(0f, 0f)
        } else {
            p.setVolume(1f, 1f)
        }
    }

    private fun applyBestAvailableBackground() {
        val bgRes = resources.getIdentifier("intro_bg", "drawable", packageName)
        if (bgRes != 0) {
            bg.setImageResource(bgRes)
        } else {
            bg.setImageResource(R.drawable.intro_bg_fallback)
        }
    }

    private fun goToMain() {
        // Avoid double navigations on rapid taps.
        if (isFinishing) return
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}

package com.zakaria5729.flash_table_lamp

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : ComponentActivity() {
    private val flashlight get() = (application as LumaApplication).flashlight
    private val screenLamp get() = (application as LumaApplication).screenLamp
    private val sounds get() = (application as LumaApplication).sounds
    private val safeScreen by lazy { GuardedScreenLampActions(this, screenLamp) }
    private val safeSounds by lazy { GuardedSoundActions(this, sounds) }
    private var resetAfterStop = false
    private var pickingSound = false
    private var pendingStart: (() -> Unit)? = null
    private var notificationsGranted by mutableStateOf(false)
    private val notificationsPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationsGranted = isNotificationPermissionGranted(this)
        val start = pendingStart
        pendingStart = null
        start?.invoke()
    }
    private val pickSound = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        pickingSound = false
        if (uri != null) sounds.importSound(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as LumaApplication
        if (savedInstanceState == null || !app.hasOpenedUi) app.resetIdleSession()
        app.hasOpenedUi = true
        notificationsGranted = isNotificationPermissionGranted(this)
        enableEdgeToEdge()
        setContent {
            val state by flashlight.state.collectAsState()
            val screen by screenLamp.state.collectAsState()
            val sound by sounds.state.collectAsState()
            val dark = when (screen.theme) {
                ThemeMode.Dark -> true
                ThemeMode.Light -> false
                ThemeMode.System -> resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            }
            LaunchedEffect(screen.isOn, screen.fullscreen, screen.currentBrightness, screen.ultraDim, dark) {
                val active = screen.isOn && screen.fullscreen
                window.attributes = window.attributes.apply {
                    screenBrightness = if (active) {
                        if (screen.ultraDim) .01f else (screen.currentBrightness / 100f).coerceIn(.01f, 1f)
                    } else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                }
                val bars = WindowCompat.getInsetsController(window, window.decorView)
                if (active) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    bars.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    bars.hide(WindowInsetsCompat.Type.systemBars())
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    bars.show(WindowInsetsCompat.Type.systemBars())
                    bars.isAppearanceLightStatusBars = !dark
                    bars.isAppearanceLightNavigationBars = !dark
                }
            }
            BackHandler(screen.isOn && screen.fullscreen) {
                if (!screenLamp.state.value.touchLocked && !screenLamp.state.value.isStopping) screenLamp.showControls()
            }
            key(screen.sessionResetId) { ReadingLampApp(screen, state, safeScreen, sound, safeSounds,
                onStartScreen = { startWithNotifications(safeScreen::start) },
                onToggleFlashlight = { toggleFlashlight() },
                onEmergency = { startWithNotifications(safeScreen::emergency) },
                onToggleSound = { if (sound.isPlaying || sound.isPreparing) safeSounds.pause() else startWithNotifications(safeSounds::play) },
                onPickSound = { featureAction(this) { pickSound.launch(arrayOf("audio/*")); pickingSound = true } },
                notificationsGranted = notificationsGranted,
                onRequestNotificationPermission = { requestNotificationPermission() },
                onOpenNotificationSettings = { openAppNotificationSettings() },
                brandLogo = {
                    Image(
                        painter = painterResource(R.drawable.little_lamp_logo),
                        contentDescription = "Little Lamp logo",
                        modifier = androidx.compose.ui.Modifier.size(64.dp),
                    )
                },
            ) { darkTheme, openSounds ->
                App(state, { toggleFlashlight() }, { value -> featureAction(this) { flashlight.setStrength(value) } }, { value -> featureAction(this) { flashlight.setTimer(value) } },
                    { value -> featureAction(this) { flashlight.setDimGradually(value) } }, { value -> featureAction(this) { flashlight.setDimmingMode(value) } }, { value -> featureAction(this) { flashlight.setDimmingRate(value) } },
                    flashlight::dismissMessage, darkTheme, embedded = true,
                    onRhythmChange = { value -> featureAction(this) { flashlight.setRhythm(value) } }, onRhythmModeChange = { value -> featureAction(this) { flashlight.setRhythmMode(value) } },
                    onRhythmTimesChange = { on, off -> featureAction(this) { flashlight.setRhythmTimes(on, off) } },
                    soundContent = { EmbeddedSounds(sound, safeSounds,
                        { featureAction(this) { pickSound.launch(arrayOf("audio/*")); pickingSound = true } }, onOpenSounds = openSounds) })

            } }
        }
        if (savedInstanceState == null) handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        notificationsGranted = isNotificationPermissionGranted(this)
        flashlight.refreshNotification()
    }

    override fun onStart() {
        super.onStart()
        if (resetAfterStop && pendingStart == null && !pickingSound) (application as LumaApplication).resetIdleSession()
        resetAfterStop = false
        flashlight.setUiVisible(true)
        sounds.isUiVisible = true
    }

    override fun onStop() {
        flashlight.setUiVisible(false)
        sounds.isUiVisible = false
        if (!isChangingConfigurations && pendingStart == null && !pickingSound) resetAfterStop = true
        if (!isChangingConfigurations) screenLamp.stop()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.attributes = window.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        when (intent.action) {
            Intent.ACTION_MAIN -> {
                (application as LumaApplication).resetIdleSession()
                screenLamp.selectPage(LampPage.Flashlight)
            }
            "com.zakaria5729.flash_table_lamp.SCREEN" -> screenLamp.selectPage(LampPage.Screen)
            SoundPlaybackService.SHOW_SOUNDS -> screenLamp.selectPage(LampPage.Sounds)
        }
    }

    private fun toggleFlashlight() {
        featureAction(this) { if (flashlight.state.value.isOn) flashlight.turnOff() else startWithNotifications(flashlight::turnOn) }
    }

    private fun startWithNotifications(start: () -> Unit) {
        val preferences = getPreferences(MODE_PRIVATE)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            !preferences.getBoolean("notifications_requested", false)
        ) {
            pendingStart = start
            preferences.edit { putBoolean("notifications_requested", true) }
            notificationsPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            start()
        }
    }

    private fun isNotificationPermissionGranted(context: Context): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java)
        val areEnabled = manager?.areNotificationsEnabled() == true
        return if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED && areEnabled
        } else {
            areEnabled
        }
    }

    private fun requestNotificationPermission() {
        if (isNotificationPermissionGranted(this)) {
            notificationsGranted = true
            Toast.makeText(this, "Notification permission is already given.", Toast.LENGTH_SHORT).show()
        } else if (Build.VERSION.SDK_INT >= 33) {
            val preferences = getPreferences(MODE_PRIVATE)
            val alreadyRequested = preferences.getBoolean("notifications_requested", false)
            if (!shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) && alreadyRequested) {
                openAppNotificationSettings()
            } else {
                preferences.edit { putBoolean("notifications_requested", true) }
                notificationsPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            openAppNotificationSettings()
        }
    }

    private fun openAppNotificationSettings() {
        try {
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            }
            startActivity(intent)
        } catch (_: Exception) {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
            }
            startActivity(intent)
        }
    }
}

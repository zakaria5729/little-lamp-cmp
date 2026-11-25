package com.zakaria5729.flash_table_lamp

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import kotlin.math.ceil
import kotlin.math.roundToInt

class FlashlightService : Service() {
    private val flashlight get() = (application as LumaApplication).flashlight
    private val notifications by lazy { FlashlightNotification(this) }
    private val sessionStore by lazy { FlashlightSessionStore(this) }
    private val handler = Handler(Looper.getMainLooper())
    private var active = false
    private var callbackRegistered = false
    private var ownsTorch = false
    private var confirmedOn = false
    private var lastStrength = 0
    private var deadline = 0L
    private var rateStartedAt = 0L
    private var rateStartStrength = 1
    private var wakeLock: PowerManager.WakeLock? = null
    private var transitionWakeLock: PowerManager.WakeLock? = null
    private var transition: Runnable? = null
    private var fadingOut = false
    private var phaseOn = true
    private var expectedTorchOn = false
    private var rhythmStartedAt = 0L
    private var rhythmWakeLock: PowerManager.WakeLock? = null
    private val rhythmTick = object : Runnable {
        override fun run() {
            if (!active || fadingOut || !flashlight.state.value.rhythmEnabled) return
            val now = SystemClock.elapsedRealtime()
            rhythmWakeLock?.acquire(120_000)
            if (deadline > 0 && now >= deadline) { finish("Timer finished. Your light is off."); return }
            val state = flashlight.state.value
            val phase = rhythmPhase(now - rhythmStartedAt, state.onSeconds, state.offSeconds)
            try {
                if (phase.lit != phaseOn) {
                    cancelTransition(); flashlight.startupFinished(); phaseOn = phase.lit
                    if (phaseOn) { confirmedOn = false; writeStrength(desiredStrength()) }
                    else {
                        expectedTorchOn = false
                        flashlight.cameraId?.let { flashlight.cameraManager.setTorchMode(it, false) }
                        lastStrength = 0; flashlight.appliedStrength(0)
                    }
                }
                val untilDeadline = if (deadline > 0) (deadline - now).coerceAtLeast(1) else Long.MAX_VALUE
                handler.postDelayed(this, minOf(phase.nextChangeMillis, untilDeadline))
            } catch (_: Exception) { finish("Flashlight unavailable. Close any camera apps and try again.") }
        }
    }

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (!active || cameraId != flashlight.cameraId) return
            if (enabled) {
                confirmedOn = true
            } else if (confirmedOn && expectedTorchOn) {
                finish("The flashlight was turned off by Android or another app.")
            }
        }

        override fun onTorchModeUnavailable(cameraId: String) {
            if (active && expectedTorchOn && confirmedOn && cameraId == flashlight.cameraId) {
                finish("Flashlight unavailable. Close the camera app and try again.")
            }
        }
    }

    private val timerTick = object : Runnable {
        override fun run() {
            if (!active || deadline == 0L || fadingOut) return
            val seconds = remainingSeconds(deadline, SystemClock.elapsedRealtime())
            flashlight.tick(seconds)
            if (seconds == 0) {
                finish("Timer finished. Your light is off.")
                return
            }
            try {
                val remainingMillis = deadline - SystemClock.elapsedRealtime()
                if (phaseOn && lastStrength > 1 && transitionsEnabled() && remainingMillis <= transitionDuration()) {
                    fadeOff("Timer finished. Your light is off.", remainingMillis.toInt())
                    return
                }
                applyStrength()
                handler.postDelayed(this, nextTickDelay())
            } catch (_: Exception) {
                finish("Couldn't adjust the flashlight. The light has been turned off.")
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            fadeOff()
            return START_NOT_STICKY
        }
        if (fadingOut) return START_NOT_STICKY
        val screen = (application as LumaApplication).screenLamp.state.value
        if (batteryProtectionTriggered(screen.stopAtCriticalBattery, screen.batteryPercent, screen.charging)) {
            finish("Battery protection stopped the light.")
            return START_NOT_STICKY
        }
        var savedSession = if (!active) sessionStore.load() else null
        val start = intent?.action == ACTION_START || (intent == null && savedSession != null)
        if (!start && !active) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!active && savedSession != null && savedSession.deadline > 0 && savedSession.deadline <= SystemClock.elapsedRealtime()) {
            if (intent?.action == ACTION_START) {
                sessionStore.clear()
                savedSession = null
            } else {
                finish("Timer finished. Your light is off.")
                return START_NOT_STICKY
            }
        }
        try {
            when {
                start && !active -> {
                    deadline = savedSession?.deadline ?: 0L
                    rateStartedAt = savedSession?.rateStartedAt ?: SystemClock.elapsedRealtime()
                    rateStartStrength = savedSession?.rateStartStrength?.coerceIn(1, flashlight.state.value.maxStrength)
                        ?: flashlight.state.value.strength
                    rhythmStartedAt = savedSession?.rhythmStartedAt?.takeIf { it > 0 } ?: SystemClock.elapsedRealtime()
                    val config = flashlight.state.value
                    phaseOn = !config.rhythmEnabled || rhythmPhase(SystemClock.elapsedRealtime() - rhythmStartedAt, config.onSeconds, config.offSeconds).lit
                    if (Build.VERSION.SDK_INT >= 34) {
                        startForeground(FlashlightNotification.ID, notifications.build(deadline), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                    } else startForeground(FlashlightNotification.ID, notifications.build(deadline))
                    val fadeIn = phaseOn && savedSession == null && transitionsEnabled()
                    if (fadeIn) writeStrength(1) else if (phaseOn) applyStrength() else flashlight.appliedStrength(0)
                    active = true
                    flashlight.started(fadeIn)
                    flashlight.cameraManager.registerTorchCallback(torchCallback, handler)
                    callbackRegistered = true
                    handler.postDelayed({
                        if (active && expectedTorchOn && !confirmedOn) finish("Couldn't turn on the flashlight. Close any camera apps and try again.")
                    }, 3000)
                    restartTimer(savedSession, fadeIn)
                    if (fadeIn) startTransition(desiredStrength(), transitionDuration()) {
                        flashlight.startupFinished()
                        applyStrength()
                    }
                    scheduleRhythm(reset = false)
                }
                intent?.action == ACTION_STRENGTH || intent?.action == ACTION_DIM_CONFIG -> {
                    cancelTransition()
                    flashlight.startupFinished()
                    rateStartedAt = SystemClock.elapsedRealtime()
                    rateStartStrength = if (intent.action == ACTION_STRENGTH) flashlight.state.value.strength
                        else flashlight.state.value.currentStrength.takeIf { it > 0 } ?: flashlight.state.value.strength
                    applyStrength()
                    saveSession()
                    updateNotification()
                }
                intent?.action == ACTION_DIM -> {
                    applyStrength()
                    updateNotification()
                }
                intent?.action == ACTION_TIMER -> restartTimer()
                intent?.action == ACTION_RHYTHM -> {
                    scheduleRhythm(reset = true)
                    saveSession(); updateNotification()
                }
            }
            if (active && deadline > 0) {
                handler.removeCallbacks(timerTick)
                handler.post(timerTick)
            }
        } catch (error: Exception) {
            val message = when (error) {
                is SecurityException -> "Android blocked flashlight access. Reopen $APP_NAME and try again."
                is CameraAccessException -> "Flashlight unavailable. Close any camera apps and try again."
                else -> "Couldn't control the flashlight. Turn it on to try again."
            }
            finish(message)
        }
        return if (active) START_STICKY else START_NOT_STICKY
    }

    private fun applyStrength(): Boolean {
        if (transition != null || !phaseOn || fadingOut) return false
        return writeStrength(desiredStrength())
    }

    private fun desiredStrength(): Int {
        val state = flashlight.state.value
        return if (state.dimGradually && state.supportsDimming && deadline > 0) {
            when (state.dimmingMode) {
                DimmingMode.Auto -> fadingStrength(state.strength, deadline - SystemClock.elapsedRealtime(), state.timerSeconds * 1000L)
                else -> dimmingStrengthByRate(rateStartStrength, state.maxStrength,
                    state.dimPercentPerMinute, SystemClock.elapsedRealtime() - rateStartedAt, state.dimmingMode.intervalMillis)
            }
        } else state.strength
    }

    private fun writeStrength(level: Int): Boolean {
        val camera = checkNotNull(flashlight.cameraId)
        val state = flashlight.state.value
        if (level == lastStrength) return false
        expectedTorchOn = true
        if (Build.VERSION.SDK_INT >= 33 && state.supportsDimming) {
            flashlight.cameraManager.turnOnTorchWithStrengthLevel(camera, level)
        } else flashlight.cameraManager.setTorchMode(camera, true)
        lastStrength = level
        ownsTorch = true
        flashlight.appliedStrength(level)
        return true
    }

    private fun transitionsEnabled() = flashlight.state.value.supportsDimming && (application as LumaApplication).screenLamp.state.value.smoothTransitions
    private fun transitionDuration(): Int {
        val requested = (application as LumaApplication).screenLamp.state.value.transitionMillis
        val seconds = flashlight.state.value.timerSeconds
        return if (seconds > 0) minOf(requested, seconds * 500) else requested
    }

    private fun fadeOff(message: String? = null, duration: Int = transitionDuration()) {
        if (fadingOut) return
        sessionStore.clear()
        if (!active || !transitionsEnabled() || lastStrength <= 1) { finish(message); return }
        fadingOut = true
        handler.removeCallbacks(rhythmTick)
        releaseRhythmWakeLock()
        flashlight.stopping()
        handler.removeCallbacks(timerTick)
        updateNotification()
        startTransition(1, duration.coerceAtLeast(1)) { finish(message) }
    }

    private fun startTransition(target: Int, duration: Int, finished: () -> Unit) {
        cancelTransition()
        val start = lastStrength.coerceAtLeast(1)
        if (start == target) { finished(); return }
        transitionWakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TableLamp:LightTransition").apply { acquire(duration.toLong() + 1000) }
        val started = SystemClock.elapsedRealtime()
        val animation = object : Runnable {
            override fun run() {
                if (!active) return
                try {
                    val progress = ((SystemClock.elapsedRealtime() - started).toFloat() / duration).coerceIn(0f, 1f)
                    writeStrength((start + (target - start) * progress).roundToInt().coerceAtLeast(1))
                    if (progress >= 1f) { cancelTransition(); finished() } else handler.postDelayed(this, 32)
                } catch (_: Exception) { finish("The flashlight became unavailable.") }
            }
        }
        transition = animation; handler.post(animation)
    }

    private fun cancelTransition() {
        transition?.let(handler::removeCallbacks)
        transition = null
        transitionWakeLock?.let { if (it.isHeld) it.release() }
        transitionWakeLock = null
    }

    private fun restartTimer(savedSession: RunningSession? = null, fadingIn: Boolean = false) {
        handler.removeCallbacks(timerTick)
        releaseWakeLock()
        val seconds = flashlight.state.value.timerSeconds
        if (savedSession == null) {
            deadline = if (seconds > 0) SystemClock.elapsedRealtime() + seconds * 1000L else 0L
            rateStartedAt = SystemClock.elapsedRealtime()
            rateStartStrength = flashlight.state.value.strength
        }
        flashlight.tick(if (deadline > 0) remainingSeconds(deadline, SystemClock.elapsedRealtime()) else 0)
        if (!fadingIn) applyStrength()
        if (seconds > 0) {
            wakeLock = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Luma:AutoOff").apply {
                    setReferenceCounted(false)
                    acquire((deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0) + 10_000L)
                }
            handler.post(timerTick)
        }
        saveSession()
        if (!fadingIn) scheduleRhythm(reset = false)
        updateNotification()
    }

    private fun saveSession() {
        sessionStore.save(RunningSession(deadline, rateStartedAt, rateStartStrength, rhythmStartedAt))
    }

    private fun scheduleRhythm(reset: Boolean) {
        handler.removeCallbacks(rhythmTick)
        releaseRhythmWakeLock()
        if (!active || fadingOut) return
        if (!flashlight.state.value.rhythmEnabled) {
            if (!phaseOn) confirmedOn = false
            phaseOn = true
            applyStrength()
            return
        }
        if (reset || rhythmStartedAt == 0L) rhythmStartedAt = SystemClock.elapsedRealtime()
        if (deadline == 0L) rhythmWakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LittleLight:Rhythm").apply { setReferenceCounted(false); acquire(120_000) }
        handler.post(rhythmTick)
    }

    private fun releaseRhythmWakeLock() {
        rhythmWakeLock?.let { if (it.isHeld) it.release() }
        rhythmWakeLock = null
    }

    private fun nextTickDelay(): Long {
        val now = SystemClock.elapsedRealtime()
        val remaining = (deadline - now).coerceAtLeast(1)
        val state = flashlight.state.value
        val uiDelay = if (flashlight.isUiVisible) (remaining % 1000).takeIf { it > 0 } ?: 1000 else Long.MAX_VALUE
        val dimDelay = if (state.isFading && lastStrength > 1) {
            when (state.dimmingMode) {
                DimmingMode.Auto -> {
                    val nextThreshold = (lastStrength - 1.5) * state.timerSeconds * 1000.0 / (state.strength - 1)
                    ceil(remaining - nextThreshold).toLong() + 1
                }
                else -> {
                    val levelsPerMillis = state.maxStrength * state.dimPercentPerMinute / 100.0 / state.dimmingMode.intervalMillis
                    val nextThreshold = (rateStartStrength - lastStrength + .5) / levelsPerMillis
                    ceil(nextThreshold - (now - rateStartedAt)).toLong() + 1
                }
            }
        } else Long.MAX_VALUE
        val fadeDelay = if (phaseOn && lastStrength > 1 && transitionsEnabled() && remaining > transitionDuration())
            remaining - transitionDuration() else remaining
        return minOf(remaining, fadeDelay, minOf(uiDelay, dimDelay).coerceAtLeast(200))
    }

    private fun updateNotification() {
        notifications.show(deadline)
    }

    private fun finish(message: String? = null) {
        sessionStore.clear()
        cleanup()
        flashlight.stopped(message)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun cleanup() {
        active = false
        expectedTorchOn = false
        phaseOn = false
        releaseRhythmWakeLock()
        fadingOut = false
        cancelTransition()
        confirmedOn = false
        lastStrength = 0
        handler.removeCallbacksAndMessages(null)
        releaseWakeLock()
        if (callbackRegistered) {
            try { flashlight.cameraManager.unregisterTorchCallback(torchCallback) } catch (error: Exception) { android.util.Log.w("LittleLight", "Torch callback cleanup failed", error) }
            callbackRegistered = false
        }
        if (ownsTorch) {
            ownsTorch = false
            try {
                flashlight.cameraId?.let { flashlight.cameraManager.setTorchMode(it, false) }
            } catch (_: Exception) {
                // Android also releases the torch when this process loses its camera connection.
            }
        }
        deadline = 0L
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    override fun onDestroy() {
        cleanup()
        flashlight.stopped()
        notifications.show()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.zakaria5729.flash_table_lamp.START"
        const val ACTION_STOP = "com.zakaria5729.flash_table_lamp.STOP"
        const val ACTION_STRENGTH = "com.zakaria5729.flash_table_lamp.STRENGTH"
        const val ACTION_TIMER = "com.zakaria5729.flash_table_lamp.TIMER"
        const val ACTION_DIM = "com.zakaria5729.flash_table_lamp.DIM"
        const val ACTION_DIM_CONFIG = "com.zakaria5729.flash_table_lamp.DIM_CONFIG"
        const val ACTION_RHYTHM = "com.zakaria5729.flash_table_lamp.RHYTHM"
    }
}

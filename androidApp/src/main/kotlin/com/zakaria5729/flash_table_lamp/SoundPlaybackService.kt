package com.zakaria5729.flash_table_lamp

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.*
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.*
import androidx.core.content.ContextCompat
import androidx.core.net.toUri

class SoundPlaybackService : Service() {
    private val sounds get() = (application as LumaApplication).sounds
    private val handler = Handler(Looper.getMainLooper())
    private val audio by lazy { getSystemService(AudioManager::class.java) }
    private val notifications by lazy { getSystemService(NotificationManager::class.java) }
    private var player: MediaPlayer? = null
    private var mediaSession: MediaSession? = null
    private var focusRequest: AudioFocusRequest? = null
    private var receiverRegistered = false
    private var paused = false
    private var active = false
    private var lastCheckpoint = 0L
    private var duckFactor = 1f
    private var lastAppliedVolume = -1
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> pausePlayback()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> { duckFactor = .2f; setVolume() }
            AudioManager.AUDIOFOCUS_GAIN -> { duckFactor = 1f; setVolume() }
        }
    }
    private val noisy = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) { pausePlayback() }
    }
    private val sleepStop = Runnable { stopPlayback("Sound sleep timer finished.") }
    private val progress = object : Runnable {
        override fun run() {
            if (!active) return
            try {
            sounds.updateRemaining()
            setVolume()
            val state = sounds.state.value
            if (state.fadeGradually && state.fadeMode != DimmingMode.Auto && sounds.deadline > 0 && sounds.fadeStartVolume > 0 && state.currentVolume == 0) {
                stopPlayback("Sound fade reached silence."); return
            }
            val now = SystemClock.elapsedRealtime()
            if (now - lastCheckpoint >= 5000) { sounds.saveSession(player?.currentPosition ?: 0); lastCheckpoint = now }
            handler.postDelayed(this, nextProgressDelay(now))
            } catch (error: Exception) {
                android.util.Log.e("LittleLight", "Sound playback failed", error)
                stopPlayback("Sound playback was interrupted. Please try again.")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= 26) notifications.createNotificationChannel(NotificationChannel(CHANNEL, "Relaxing sounds", NotificationManager.IMPORTANCE_LOW))
        mediaSession = MediaSession(this, "TableLampSounds").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPause() = pausePlayback()
                override fun onStop() = stopPlayback()
                override fun onPlay() { sounds.play() }
                override fun onSkipToNext() {
                    val state = sounds.state.value
                    val available = state.sounds.filter { it.imported || it.downloaded }
                    if (available.isNotEmpty()) {
                        val index = available.indexOfFirst { it.id == state.selectedId }
                        sounds.selectSound(available[(index + 1).mod(available.size)].id)
                    }
                }
            }, handler)
            isActive = true
        }
        ContextCompat.registerReceiver(this, noisy, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), ContextCompat.RECEIVER_NOT_EXPORTED)
        receiverRegistered = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            PAUSE -> { pausePlayback(); return START_NOT_STICKY }
            STOP -> { stopPlayback(); return START_NOT_STICKY }
            VOLUME -> {
                sounds.restartFade(); setVolume()
                if (active) { sounds.saveSession(player?.currentPosition ?: 0); handler.removeCallbacks(progress); handler.post(progress) }
                return if (active) START_STICKY else START_NOT_STICKY
            }
            TIMER -> { restartTimer(); return if (active) START_STICKY else START_NOT_STICKY }
        }
        if (active) return START_STICKY
        val saved = sounds.loadSession()
        if (intent == null && saved == null) { stopSelf(); return START_NOT_STICKY }
        if (saved != null && saved.deadline > 0 && saved.deadline <= SystemClock.elapsedRealtime() && intent == null) {
            stopPlayback("Sound sleep timer finished."); return START_NOT_STICKY
        }
        if (!sounds.resumeLinkedPlaybackAllowed()) { stopSelf(); return START_NOT_STICKY }
        val selected = sounds.state.value.selected ?: run { stopSelf(); return START_NOT_STICKY }
        if (!sounds.validateSelectedSound()) { stopSelf(); return START_NOT_STICKY }
        try {
            sounds.preparing()
            if (Build.VERSION.SDK_INT >= 29) startForeground(ID, notification(true), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            else startForeground(ID, notification(true))
            if (!requestFocus()) { stopPlayback("Another app is using audio. Try again when it finishes."); return START_NOT_STICKY }
            sounds.deadline = saved?.deadline ?: sounds.deadline.takeIf { it > SystemClock.elapsedRealtime() } ?: 0
            if (saved != null) { sounds.fadeStartedAt = saved.fadeStartedAt; sounds.fadeStartVolume = saved.fadeStartVolume }
            val position = saved?.position ?: sounds.resumePosition
            val restored = saved != null || sounds.deadline > 0
            player = MediaPlayer().apply {
                setAudioAttributes(attributes)
                setWakeMode(this@SoundPlaybackService, PowerManager.PARTIAL_WAKE_LOCK)
                setDataSource(this@SoundPlaybackService, selected.uri.toUri())
                isLooping = true
                setOnPreparedListener { prepared ->
                    if (player !== prepared) return@setOnPreparedListener
                    if (position > 0 && prepared.duration > 0) prepared.seekTo(position % prepared.duration)
                    if (!restored) restartTimer()
                    setVolume(); prepared.start(); active = true; sounds.playing()
                    mediaSession?.setMetadata(MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, selected.title)
                        .putString(MediaMetadata.METADATA_KEY_ARTIST, APP_NAME).build())
                    mediaSession?.setPlaybackState(PlaybackState.Builder().setActions(PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_STOP or PlaybackState.ACTION_SKIP_TO_NEXT)
                        .setState(PlaybackState.STATE_PLAYING, prepared.currentPosition.toLong(), 1f).build())
                    scheduleStop()
                    sounds.saveSession(prepared.currentPosition)
                    notifications.notify(ID, notification(true)); handler.post(progress)
                }
                setOnErrorListener { _, _, _ -> sounds.validateSelectedSound(); stopPlayback("Couldn't play this sound. The file may have moved or use an unsupported audio format."); true }
                prepareAsync()
            }
        } catch (_: Exception) { stopPlayback("Couldn't open this sound. Choose the audio file again in Files.") }
        return START_STICKY
    }

    private fun restartTimer() {
        val duration = sounds.state.value.timerSeconds
        sounds.deadline = if (duration > 0) SystemClock.elapsedRealtime() + duration * 1000L else 0
        sounds.restartFade()
        if (active) {
            setVolume(); scheduleStop(); sounds.saveSession(player?.currentPosition ?: 0); notifications.notify(ID, notification(true))
            handler.removeCallbacks(progress); handler.post(progress)
        }
    }
    private fun scheduleStop() {
        handler.removeCallbacks(sleepStop)
        sounds.updateRemaining()
        if (sounds.deadline > 0) handler.postDelayed(sleepStop, (sounds.deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0))
    }
    private fun setVolume() {
        val volume = sounds.effectiveVolume()
        val applied = (volume * duckFactor * 100).toInt()
        if (lastAppliedVolume == applied) return
        val level = applied / 10000f
        try { player?.setVolume(level, level); lastAppliedVolume = applied; sounds.appliedVolume(volume) } catch (_: IllegalStateException) { }
    }
    private fun nextProgressDelay(now: Long): Long {
        val maximumDelay = if (sounds.isUiVisible) 1000L else 5000L
        val state = sounds.state.value
        if (!state.fadeGradually || sounds.deadline == 0L || sounds.effectiveVolume(now) == 0) return maximumDelay
        val step = if (state.fadeMode == DimmingMode.Auto)
            (sounds.deadline - sounds.fadeStartedAt) / sounds.fadeStartVolume.coerceAtLeast(1)
        else state.fadeMode.intervalMillis / state.fadePercent
        return step.coerceIn(200, maximumDelay)
    }
    private fun pausePlayback() {
        paused = true
        active = false
        val position = try { player?.currentPosition ?: 0 } catch (_: IllegalStateException) { 0 }
        sounds.paused(position)
        releasePlayer()
        stopForeground(STOP_FOREGROUND_REMOVE)
        notifications.notify(ID, notification(false))
        stopSelf()
    }
    private fun stopPlayback(message: String? = null) {
        active = false; paused = false
        sounds.stopped(message)
        releasePlayer()
        stopForeground(STOP_FOREGROUND_REMOVE)
        notifications.cancel(ID)
        stopSelf()
    }
    private fun releasePlayer() {
        handler.removeCallbacksAndMessages(null)
        try { player?.release() } catch (error: Exception) { android.util.Log.w("LittleLight", "Sound cleanup failed", error) }; player = null
        lastAppliedVolume = -1
        try { abandonFocus() } catch (error: Exception) { android.util.Log.w("LittleLight", "Audio focus cleanup failed", error) }
    }
    @Suppress("DEPRECATION")
    private fun requestFocus(): Boolean = if (Build.VERSION.SDK_INT >= 26) {
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener(focusListener, handler).build()
        focusRequest = request
        audio.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    } else audio.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED

    @Suppress("DEPRECATION")
    private fun abandonFocus() {
        if (Build.VERSION.SDK_INT >= 26) focusRequest?.let(audio::abandonAudioFocusRequest)
        else audio.abandonAudioFocus(focusListener)
        focusRequest = null
    }
    @Suppress("DEPRECATION")
    private fun notification(playing: Boolean): Notification {
        fun action(name: String, request: Int) = if (Build.VERSION.SDK_INT >= 26 && name == PLAY) {
            PendingIntent.getForegroundService(this, request, Intent(this, SoundPlaybackService::class.java).setAction(name), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        } else PendingIntent.getService(this, request, Intent(this, SoundPlaybackService::class.java).setAction(name), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL) else Notification.Builder(this)
        builder.setSmallIcon(R.drawable.ic_sound).setContentTitle(sounds.state.value.selected?.title ?: "Relaxing sounds")
            .setContentText(if (playing) "$APP_NAME · Relaxing sounds" else "$APP_NAME · Paused")
            .setContentIntent(PendingIntent.getActivity(this, 200, Intent(this, MainActivity::class.java).setAction(SHOW_SOUNDS), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            .setOnlyAlertOnce(true).setOngoing(playing).setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(Notification.Action.Builder(null, if (playing) "Pause" else "Play", action(if (playing) PAUSE else PLAY, 201)).build())
            .addAction(Notification.Action.Builder(null, "Stop", action(STOP, 202)).build())
        if (playing) builder.setStyle(Notification.MediaStyle().setMediaSession(mediaSession?.sessionToken).setShowActionsInCompactView(0, 1))
        return builder.build()
    }
    override fun onDestroy() {
        if (active && sounds.state.value.isPlaying) {
            sounds.saveSession(try { player?.currentPosition ?: 0 } catch (_: Exception) { 0 })
            sounds.interrupted()
        }
        releasePlayer()
        mediaSession?.release(); mediaSession = null
        if (receiverRegistered) unregisterReceiver(noisy)
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
    companion object {
        const val PLAY = "com.zakaria5729.flash_table_lamp.SOUND_PLAY"
        const val PAUSE = "com.zakaria5729.flash_table_lamp.SOUND_PAUSE"
        const val STOP = "com.zakaria5729.flash_table_lamp.SOUND_STOP"
        const val VOLUME = "com.zakaria5729.flash_table_lamp.SOUND_VOLUME"
        const val TIMER = "com.zakaria5729.flash_table_lamp.SOUND_TIMER"
        const val SHOW_SOUNDS = "com.zakaria5729.flash_table_lamp.SOUNDS"
        const val NOTIFICATION_ID = 2
        private const val ID = NOTIFICATION_ID
        private const val CHANNEL = "ambient_sounds"
    }
}

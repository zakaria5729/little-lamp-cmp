package com.zakaria5729.flash_table_lamp

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt

class FlashlightNotification(private val context: Context) {
    private val flashlight get() = (context.applicationContext as LumaApplication).flashlight
    private val manager = context.getSystemService(NotificationManager::class.java)

    init {
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel(CHANNEL, "Flashlight controls", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Power, brightness, timer and gradual dimming controls"
            setShowBadge(false)
        })
    }

    fun show(deadline: Long = 0) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        try { manager.notify(ID, build(deadline)) }
        catch (error: Exception) {
            android.util.Log.e("LittleLight", "Notification unavailable", error)
            flashlight.showMessage("Couldn't show notification controls. Use the controls inside the app.")
        }
    }

    fun build(deadline: Long = 0): Notification = try {
        buildControls(deadline).also { notification ->
            val parent = android.widget.FrameLayout(context)
            notification.contentView?.apply(context, parent)
            notification.bigContentView?.apply(context, parent)
        }
    } catch (error: Exception) {
        android.util.Log.e("LittleLight", "Custom notification unavailable", error)
        flashlight.showMessage("Custom notification controls aren't supported on this phone. Basic controls are available.")
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(context, CHANNEL) else Notification.Builder(context)
        builder.setSmallIcon(R.drawable.ic_flashlight).setContentTitle(APP_NAME)
            .setContentText(if (flashlight.state.value.isOn) "Flashlight on" else "Flashlight off")
            .setContentIntent(PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), IMMUTABLE))
            .setOngoing(flashlight.state.value.isOn).setOnlyAlertOnce(true)
            .addAction(Notification.Action.Builder(null, if (flashlight.state.value.isOn) "Turn off" else "Turn on", power(flashlight.state.value.isOn)).build())
            .build()
    }

    private fun buildControls(deadline: Long): Notification {
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), IMMUTABLE)
        val input = RemoteInput.Builder(NotificationControlReceiver.DURATION)
            .setLabel("Timer: 15m, 30s, off; rhythm auto or rhythm 2/3")
            .setChoices(arrayOf("1m", "5m", "15m", "30m", "off"))
            .build()
        val customTimer = PendingIntent.getBroadcast(context, 100,
            Intent(context, NotificationControlReceiver::class.java).setAction(NotificationControlReceiver.CUSTOM_TIMER),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
        val state = flashlight.state.value
        val rateInput = RemoteInput.Builder(NotificationControlReceiver.RATE)
            .setLabel("Dim rate: 10%/s, 10%/m or 10%/h")
            .setChoices(arrayOf("5%/s", "10%/m", "20%/m", "10%/h"))
            .build()
        val customRate = PendingIntent.getBroadcast(context, 101,
            Intent(context, NotificationControlReceiver::class.java).setAction(NotificationControlReceiver.CUSTOM_RATE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(context, CHANNEL) else Notification.Builder(context)
        return builder
            .setSmallIcon(R.drawable.ic_flashlight)
            .setContentTitle(if (state.isOn && state.rhythmEnabled) "$APP_NAME · Rhythm running" else if (state.isOn) "$APP_NAME · Flashlight on" else "$APP_NAME · Ready to light up")
            .setContentText("${state.brightnessPercent}% brightness · ${durationLabel(state.timerSeconds)}")
            .setContentIntent(open)
            .setOngoing(state.isOn)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setShowWhen(false)
            .setStyle(Notification.DecoratedCustomViewStyle())
            .setCustomContentView(views(false, deadline))
            .setCustomBigContentView(views(true, deadline))
            .addAction(Notification.Action.Builder(null, "Custom timer", customTimer)
                .addRemoteInput(input).setAllowGeneratedReplies(false).build())
            .addAction(Notification.Action.Builder(null, "Dim rate", customRate)
                .addRemoteInput(rateInput).setAllowGeneratedReplies(false).build())
            .addAction(Notification.Action.Builder(null, "Screen lamp", PendingIntent.getActivity(context, 4,
                Intent(context, MainActivity::class.java).setAction("com.zakaria5729.flash_table_lamp.SCREEN"), IMMUTABLE)).build())
            .build()
    }

    private fun views(expanded: Boolean, deadline: Long): RemoteViews {
        val state = flashlight.state.value
        val views = RemoteViews(context.packageName,
            if (expanded) R.layout.notification_expanded else R.layout.notification_compact)
        views.setTextViewText(R.id.notification_brightness,
            if (state.rhythmEnabled && state.isOn) "${state.brightnessPercent}% · rhythm" else if (state.isFading) "${state.brightnessPercent}% start" else "${state.brightnessPercent}%")
        views.setOnClickPendingIntent(R.id.brightness_minus, control(NotificationControlReceiver.DIMMER))
        views.setOnClickPendingIntent(R.id.brightness_plus, control(NotificationControlReceiver.BRIGHTER))
        enable(views, R.id.brightness_minus, !state.isStopping && state.supportsDimming && state.strength > 1)
        enable(views, R.id.brightness_plus, !state.isStopping && state.supportsDimming && state.strength < state.maxStrength)
        views.setTextViewText(R.id.notification_power, if (state.isStopping) "Fading off" else if (state.isOn && state.rhythmEnabled) "Stop rhythm" else if (state.isOn) "Turn off" else "Turn on")
        enable(views, R.id.notification_power, !state.isStopping)
        views.setOnClickPendingIntent(R.id.notification_power, power(state.isOn))
        if (expanded) {
            views.setTextViewText(R.id.notification_status, when {
                state.rhythmEnabled && state.isOn -> "Rhythm · ${state.onSeconds}s on / ${state.offSeconds}s off"
                state.isFading -> if (state.dimmingMode == DimmingMode.Auto) "Auto fade · minimum at timer end"
                    else "Fading ${state.dimPercentPerMinute}% / ${state.dimmingMode.unit}"
                state.isOn -> "Light on · ${durationLabel(state.timerSeconds)}"
                else -> "Light off · ${durationLabel(state.timerSeconds)}"
            })
            views.setViewVisibility(R.id.notification_countdown, if (state.isOn && deadline > 0) View.VISIBLE else View.GONE)
            views.setChronometer(R.id.notification_countdown, deadline, "%s left", state.isOn && deadline > 0)
            views.setChronometerCountDown(R.id.notification_countdown, true)
            listOf(R.id.timer_off to 0, R.id.timer_1 to 60, R.id.timer_5 to 300,
                R.id.timer_15 to 900, R.id.timer_30 to 1800).forEach { (id, seconds) ->
                views.setOnClickPendingIntent(id, control(NotificationControlReceiver.TIMER, seconds))
                select(views, id, state.timerSeconds == seconds)
            }
            views.setTextViewText(R.id.notification_dim, when {
                !state.supportsDimming -> "Gradual dimming unavailable"
                state.timerSeconds == 0 -> "Choose a timer to dim gradually"
                else -> "Dim gradually: ${if (state.dimGradually) "ON" else "OFF"}"
            })
            views.setOnClickPendingIntent(R.id.notification_dim, control(NotificationControlReceiver.TOGGLE_DIM))
            select(views, R.id.notification_dim, state.dimGradually && state.timerSeconds > 0)
            enable(views, R.id.notification_dim, state.supportsDimming && state.timerSeconds > 0)
            views.setTextViewText(R.id.notification_rhythm, "Rhythm: ${if (state.rhythmEnabled) "ON" else "OFF"}")
            views.setOnClickPendingIntent(R.id.notification_rhythm, control(NotificationControlReceiver.TOGGLE_RHYTHM))
            select(views, R.id.notification_rhythm, state.rhythmEnabled)
            enable(views, R.id.notification_rhythm, !state.isStopping)
            views.setTextViewText(R.id.notification_rate,
                if (state.dimmingMode == DimmingMode.Auto) "Auto fade" else "${state.dimPercentPerMinute}% / ${state.dimmingMode.unit}")
            views.setTextViewText(R.id.notification_mode,
                if (state.dimmingMode == DimmingMode.Auto) "Auto ↔" else "/ ${state.dimmingMode.unit} ↔")
            views.setOnClickPendingIntent(R.id.notification_mode, control(NotificationControlReceiver.TOGGLE_MODE))
            views.setOnClickPendingIntent(R.id.dim_rate_minus, control(NotificationControlReceiver.RATE_DOWN))
            views.setOnClickPendingIntent(R.id.dim_rate_plus, control(NotificationControlReceiver.RATE_UP))
            val rateEnabled = state.supportsDimming && state.dimGradually && state.timerSeconds > 0
            enable(views, R.id.notification_mode, rateEnabled)
            enable(views, R.id.dim_rate_minus, rateEnabled && state.dimmingMode != DimmingMode.Auto && state.dimPercentPerMinute > 1)
            enable(views, R.id.dim_rate_plus, rateEnabled && state.dimmingMode != DimmingMode.Auto && state.dimPercentPerMinute < 100)
        }
        return views
    }

    private fun power(isOn: Boolean): PendingIntent {
        if (isOn) return PendingIntent.getService(context, 1,
            Intent(context, FlashlightService::class.java).setAction(FlashlightService.ACTION_STOP), IMMUTABLE)
        val intent = Intent(context, FlashlightService::class.java).setAction(FlashlightService.ACTION_START)
        return if (Build.VERSION.SDK_INT >= 26) PendingIntent.getForegroundService(context, 3, intent, IMMUTABLE)
            else PendingIntent.getService(context, 3, intent, IMMUTABLE)
    }

    private fun control(action: String, seconds: Int = -1) = PendingIntent.getBroadcast(context, seconds + 10,
        Intent(context, NotificationControlReceiver::class.java).setAction(action).putExtra("seconds", seconds), IMMUTABLE)

    private fun enable(views: RemoteViews, id: Int, enabled: Boolean) {
        views.setBoolean(id, "setEnabled", enabled)
        if (!enabled) views.setTextColor(id, "#929DAE".toColorInt())
    }

    private fun select(views: RemoteViews, id: Int, selected: Boolean) {
        views.setInt(id, "setBackgroundResource", if (selected) R.drawable.notification_button_selected else R.drawable.notification_button)
        views.setTextColor(id, (if (selected) "#0C1015" else "#F1F3F5").toColorInt())
    }

    companion object {
        const val ID = 1
        private const val CHANNEL = "flashlight"
        private const val IMMUTABLE = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    }
}

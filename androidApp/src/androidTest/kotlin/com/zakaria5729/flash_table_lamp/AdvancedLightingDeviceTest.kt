package com.zakaria5729.flash_table_lamp

import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Intent
import android.graphics.Color
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class AdvancedLightingDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val app get() = context.applicationContext as LumaApplication
    private val screen get() = app.screenLamp
    private val torch get() = app.flashlight
    private val sounds get() = app.sounds
    private val device = UiDevice.getInstance(instrumentation)
    private val hardwareOn = AtomicReference<Boolean?>(null)
    private val callback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId == torch.cameraId) hardwareOn.set(enabled)
        }
    }
    @Before fun prepare() {
        Configurator.getInstance().waitForIdleTimeout = 100
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, android.Manifest.permission.POST_NOTIFICATIONS)
        }
        device.wakeUp(); device.executeShellCommand("wm dismiss-keyguard")
        onMain { screen.stop(); torch.turnOff(false); sounds.stop() }
        await { !lightServiceRunning() && !soundServiceRunning() }
        device.executeShellCommand("cmd statusbar collapse")
        launch()
        assertTrue(device.wait(Until.hasObject(By.desc("Open navigation menu")), 7000))
        onMain { screen.setCriticalStop(false); screen.setSmoothTransitions(false); screen.selectPage(LampPage.Screen) }
        torch.cameraManager.registerTorchCallback(callback, Handler(Looper.getMainLooper()))
        await { hardwareOn.get() == false }
    }
    @After fun cleanup() {
        onMain { screen.stop(); torch.turnOff(false); sounds.stop(); screen.deletePreset("Advanced device test") }
        await { !lightServiceRunning() && !soundServiceRunning() }
        torch.cameraManager.unregisterTorchCallback(callback)
        Configurator.getInstance().waitForIdleTimeout = 10_000
    }
    @Test fun splitZonesRenderIndependentColorsAndLayouts() {
        onMain {
            screen.setZonesEnabled(true); screen.setZoneCount(2); screen.setZoneLayout(ZoneLayout.Stacked)
            screen.setZoneColor(0, 0xFFFF0000.toInt()); screen.setZoneColor(1, 0xFF0000FF.toInt())
            screen.setZoneBrightness(0, 100); screen.setZoneBrightness(1, 50); screen.setTimer(0); screen.start()
        }
        click("Hide controls")
        assertTrue(device.wait(Until.gone(By.text("Screen off")), 2000))
        SystemClock.sleep(250)
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        val top = bitmap.getPixel(bitmap.width / 2, bitmap.height / 4)
        val bottom = bitmap.getPixel(bitmap.width / 2, bitmap.height * 3 / 4)
        assertTrue("Top zone should be red: $top", Color.red(top) > 245 && Color.blue(top) < 5)
        assertTrue("Bottom zone should be half blue: $bottom", Color.blue(bottom) in 120..135 && Color.red(bottom) < 5)
        bitmap.recycle()
        onMain { screen.setZoneCount(3); screen.setZoneLayout(ZoneLayout.SideBySide); screen.setZoneColor(2, 0xFF00FF00.toInt()) }
        SystemClock.sleep(250)
        val side = instrumentation.uiAutomation.takeScreenshot()
        assertTrue(Color.red(side.getPixel(side.width / 6, side.height / 2)) > 245)
        assertTrue(Color.green(side.getPixel(side.width * 5 / 6, side.height / 2)) > 245)
        side.recycle()
        onMain { screen.setZoneCount(4); screen.setZoneColor(3, 0xFFFFFF00.toInt()); screen.setCycleInterval(1) }
        assertEquals(1, screen.state.value.cycleInterval)
        assertEquals(ZoneLayout.Grid, screen.state.value.zoneLayout)
        SystemClock.sleep(250)
        val grid = instrumentation.uiAutomation.takeScreenshot()
        assertTrue(Color.red(grid.getPixel(grid.width / 4, grid.height / 4)) > 245)
        assertTrue(Color.blue(grid.getPixel(grid.width * 3 / 4, grid.height / 4)) in 120..135)
        assertTrue(Color.green(grid.getPixel(grid.width / 4, grid.height * 3 / 4)) > 245)
        val fourth = grid.getPixel(grid.width * 3 / 4, grid.height * 3 / 4)
        assertTrue(Color.red(fourth) > 245 && Color.green(fourth) > 245)
        grid.recycle()
    }
    @Test fun automaticColorChangesFadeAtOneSecondIntervals() {
        onMain {
            screen.setTimer(0); screen.setGradient(false); screen.setZonesEnabled(false)
            screen.setSmoothTransitions(false); screen.setCycleInterval(1); screen.setCycleColors(true); screen.start()
        }
        assertTrue(device.wait(Until.hasObject(By.desc("Full-screen colored lamp")), 3000))
        val previousIndex = screen.state.value.cycleIndex
        val previous = screenLightPattern(screen.state.value).colors.first()
        await(2000) { screen.state.value.cycleIndex != previousIndex }
        val target = screenLightPattern(screen.state.value).colors.first()
        SystemClock.sleep(250)
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        val intermediate = bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
        bitmap.recycle()
        assertNotEquals("Color must fade through an intermediate shade", previous, intermediate)
        assertNotEquals("Color must not jump straight to the next shade", target, intermediate)
    }
    @Test fun eightZoneSpotlightReachesEveryZoneAndRandomColorsRenderIndependently() {
        onMain {
            screen.setZonesEnabled(true); screen.setZoneCount(8); screen.setTimer(0)
            repeat(8) { screen.setZoneColor(it, 0xFFFFFFFF.toInt()); screen.setZoneBrightness(it, 100) }
            screen.setZoneAnimation(ZoneAnimation.Sequential); screen.setCycleInterval(1); screen.setCycleColors(true); screen.start()
        }
        click("Hide controls")
        await(10_000) { screen.state.value.cycleIndex >= 7 }
        onMain { screen.setCycleInterval(60) }
        SystemClock.sleep(900)
        var bitmap = instrumentation.uiAutomation.takeScreenshot()
        val active = screen.state.value.cycleIndex % 8
        repeat(8) { index ->
            val color = bitmap.getPixel(bitmap.width * (index % 2 * 2 + 1) / 4, bitmap.height * (index / 2 * 2 + 1) / 8)
            val brightest = maxOf(Color.red(color), Color.green(color), Color.blue(color))
            if (index == active) assertTrue("Active zone $index", brightest > 100)
            else assertTrue("Dimmed zone $index", brightest <= 53)
        }
        bitmap.recycle()
        for (count in listOf(6, 8)) {
            onMain { screen.setZoneCount(count); screen.setZoneAnimation(ZoneAnimation.Random) }
            SystemClock.sleep(900)
            val expected = screenLightPattern(screen.state.value).colors
            bitmap = instrumentation.uiAutomation.takeScreenshot()
            val rows = count / 2
            repeat(count) { index ->
                val actual = bitmap.getPixel(bitmap.width * (index % 2 * 2 + 1) / 4, bitmap.height * (index / 2 * 2 + 1) / (rows * 2))
                assertEquals("Random zone $index of $count", expected[index], actual)
            }
            bitmap.recycle()
        }
        val preset = screen.state.value.toLightingPreset("Eight random zones")
        assertEquals(preset, preset.toJson().toLightingPreset())
        val legacy = org.json.JSONObject("{\"name\":\"Old four zones\",\"zone_count\":4}").toLightingPreset()
        assertEquals(4, legacy.zoneCount); assertEquals(8, legacy.zones.size)
    }
    @Test fun gradientsAndCycleApplyToRealScreenWithoutChangingManualColors() {
        onMain {
            screen.setGradient(true); screen.setGradientCount(4); screen.setGradientAngle(90)
            listOf(0xFFFF0000, 0xFF00FF00, 0xFF0000FF, 0xFFFFFF00).forEachIndexed { i, color -> screen.setGradientColor(i, color.toInt()) }
            screen.setTimer(0); screen.start()
        }
        click("Hide controls")
        SystemClock.sleep(350)
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        val nearTop = bitmap.getPixel(bitmap.width / 2, bitmap.height / 20)
        val nearBottom = bitmap.getPixel(bitmap.width / 2, bitmap.height * 19 / 20)
        assertTrue(Color.red(nearTop) > Color.green(nearTop))
        assertTrue("Bottom of gradient: $nearBottom", Color.red(nearBottom) > 150 && Color.green(nearBottom) > 150)
        bitmap.recycle()
        val original = screen.state.value.color
        onMain { screen.setCycleInterval(2); screen.setCycleColors(true) }
        await(3500) { screen.state.value.cycleIndex > 0 }
        assertEquals(original, screen.state.value.color)
        onMain { screen.showControls() }
        val phase = screen.state.value.cycleIndex
        SystemClock.sleep(2300)
        assertEquals("Color cycle must pause outside the full-screen lamp", phase, screen.state.value.cycleIndex)
    }
    @Test fun smartFadesRampScreenAndLedAndReleaseServices() {
        onMain { screen.setSmoothTransitions(true); screen.setTransitionMillis(600); screen.setBrightness(60); screen.setTimer(0); screen.start() }
        await { screen.state.value.currentBrightness in 1..59 }
        await { screen.state.value.currentBrightness == 60 }
        onMain { screen.stopSmoothly() }
        assertTrue(screen.state.value.isStopping)
        await { screen.state.value.currentBrightness in 1..59 }
        await { !screen.state.value.isOn }
        onMain { torch.setTimer(0); torch.setStrength(20); torch.turnOn() }
        await { torch.state.value.isOn && torch.state.value.isStarting && torch.state.value.currentStrength in 1..19 }
        await { !torch.state.value.isStarting && torch.state.value.currentStrength == 20 }
        assertEquals(20, torch.cameraManager.getTorchStrengthLevel(torch.cameraId!!))
        onMain { torch.turnOff() }
        await { torch.state.value.isStopping }
        await { torch.state.value.currentStrength in 1..19 }
        await { !torch.state.value.isOn && hardwareOn.get() == false && !lightServiceRunning() }
    }
    @Test fun idleRelaunchResetsLightingAndSoundsButKeepsUserLibraryAndAppearance() {
        onMain {
            screen.setTheme(ThemeMode.Light); screen.setBrightness(12); screen.setGradient(true); screen.setGradientCount(4)
            screen.setZoneCount(3); screen.setZonesEnabled(true); screen.setCycleColors(true); screen.setDual(true)
            screen.setTimer(123); screen.setDim(true); screen.setMode(DimmingMode.PerHour); screen.setRate(7)
            screen.savePreset("Advanced device test")
            sounds.selectSound("forest"); sounds.setVolume(77); sounds.setTimer(300)
            torch.setStrength(5); torch.setTimer(22); torch.setDimGradually(true); torch.setRhythm(true)
        }
        launch()
        await { screen.state.value.page == LampPage.Flashlight && !screen.state.value.zonesEnabled && !torch.state.value.rhythmEnabled }
        val current = screen.state.value
        assertEquals(50, current.brightness); assertEquals(900, current.timerSeconds)
        assertFalse(current.dim); assertFalse(current.gradient); assertFalse(current.cycleColors); assertFalse(current.dual)
        assertEquals(ThemeMode.Light, current.theme)
        assertTrue(current.presets.any { it.name == "Advanced device test" })
        assertEquals("rain", sounds.state.value.selectedId); assertEquals(30, sounds.state.value.volume); assertEquals(0, sounds.state.value.timerSeconds)
        assertEquals(900, torch.state.value.timerSeconds); assertFalse(torch.state.value.dimGradually)
        assertFalse(lightServiceRunning()); assertFalse(soundServiceRunning())
    }
    @Test fun activeSessionsSurviveRelaunchWithTheirOriginalDeadlines() {
        onMain { torch.setStrength(5); torch.setTimer(15); torch.setDimGradually(true); torch.turnOn(); screen.setZonesEnabled(true) }
        await { torch.state.value.isOn && hardwareOn.get() == true }
        val deadline = FlashlightSessionStore(context).load()!!.deadline
        device.pressHome(); launch()
        assertTrue(torch.state.value.isOn); assertTrue(screen.state.value.zonesEnabled)
        assertEquals(deadline, FlashlightSessionStore(context).load()!!.deadline)
        onMain { torch.turnOff(false); sounds.selectSound("ocean"); sounds.setVolume(12); sounds.setTimer(15); sounds.play() }
        await { sounds.state.value.isPlaying }
        val soundDeadline = sounds.deadline
        device.pressHome(); launch()
        assertTrue(sounds.state.value.isPlaying); assertEquals("ocean", sounds.state.value.selectedId)
        assertEquals(12, sounds.state.value.volume); assertEquals(soundDeadline, sounds.deadline)
    }
    @Test fun bundledSoundsPlayAndMediaNotificationActionsStopTheirService() {
        onMain { sounds.setVolume(8); sounds.play() }
        await { sounds.state.value.isPlaying && soundServiceRunning() }
        await { context.getSystemService(AudioManager::class.java).isMusicActive }
        for (id in listOf("ocean", "forest", "fireplace", "rain")) {
            onMain { sounds.selectSound(id) }
            await { sounds.state.value.selectedId == id && sounds.state.value.isPlaying }
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        val pause = manager.activeNotifications.first { it.id == SoundPlaybackService.NOTIFICATION_ID }.notification.actions.first { it.title == "Pause" }
        pause.actionIntent.send()
        await { !sounds.state.value.isPlaying && !soundServiceRunning() }
        val play = manager.activeNotifications.first { it.id == SoundPlaybackService.NOTIFICATION_ID }.notification.actions.first { it.title == "Play" }
        play.actionIntent.send()
        await { sounds.state.value.isPlaying && soundServiceRunning() }
        onMain { sounds.setTimer(2) }
        await(5000) { !sounds.state.value.isPlaying && !soundServiceRunning() }
        await { manager.activeNotifications.none { it.id == SoundPlaybackService.NOTIFICATION_ID } }
    }
    @Test fun soundKeepsLoopingAfterLeavingAppAndPauseReleasesForegroundService() {
        onMain { sounds.setVolume(8); sounds.play() }
        await { sounds.state.value.isPlaying }
        device.pressHome(); SystemClock.sleep(9000)
        assertTrue(sounds.state.value.isPlaying)
        assertTrue(context.getSystemService(AudioManager::class.java).isMusicActive)
        onMain { sounds.pause() }
        await { !sounds.state.value.isPlaying && !soundServiceRunning() }
        assertTrue("An eight-second loop should wrap its playback position", sounds.resumePosition in 1..7999)
    }
    @Test fun soundFadesWithItsTimerWithoutChangingDeviceVolume() {
        val audio = context.getSystemService(AudioManager::class.java)
        val streamVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        onMain {
            sounds.setVolume(60); sounds.setTimer(6); sounds.setFadeMode(DimmingMode.Auto)
            sounds.setFadeGradually(true); sounds.play()
        }
        await { sounds.state.value.isPlaying }
        await(4000) { sounds.state.value.currentVolume in 10..49 }
        assertEquals(60, sounds.state.value.volume)
        val session = sounds.loadSession()!!
        assertTrue(session.fadeStartedAt > 0 && session.fadeStartVolume == 60)
        device.pressHome(); launch()
        assertTrue(sounds.state.value.fadeGradually)
        assertEquals(session.fadeStartedAt, sounds.fadeStartedAt)
        await(7000) { !sounds.state.value.isPlaying && !soundServiceRunning() }
        assertEquals(streamVolume, audio.getStreamVolume(AudioManager.STREAM_MUSIC))
        onMain {
            sounds.setVolume(20); sounds.setTimer(30); sounds.setFadeMode(DimmingMode.PerSecond)
            sounds.setFadeRate(10); sounds.play()
        }
        await { sounds.state.value.isPlaying }
        await(5000) { !sounds.state.value.isPlaying && !soundServiceRunning() }
        assertEquals("Sound fade reached silence.", sounds.state.value.message)
    }




    @Test fun rhythmRunsOneSecondPhasesAcrossRelaunchAndStopsAtTimerDeadline() {
        onMain { torch.setTimer(7); torch.setStrength(5); torch.setRhythm(true); torch.turnOn() }
        await { hardwareOn.get() == true && torch.state.value.isOn }
        val anchor = FlashlightSessionStore(context).load()!!.rhythmStartedAt
        val first = SystemClock.elapsedRealtime()
        await(3500) { hardwareOn.get() == false && !torch.state.value.torchLit }
        assertTrue(SystemClock.elapsedRealtime() - first >= 600)
        assertTrue(torch.state.value.isOn); assertTrue(lightServiceRunning())
        launch()
        assertTrue(torch.state.value.rhythmEnabled)
        assertEquals(anchor, FlashlightSessionStore(context).load()!!.rhythmStartedAt)
        await(3500) { hardwareOn.get() == true }
        await(5000) { !torch.state.value.isOn && hardwareOn.get() == false && !lightServiceRunning() }
    }
    @Test fun customRhythmClampsMinimumAndUsesItsOwnOffDuration() {
        onMain { torch.setRhythmMode(RhythmMode.Custom); torch.setRhythmTimes(0, 1) }
        assertEquals(1, torch.state.value.onSeconds); assertEquals(1, torch.state.value.offSeconds)
        onMain { torch.setRhythmTimes(2, 3); torch.setRhythm(true); torch.setTimer(9); torch.setStrength(5); torch.turnOn() }
        await { hardwareOn.get() == true }
        await(3500) { hardwareOn.get() == false }
        val off = SystemClock.elapsedRealtime()
        await(4500) { hardwareOn.get() == true }
        assertTrue(SystemClock.elapsedRealtime() - off >= 2600)
        onMain { torch.turnOff(false) }
        await { !torch.state.value.isOn && !lightServiceRunning() }
    }
    @Test fun advancedPresetsRoundTripAndOlderPresetsRemainCompatible() {
        val configured = ScreenLampState(zonesEnabled = true, zoneCount = 3, zoneLayout = ZoneLayout.SideBySide,
            gradientCount = 4, gradientAngle = 270, cycleColors = true, cyclePalette = CyclePalette.Warm, cycleInterval = 7)
        val preset = configured.toLightingPreset("Three lights")
        assertEquals(preset, preset.toJson().toLightingPreset())
        val legacy = org.json.JSONObject("{\"name\":\"Old preset\",\"color\":-1,\"brightness\":30,\"timer\":300}").toLightingPreset()
        assertEquals("Old preset", legacy.name); assertFalse(legacy.zonesEnabled); assertEquals(2, legacy.gradientCount)
    }
    private fun launch() {
        context.startActivity(Intent(context, MainActivity::class.java).setAction(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
    }
    private fun click(text: String) { device.wait(Until.findObject(By.text(text)), 3000).click() }
    private fun onMain(block: () -> Unit) = instrumentation.runOnMainSync(block)
    @Suppress("DEPRECATION") private fun serviceRunning(name: String) = context.getSystemService(ActivityManager::class.java)
        .getRunningServices(Int.MAX_VALUE).any { it.service.className == name }
    private fun lightServiceRunning() = serviceRunning(FlashlightService::class.java.name)
    private fun soundServiceRunning() = serviceRunning(SoundPlaybackService::class.java.name)
    private fun await(timeout: Long = 5000, condition: () -> Boolean) {
        val end = SystemClock.elapsedRealtime() + timeout
        while (SystemClock.elapsedRealtime() < end) { if (condition()) return; SystemClock.sleep(30) }
        assertTrue("Condition failed. LED=${torch.state.value}; sound=${sounds.state.value}; screen=${screen.state.value}", condition())
    }
}

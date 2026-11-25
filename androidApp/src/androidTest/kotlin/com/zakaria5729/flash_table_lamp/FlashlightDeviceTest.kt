package com.zakaria5729.flash_table_lamp

import android.content.Intent
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.FixMethodOrder
import org.junit.runners.MethodSorters
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class FlashlightDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val flashlight get() = (context.applicationContext as LumaApplication).flashlight
    private val screenLamp get() = (context.applicationContext as LumaApplication).screenLamp
    private val device = UiDevice.getInstance(instrumentation)
    private val hardwareOn = AtomicReference<Boolean?>(null)
    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId == flashlight.cameraId) hardwareOn.set(enabled)
        }
    }

    @Before
    fun prepare() {
        androidx.test.uiautomator.Configurator.getInstance().waitForIdleTimeout = 100
        assertEquals(android.content.pm.PackageManager.PERMISSION_DENIED,
            context.checkSelfPermission(android.Manifest.permission.CAMERA))
        shell("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS")
        device.wakeUp()
        shell("wm dismiss-keyguard")
        shell("cmd statusbar collapse")
        onMain {
            screenLamp.stop()
            screenLamp.setCriticalStop(false)
            screenLamp.selectPage(LampPage.Flashlight)
            flashlight.turnOff()
            flashlight.setTimer(900)
            flashlight.setDimGradually(false)
            flashlight.setDimmingMode(DimmingMode.Auto)
            flashlight.setDimmingRate(10)
            flashlight.setStrength(1)
            flashlight.dismissMessage()
        }
        flashlight.cameraManager.registerTorchCallback(torchCallback, Handler(Looper.getMainLooper()))
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        if (!device.wait(Until.hasObject(By.desc("Turn flashlight on")), 1500)) {
            shell("wm dismiss-keyguard")
        }
        assertTrue(device.wait(Until.hasObject(By.desc("Turn flashlight on")), 10_000))
        onMain { screenLamp.setSmoothTransitions(false); flashlight.setStrength(1) }
        device.waitForIdle()
        await { hardwareOn.get() == false }
    }

    @After
    fun cleanup() {
        onMain { screenLamp.stop(); flashlight.turnOff(smooth = false); (context.applicationContext as LumaApplication).sounds.stop() }
        await { !flashlight.state.value.isOn }
        await { !serviceRunning() }
        flashlight.cameraManager.unregisterTorchCallback(torchCallback)
        device.wakeUp()
        device.waitForIdle()
        shell("wm dismiss-keyguard")
        shell("cmd statusbar collapse")
        androidx.test.uiautomator.Configurator.getInstance().waitForIdleTimeout = 10_000
    }

    @Test
    fun powerAndBrightnessButtonsControlActualTorch() {
        device.findObject(By.desc("No auto-off timer")).click()
        device.findObject(By.desc("Turn flashlight on")).click()
        await { flashlight.state.value.isOn && hardwareOn.get() == true }
        assertTrue("Pixel should support brightness adjustment", flashlight.state.value.supportsDimming)
        assertEquals(1, flashlight.cameraManager.getTorchStrengthLevel(flashlight.cameraId!!))

        device.findObject(By.desc("Increase brightness")).click()
        await { flashlight.cameraManager.getTorchStrengthLevel(flashlight.cameraId!!) == 2 }
        assertEquals(2, flashlight.state.value.strength)

        device.findObject(By.desc("Decrease brightness")).click()
        await { flashlight.cameraManager.getTorchStrengthLevel(flashlight.cameraId!!) == 1 }

        onMain { flashlight.setStrength(flashlight.state.value.maxStrength) }
        await { flashlight.cameraManager.getTorchStrengthLevel(flashlight.cameraId!!) == flashlight.state.value.maxStrength }
        onMain { flashlight.setStrength(1) }
        await { flashlight.cameraManager.getTorchStrengthLevel(flashlight.cameraId!!) == 1 }

        device.findObject(By.desc("Turn flashlight off")).click()
        await { !flashlight.state.value.isOn && hardwareOn.get() == false }
    }



    @Test
    fun zCustomTimerTurnsTorchOffWithScreenLocked() {
        onMain {
            flashlight.setStrength(21.coerceAtMost(flashlight.state.value.maxStrength))
            flashlight.setDimGradually(true)
        }
        device.findObject(By.text("Custom ↗")).click()
        assertTrue(device.wait(Until.hasObject(By.text("Set timer")), 3000))
        val fields = device.findObjects(By.clazz("android.widget.EditText"))
        assertEquals(2, fields.size)
        fields[0].text = "0"
        fields[1].text = "5"
        device.findObject(By.text("Set timer")).click()
        await { flashlight.state.value.timerSeconds == 5 }

        device.findObject(By.desc("Turn flashlight on")).click()
        await { flashlight.state.value.isOn && hardwareOn.get() == true }
        device.sleep()
        assertFalse(device.isScreenOn)
        await(10_000) { !flashlight.state.value.isOn && hardwareOn.get() == false }
        assertEquals(0, flashlight.state.value.remainingSeconds)
        assertFalse(device.isScreenOn)
    }

    @Test
    fun timerCanBeCancelledAndRestartedInBackground() {
        onMain {
            flashlight.setTimer(3)
            flashlight.setStrength(21.coerceAtMost(flashlight.state.value.maxStrength))
            flashlight.setDimGradually(true)
        }
        device.findObject(By.desc("Turn flashlight on")).click()
        await { flashlight.state.value.isOn && hardwareOn.get() == true }
        onMain { flashlight.setTimer(0) }
        device.pressHome()
        SystemClock.sleep(3500)
        assertTrue(flashlight.state.value.isOn)
        assertEquals(true, hardwareOn.get())
        assertEquals(0, flashlight.state.value.remainingSeconds)
        assertEquals(flashlight.state.value.strength, flashlight.cameraManager.getTorchStrengthLevel(flashlight.cameraId!!))

        onMain { flashlight.setTimer(2) }
        await(6000) { !flashlight.state.value.isOn && hardwareOn.get() == false }
    }

    @Test
    fun notificationActionTurnsTorchOff() {
        device.findObject(By.desc("No auto-off timer")).click()
        device.findObject(By.desc("Turn flashlight on")).click()
        await { flashlight.state.value.isOn && hardwareOn.get() == true }
        openExpandedNotification()
        assertTrue(device.wait(Until.hasObject(By.text("Turn off")), 5000))
        device.findObject(By.text("Turn off")).click()
        await { !flashlight.state.value.isOn && hardwareOn.get() == false }
        device.pressBack()
    }

    @Test
    fun notificationControlsPowerBrightnessTimerAndFadeFromBackground() {
        onMain {
            flashlight.setStrength(10)
            flashlight.setTimer(900)
        }
        device.pressHome()
        openExpandedNotification()
        clickNotification(By.text("Turn on"))
        await { flashlight.state.value.isOn && hardwareOn.get() == true }
        clickNotification(By.desc("Increase brightness"))
        await { flashlight.cameraManager.getTorchStrengthLevel(flashlight.cameraId!!) == 11 }
        clickNotification(By.desc("Decrease brightness"))
        await { flashlight.cameraManager.getTorchStrengthLevel(flashlight.cameraId!!) == 10 }

        clickNotification(By.desc("Set timer to 5 min"))
        await { flashlight.state.value.timerSeconds == 300 }
        clickNotification(By.desc("Toggle gradual dimming"))
        await { flashlight.state.value.dimGradually }
        clickNotification(By.desc("Change dimming mode"))
        await { flashlight.state.value.dimmingMode == DimmingMode.PerSecond }
        clickNotification(By.desc("Change dimming mode"))
        await { flashlight.state.value.dimmingMode == DimmingMode.PerMinute }
        clickNotification(By.desc("Increase dim rate"))
        await { flashlight.state.value.dimPercentPerMinute == 11 }
        clickNotification(By.desc("Decrease dim rate"))
        await { flashlight.state.value.dimPercentPerMinute == 10 }
        clickNotification(By.text("Dim rate"))
        submitNotificationInput("20%")
        await { flashlight.state.value.dimPercentPerMinute == 20 }
        clickNotification(By.text("Custom timer"))
        submitNotificationInput("3s")
        await { flashlight.state.value.timerSeconds == 3 }
        await(7000) { !flashlight.state.value.isOn && hardwareOn.get() == false }

        assertTrue(device.wait(Until.hasObject(By.text("Turn on")), 3000))
        clickNotification(By.text("Turn on"))
        await { flashlight.state.value.isOn && hardwareOn.get() == true }
        clickNotification(By.text("Turn off"))
        await { !flashlight.state.value.isOn && hardwareOn.get() == false }
        device.pressBack()
    }

    @Test
    fun gradualDimmingSwitchFadesActualTorchBeforeStopping() {
        val start = 21.coerceAtMost(flashlight.state.value.maxStrength)
        onMain {
            flashlight.setStrength(start)
            flashlight.setTimer(10)
        }
        repeat(6) {
            instrumentation.uiAutomation.clearCache()
            if (!device.hasObject(By.desc("Dim gradually with timer"))) {
                device.findObject(By.desc("Flashlight controls")).scroll(androidx.test.uiautomator.Direction.DOWN, .35f)
            }
        }
        instrumentation.uiAutomation.clearCache()
        val dimSwitch = device.wait(Until.findObject(By.desc("Dim gradually with timer")), 3000)
        if (dimSwitch == null) {
            device.executeShellCommand("screencap -p /sdcard/table-lamp-scroll-check.png")
            val hierarchy = java.io.ByteArrayOutputStream()
            device.dumpWindowHierarchy(hierarchy)
            fail("Dimming control not visible after scroll: $hierarchy")
        }
        dimSwitch.click()
        await { flashlight.state.value.dimGradually }
        device.findObject(By.desc("Flashlight controls")).fling(androidx.test.uiautomator.Direction.UP)
        instrumentation.uiAutomation.clearCache()
        device.wait(Until.findObject(By.desc("Turn flashlight on")), 3000).click()
        await { flashlight.state.value.isOn && hardwareOn.get() == true }
        await { flashlight.cameraManager.getTorchStrengthLevel(flashlight.cameraId!!) < start }

        var previous = start
        var lowest = start
        val end = SystemClock.elapsedRealtime() + 12_000
        while (flashlight.state.value.isOn && hardwareOn.get() == true && SystemClock.elapsedRealtime() < end) {
            val level = flashlight.cameraManager.getTorchStrengthLevel(flashlight.cameraId!!)
            if (hardwareOn.get() == true && flashlight.state.value.isOn) {
                assertTrue("Brightness should decrease: $previous -> $level", level <= previous)
                previous = level
                lowest = minOf(lowest, level)
            }
            SystemClock.sleep(50)
        }
        await { !flashlight.state.value.isOn && hardwareOn.get() == false }
        assertTrue("Torch should fade close to minimum before stopping, observed $lowest", lowest <= 3)
    }

    @Test
    fun externalTorchOffStopsSession() {
        onMain { flashlight.setTimer(0) }
        device.findObject(By.desc("Turn flashlight on")).click()
        await { flashlight.state.value.isOn && hardwareOn.get() == true }
        SystemClock.sleep(300)
        flashlight.cameraManager.setTorchMode(flashlight.cameraId!!, false)
        await { !flashlight.state.value.isOn && hardwareOn.get() == false }
    }

    @Test
    fun manualRateDimsIndependentlyOfTimerLength() {
        onMain {
            flashlight.setStrength(20)
            flashlight.setTimer(5)
            flashlight.setDimmingMode(DimmingMode.PerMinute)
            flashlight.setDimmingRate(100)
            flashlight.setDimGradually(true)
        }
        device.findObject(By.desc("Turn flashlight on")).click()
        await { flashlight.state.value.isOn && hardwareOn.get() == true }
        await { flashlight.state.value.remainingSeconds <= 2 }
        val level = flashlight.cameraManager.getTorchStrengthLevel(flashlight.cameraId!!)
        assertTrue("Manual fade should follow 100% per minute, observed level $level", level in 16..19)
        await(6000) { !flashlight.state.value.isOn && hardwareOn.get() == false }
    }

    @Test
    fun removingTaskFromRecentsKeepsLightAndOriginalTimerRunning() {
        onMain {
            flashlight.setStrength(21)
            flashlight.setTimer(5)
            flashlight.setDimGradually(true)
        }
        device.findObject(By.desc("Turn flashlight on")).click()
        await { flashlight.state.value.isOn && hardwareOn.get() == true }
        val deadline = FlashlightSessionStore(context).load()!!.deadline
        onMain {
            val activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).first()
            activity.finishAndRemoveTask()
        }
        SystemClock.sleep(1200)
        assertTrue(flashlight.state.value.isOn)
        assertEquals(true, hardwareOn.get())
        assertEquals(deadline, FlashlightSessionStore(context).load()!!.deadline)
        assertTrue(flashlight.cameraManager.getTorchStrengthLevel(flashlight.cameraId!!) < 21)
        await(6000) { !flashlight.state.value.isOn && hardwareOn.get() == false }
        assertNull(FlashlightSessionStore(context).load())
    }

    private fun onMain(action: () -> Unit) = instrumentation.runOnMainSync(action)

    @Suppress("DEPRECATION")
    private fun serviceRunning(): Boolean = context.getSystemService(android.app.ActivityManager::class.java)
        .getRunningServices(Int.MAX_VALUE).any { it.service.className == FlashlightService::class.java.name }

    private fun clickNotification(selector: androidx.test.uiautomator.BySelector) {
        val end = SystemClock.elapsedRealtime() + 5000
        while (SystemClock.elapsedRealtime() < end) {
            try {
                val button = device.wait(Until.findObject(selector), 500) ?: continue
                button.click()
                return
            } catch (_: androidx.test.uiautomator.StaleObjectException) {
                SystemClock.sleep(50)
            }
        }
        fail("Notification control was not ready: $selector")
    }

    private fun submitNotificationInput(text: String) {
        val editor = device.wait(Until.findObject(By.res(java.util.regex.Pattern.compile(".*:id/remote_input_text"))), 3000)
            ?: device.findObject(By.clazz("android.widget.EditText"))
        if (editor == null) {
            val hierarchy = java.io.ByteArrayOutputStream()
            device.dumpWindowHierarchy(hierarchy)
            fail("Notification input missing: $hierarchy")
        }
        editor!!.text = text
        val send = device.wait(Until.findObject(By.res(java.util.regex.Pattern.compile(".*:id/remote_input_send"))), 3000)
            ?: device.wait(Until.findObject(By.descContains("Send")), 1000)
        if (send == null) {
            val hierarchy = java.io.ByteArrayOutputStream()
            device.dumpWindowHierarchy(hierarchy)
            fail("Notification send button missing: $hierarchy")
        }
        send!!.click()
    }

    private fun openExpandedNotification() {
        device.openNotification()
        assertTrue(device.wait(Until.hasObject(By.res(context.packageName, "notification_power")), 5000))
        if (!device.hasObject(By.res(context.packageName, "timer_5"))) {
            val row = device.findObject(By.res("com.android.systemui:id/expandableNotificationRow")
                .hasDescendant(By.res(context.packageName, "notification_power")))
            assertNotNull("Little Lamp notification should be visible", row)
            val expand = row.findObject(By.res("android:id/expand_button"))
                ?: row.findObject(By.descContains("Expand"))
            if (expand != null) expand.click() else row.swipe(androidx.test.uiautomator.Direction.DOWN, .8f)
        }
        assertTrue(device.wait(Until.hasObject(By.res(context.packageName, "timer_5")), 3000))
    }

    private fun shell(command: String) {
        instrumentation.uiAutomation.executeShellCommand(command).use { descriptor ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).readBytes()
        }
    }

    private fun await(timeout: Long = 5000, condition: () -> Boolean) {
        val end = SystemClock.elapsedRealtime() + timeout
        while (SystemClock.elapsedRealtime() < end) {
            if (condition()) return
            SystemClock.sleep(50)
        }
        assertTrue("Condition not reached within $timeout ms. State: ${flashlight.state.value}", condition())
    }
}

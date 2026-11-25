package com.zakaria5729.flash_table_lamp

import android.content.Intent
import android.os.SystemClock
import android.view.WindowManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScreenLampDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val app get() = context.applicationContext as LumaApplication
    private val screen get() = app.screenLamp
    private val device = UiDevice.getInstance(instrumentation)

    @Before
    fun prepare() {
        Configurator.getInstance().waitForIdleTimeout = 100
        assertEquals(android.content.pm.PackageManager.PERMISSION_DENIED, context.checkSelfPermission(android.Manifest.permission.CAMERA))
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, android.Manifest.permission.POST_NOTIFICATIONS)
        }
        device.wakeUp()
        device.executeShellCommand("wm dismiss-keyguard")
        onMain {
            app.flashlight.turnOff()
            screen.stop()
            screen.selectPage(LampPage.Screen)
            screen.setTheme(ThemeMode.Dark)
            screen.setBrightness(50)
            screen.setTimer(900)
            screen.setDim(false)
            screen.setMode(DimmingMode.Auto)
            screen.setGradient(false)
            screen.setUltraDim(false)
            screen.setDual(false)
            screen.setEmergencyLed(false)
            screen.setCriticalStop(false)
        }
        device.executeShellCommand("cmd statusbar collapse")
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        assertTrue(device.wait(Until.hasObject(By.desc("Open navigation menu")), 7000))
        onMain { screen.setSmoothTransitions(false); screen.selectPage(LampPage.Screen) }
    }

    @After
    fun cleanup() {
        onMain { screen.stop(); app.flashlight.turnOff(smooth = false); app.sounds.stop() }
        await { !screen.state.value.isOn && !app.flashlight.state.value.isOn }
        Configurator.getInstance().waitForIdleTimeout = 10_000
    }

    @Test
    fun screenTimerRestoresWindowBrightnessAndKeepAwakeFlag() {
        onMain { screen.setBrightness(25); screen.setTimer(3) }
        clickText("Turn screen lamp on")
        assertTrue(device.wait(Until.hasObject(By.desc("Full-screen colored lamp")), 3000))
        await { windowBrightness() == .25f }
        assertTrue(keepsScreenOn())
        assertFalse(app.flashlight.state.value.isOn)
        await(6000) { !screen.state.value.isOn }
        await { windowBrightness() == WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE && !keepsScreenOn() }
    }

    @Test
    fun touchLockProtectsControlsAndLongPressUnlocks() {
        clickText("Turn screen lamp on")
        val lock = device.wait(Until.findObject(By.text("Lock controls")), 3000)
        assertNotNull(lock)
        lock.click()
        await { screen.state.value.touchLocked }
        assertTrue(device.wait(Until.hasObject(By.desc("Hold to unlock lamp controls")), 2000))
        device.pressBack()
        assertTrue(screen.state.value.fullscreen)
        assertTrue(screen.state.value.touchLocked)
        device.wait(Until.findObject(By.desc("Hold to unlock lamp controls")), 2000).longClick()
        await { !screen.state.value.touchLocked }
        clickText("Screen off")
        await { !screen.state.value.isOn }
    }

    @Test
    fun navigationThemesColorsAndPresetRestoreWork() {
        navigate("Settings")
        assertTrue(device.wait(Until.hasObject(By.text("Already given")), 3000))
        device.wait(Until.findObject(By.desc("Use Light theme")), 3000).click()
        await { screen.state.value.theme == ThemeMode.Light }
        device.wait(Until.findObject(By.desc("Use System theme")), 3000).click()
        await { screen.state.value.theme == ThemeMode.System }
        navigate("Colors & gradients")
        device.wait(Until.findObject(By.desc("Color gradient")), 3000).click()
        await { screen.state.value.gradient }
        device.wait(Until.findObject(By.desc("Choose color 4")), 3000).click()
        await { screen.state.value.color == 0xFFFF7777.toInt() }
        navigate("Saved presets")
        clickText("Save current lighting preset")
        val editor = device.wait(Until.findObject(By.clazz("android.widget.EditText")), 2000)
        assertNotNull(editor)
        editor.text = "Device test glow"
        clickText("Save preset")
        await { screen.state.value.presets.any { it.name == "Device test glow" } }
        onMain { screen.setColor(0xFF0000FF.toInt(), false); screen.setGradient(false) }
        clickText("Use")
        await { screen.state.value.page == LampPage.Screen && screen.state.value.color == 0xFFFF7777.toInt() && screen.state.value.gradient }
        onMain { screen.deletePreset("Device test glow") }
    }

    @Test
    fun dualModeUsesIndependentScreenAndLedControls() {
        onMain {
            screen.setTimer(5)
            screen.setDual(true)
            app.flashlight.setStrength(5)
        }
        clickText("Turn screen lamp on")
        await { screen.state.value.isOn && app.flashlight.state.value.isOn }
        onMain { screen.stop() }
        assertFalse(screen.state.value.isOn)
        assertTrue(app.flashlight.state.value.isOn)
        await(7000) { !app.flashlight.state.value.isOn }
    }

    @Test
    fun readingAndEmergencyShortcutsApplyTheirConfigurations() {
        openMenu()
        clickText("Smart reading mode")
        await { screen.state.value.isOn && screen.state.value.brightness == 35 }
        assertEquals(1800, screen.state.value.timerSeconds)
        assertTrue(screen.state.value.dim)
        clickText("Screen off")
        await { !screen.state.value.isOn }
        openMenu()
        clickText("Emergency light")
        await { screen.state.value.isOn && screen.state.value.brightness == 100 }
        assertEquals(0xFFFFFFFF.toInt(), screen.state.value.color)
        assertFalse(screen.state.value.dim)
        assertFalse(app.flashlight.state.value.isOn)
    }

    private fun navigate(title: String) {
        openMenu()
        val item = device.wait(Until.findObject(By.desc("Navigate to $title")), 2000)
        assertNotNull(item)
        item.click()
        await { screen.state.value.page.title == title }
        assertTrue(device.wait(Until.gone(By.desc("Navigate to Screen lamp")), 3000))
        device.waitForIdle()
    }

    private fun openMenu() {
        device.wait(Until.findObject(By.desc("Open navigation menu")), 3000).click()
        assertTrue(device.wait(Until.hasObject(By.desc("Navigation menu open")), 3000))
    }

    private fun onMain(action: () -> Unit) = instrumentation.runOnMainSync(action)

    private fun clickText(text: String) {
        val button = device.wait(Until.findObject(By.text(text)), 3000)
        assertNotNull("Expected visible control: $text", button)
        button.click()
    }

    private fun windowBrightness(): Float {
        var brightness = -2f
        onMain { brightness = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
            .firstOrNull()?.window?.attributes?.screenBrightness ?: -2f }
        return brightness
    }

    private fun keepsScreenOn(): Boolean {
        var active = false
        onMain { active = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
            .firstOrNull()?.window?.attributes?.flags?.and(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0 }
        return active
    }

    private fun await(timeout: Long = 5000, condition: () -> Boolean) {
        val end = SystemClock.elapsedRealtime() + timeout
        while (SystemClock.elapsedRealtime() < end) {
            if (condition()) return
            SystemClock.sleep(50)
        }
        assertTrue("Condition failed. Screen: ${screen.state.value}", condition())
    }
}

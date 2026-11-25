package com.zakaria5729.flash_table_lamp

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LinkedSoundDeviceTest {
    @Test fun linkedLightsSharePlaybackAndSoundOnlyRemainsIndependent() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as LumaApplication
        val sound = app.sounds
        fun main(action: () -> Unit) = instrumentation.runOnMainSync(action)
        fun await(check: () -> Boolean) {
            val until = SystemClock.elapsedRealtime() + 6000
            while (!check() && SystemClock.elapsedRealtime() < until) SystemClock.sleep(40)
            assertTrue("Sound=${sound.state.value}", check())
        }
        main { app.stopAll() }
        await { !app.flashlight.state.value.isOn }
        try {
            main {
                app.screenLamp.setSmoothTransitions(false)
                app.screenLamp.setCriticalStop(false)
                sound.selectSound("rain"); sound.setVolume(5); sound.setTimer(0)
                sound.setPlayWithLight(false, true); sound.setPlayWithLight(true, true)
                app.screenLamp.setTimer(0); app.screenLamp.start()
                app.flashlight.setTimer(0); app.flashlight.setStrength(1); app.flashlight.turnOn()
            }
            await { sound.state.value.isPlaying && sound.state.value.linkedLights.size == 2 }
            main { sound.stop() }
            await { !sound.state.value.isPlaying }
            main { sound.playWithLight(false) }
            await { sound.state.value.isPlaying && sound.state.value.linkedLights == setOf("flashlight") }
            main { sound.playWithLight(true) }
            assertEquals(setOf("screen", "flashlight"), sound.state.value.linkedLights)
            main { app.screenLamp.stop() }
            assertTrue(sound.state.value.isPlaying)
            assertEquals(setOf("flashlight"), sound.state.value.linkedLights)
            main { app.flashlight.turnOff(false) }
            await { !app.flashlight.state.value.isOn && !sound.state.value.isPlaying && !sound.state.value.isPreparing }
            main { sound.play() }
            await { sound.state.value.isPlaying }
            main { app.flashlight.turnOn() }
            await { app.flashlight.state.value.isOn }
            main { app.flashlight.turnOff(false) }
            await { !app.flashlight.state.value.isOn }
            assertTrue("Sound-only playback must continue", sound.state.value.isPlaying)
            assertTrue(sound.state.value.linkedLights.isEmpty())
        } finally {
            main { app.stopAll(); sound.setPlayWithLight(false, false); sound.setPlayWithLight(true, false) }
        }
    }
}

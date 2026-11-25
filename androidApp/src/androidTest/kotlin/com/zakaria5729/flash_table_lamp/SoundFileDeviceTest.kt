package com.zakaria5729.flash_table_lamp

import android.content.Context
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SoundFileDeviceTest {
    @Test fun savedPathSurvivesReloadAndMissingFileRequiresRemoval() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefsName = "sound_file_validation_test"
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val file = java.io.File(context.cacheDir, "sound_file_validation.wav")
        file.writeBytes(byteArrayOf(1, 2, 3))
        try {
            prefs.edit().putString("imports", JSONArray().put(JSONObject().put("id", "validation")
                .put("title", "Validation sound").put("uri", Uri.fromFile(file).toString())).toString()).commit()
            val controller = SoundController(context, prefsName)
            controller.selectSound("validation")
            assertEquals("validation", controller.state.value.selectedId)
            assertNull(controller.state.value.unavailableSoundId)
            val restored = SoundController(context, prefsName)
            assertEquals(Uri.fromFile(file).toString(), restored.state.value.selected!!.uri)
            assertTrue(file.delete())
            restored.selectSound("validation")
            assertEquals("validation", restored.state.value.unavailableSoundId)
            restored.play()
            assertFalse(restored.state.value.isPlaying)
            assertFalse(restored.state.value.isPreparing)
            restored.removeSound("validation")
            assertNull(restored.state.value.unavailableSoundId)
            assertTrue(restored.state.value.sounds.none { it.id == "validation" })
            assertTrue(SoundController(context, prefsName).state.value.sounds.none { it.id == "validation" })
        } finally {
            file.delete()
            context.deleteSharedPreferences(prefsName)
        }
    }

    @Test fun remoteUndownloadedSoundCannotBeSelected() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val prefsName = "sound_remote_test"
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        try {
            prefs.edit().putString("drive_catalog", JSONArray().put(JSONObject().put("id", "remote_undownloaded")
                .put("title", "Remote undownloaded sound")).toString()).commit()
            lateinit var controller: SoundController
            instrumentation.runOnMainSync {
                controller = SoundController(context, prefsName)
                controller.selectSound("remote_undownloaded")
            }
            assertNotEquals("remote_undownloaded", controller.state.value.selectedId)
        } finally {
            context.deleteSharedPreferences(prefsName)
        }
    }
}

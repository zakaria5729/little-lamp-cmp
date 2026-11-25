package com.zakaria5729.flash_table_lamp

import android.content.Context
import android.provider.Settings
import androidx.core.content.edit

data class RunningSession(
    val deadline: Long,
    val rateStartedAt: Long,
    val rateStartStrength: Int,
    val rhythmStartedAt: Long = 0,
)

class FlashlightSessionStore(context: Context) {
    private val preferences = context.getSharedPreferences("active_session", Context.MODE_PRIVATE)
    private val boot = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)

    fun load(): RunningSession? {
        if (!preferences.getBoolean("running", false) || preferences.getInt("boot", -2) != boot) return null
        return RunningSession(
            preferences.getLong("deadline", 0),
            preferences.getLong("rate_started_at", 0),
            preferences.getInt("rate_start_strength", 1),
            preferences.getLong("rhythm_started_at", 0),
        )
    }

    fun save(session: RunningSession) {
        preferences.edit(commit = true) {
            putBoolean("running", true)
            putInt("boot", boot)
            putLong("deadline", session.deadline)
            putLong("rate_started_at", session.rateStartedAt)
            putInt("rate_start_strength", session.rateStartStrength)
            putLong("rhythm_started_at", session.rhythmStartedAt)
        }
    }

    fun clear() {
        preferences.edit(commit = true) { clear() }
    }
}

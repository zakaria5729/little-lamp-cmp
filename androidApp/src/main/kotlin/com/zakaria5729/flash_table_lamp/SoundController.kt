package com.zakaria5729.flash_table_lamp

import android.content.Context
import android.content.Intent
import android.app.NotificationManager
import android.widget.Toast
import android.net.Uri
import android.os.SystemClock
import android.provider.OpenableColumns
import android.provider.Settings
import android.text.Html
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

private const val DRIVE_FOLDER_ID = "1ntsscb5pygRaMGX_lJM9VgC7lmp0SreI"

data class SoundSession(val deadline: Long, val position: Int, val fadeStartedAt: Long, val fadeStartVolume: Int)

class SoundController(private val context: Context, preferencesName: String = "ambient_sounds") : SoundActions {
    private val prefs = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    private val session = context.getSharedPreferences("sound_session", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val boot = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
    private val mutableState = MutableStateFlow(SoundState())
    val state = mutableState.asStateFlow()
    var isUiVisible = false
    var resumePosition = 0
    var deadline = 0L
    var fadeStartedAt = 0L
    var fadeStartVolume = 30

    init {
        val remote = try {
            val saved = JSONArray(prefs.getString("drive_catalog", "[]"))
            (0 until saved.length()).map { i -> saved.getJSONObject(i).let { remoteSound(it.getString("id"), it.getString("title")) } }
        } catch (_: Exception) { emptyList() }
        val imported = try {
            val saved = JSONArray(prefs.getString("imports", "[]"))
            (0 until saved.length()).map { i -> saved.getJSONObject(i).let { AmbientSound(it.getString("id"), it.getString("title"), it.getString("uri"), true) } }
        } catch (_: Exception) { emptyList() }
        val tracks = remote + imported
        val savedSelection = prefs.getString("selected", "").orEmpty()
        mutableState.value = SoundState(sounds = tracks,
            selectedId = savedSelection.takeIf { id -> tracks.any { it.id == id && (!it.remote || it.downloaded) } }
                ?: tracks.firstOrNull { it.downloaded || it.imported }?.id.orEmpty(),
            withFlashlight = prefs.getBoolean("with_flashlight", false), withScreenLamp = prefs.getBoolean("with_screen", false),
            linkedLights = prefs.getStringSet("linked_lights", emptySet()).orEmpty().toSet(),
            volume = prefs.getInt("volume", 30).coerceIn(0, 100), currentVolume = prefs.getInt("volume", 30).coerceIn(0, 100),
            fadeGradually = prefs.getBoolean("fade", false),
            fadeMode = DimmingMode.entries.firstOrNull { it.name == prefs.getString("fade_mode", null) } ?: DimmingMode.Auto,
            fadePercent = prefs.getInt("fade_rate", 10).coerceIn(1, 100),
            timerSeconds = prefs.getInt("timer", 0).coerceIn(0, 86400))
    }

    override fun refreshSoundLibrary() {
        if (state.value.remoteLibraryLoading) return
        mutableState.update { it.copy(remoteLibraryLoading = true, remoteLibraryError = null) }
        scope.launch {
            try {
                val catalog = withContext(Dispatchers.IO) { fetchDriveCatalog() }
                prefs.edit().putString("drive_catalog", JSONArray().apply {
                    catalog.forEach { (id, title) -> put(JSONObject().put("id", id).put("title", title)) }
                }.toString()).apply()
                mutableState.update { current ->
                    val imports = current.sounds.filter { it.imported }
                    val tracks = catalog.map { (id, title) -> remoteSound(id, title) } + imports
                    val selected = current.selectedId.takeIf { id -> tracks.any { it.id == id && (!it.remote || it.downloaded) } }
                        ?: tracks.firstOrNull { it.downloaded || it.imported }?.id.orEmpty()
                    current.copy(sounds = tracks, selectedId = selected, remoteLibraryLoading = false, remoteLibraryError = null)
                }
                persist()
            } catch (error: Exception) {
                mutableState.update { it.copy(remoteLibraryLoading = false,
                    remoteLibraryError = "Couldn't load sounds from Drive. Check your internet connection and try again.") }
            }
        }
    }

    override fun downloadSound(id: String) {
        val track = state.value.sounds.firstOrNull { it.id == id && it.remote && !it.downloaded && !it.downloading } ?: return
        mutableState.update { it.copy(sounds = it.sounds.map { sound -> if (sound.id == id) sound.copy(downloading = true) else sound }) }
        scope.launch {
            try {
                val file = withContext(Dispatchers.IO) { downloadDriveSound(track) }
                mutableState.update { current -> current.copy(
                    sounds = current.sounds.map { sound -> if (sound.id == id) sound.copy(uri = Uri.fromFile(file).toString(), downloaded = true, downloading = false) else sound },
                    message = "Downloaded ${track.title}. Select it and press Play.") }
            } catch (_: Exception) {
                mutableState.update { current -> current.copy(
                    sounds = current.sounds.map { sound -> if (sound.id == id) sound.copy(downloading = false) else sound },
                    message = "Couldn't download ${track.title}. Check the connection or try again later.") }
            }
        }
    }

    private fun fetchDriveCatalog(): List<Pair<String, String>> {
        val connection = URL("https://drive.google.com/embeddedfolderview?id=$DRIVE_FOLDER_ID#list")
            .openConnection() as HttpURLConnection
        connection.connectTimeout = 20_000
        connection.readTimeout = 30_000
        connection.setRequestProperty("User-Agent", "LittleLamp/1.0 (Android)")
        try {
            if (connection.responseCode !in 200..299) error("Drive returned ${connection.responseCode}")
            val html = connection.inputStream.bufferedReader().use { it.readText() }
            val entry = Regex("""<div class="flip-entry" id="entry-([^"]+)"[\s\S]*?<div class="flip-entry-title">(.*?)</div>""")
            val tracks = entry.findAll(html).mapNotNull { match ->
                val title = Html.fromHtml(match.groupValues[2], Html.FROM_HTML_MODE_LEGACY).toString().trim()
                val id = match.groupValues[1].trim()
                if (id.isBlank() || title.isBlank()) null else id to title.replace('_', ' ')
            }.toList()
            if (tracks.isEmpty()) error("No audio files found in the shared Drive folder")
            return tracks
        } finally { connection.disconnect() }
    }

    private fun remoteSound(id: String, title: String): AmbientSound {
        val extension = title.substringAfterLast('.', "audio").lowercase().filter { it.isLetterOrDigit() }.take(8).ifBlank { "audio" }
        val file = File(soundDirectory(), "$id.$extension")
        return AmbientSound(id, title.replace('_', ' '), if (file.isFile) Uri.fromFile(file).toString() else "drive:$id",
            remote = true, downloaded = file.isFile)
    }

    private fun soundDirectory() = File(context.filesDir, "drive_sounds").apply { mkdirs() }

    private fun downloadDriveSound(sound: AmbientSound): File {
        val extension = sound.title.substringAfterLast('.', "audio").lowercase().filter { it.isLetterOrDigit() }.take(8).ifBlank { "audio" }
        val target = File(soundDirectory(), "${sound.id}.$extension")
        val temporary = File(soundDirectory(), "${sound.id}.$extension.part")
        val connection = URL("https://drive.google.com/uc?export=download&confirm=t&id=${sound.id}")
            .openConnection() as HttpURLConnection
        connection.connectTimeout = 20_000
        connection.readTimeout = 30_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "LittleLamp/1.0 (Android)")
        try {
            if (connection.responseCode !in 200..299) error("Drive returned ${connection.responseCode}")
            if (connection.contentType.orEmpty().contains("text/html", ignoreCase = true)) error("Drive did not return an audio file")
            val maxBytes = 100L * 1024 * 1024
            if (connection.contentLengthLong > maxBytes) error("Sound file is too large")
            var total = 0L
            connection.inputStream.use { input -> temporary.outputStream().buffered().use { output ->
                val buffer = ByteArray(32 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > maxBytes) error("Sound file is too large")
                    output.write(buffer, 0, count)
                }
            } }
            if (total == 0L || !temporary.renameTo(target)) error("Download was empty or could not be saved")
            return target
        } finally {
            connection.disconnect()
            if (temporary.exists()) temporary.delete()
        }
    }

    override fun selectSound(id: String) {
        val track = state.value.sounds.firstOrNull { it.id == id } ?: return
        if (track.remote && !track.downloaded) {
            Toast.makeText(context, "Download this sound first to play it.", Toast.LENGTH_SHORT).show()
            return
        }
        if (!validateSound(track) || state.value.selectedId == id) return
        val resume = state.value.isPlaying || state.value.isPreparing
        val links = state.value.linkedLights
        stop()
        mutableState.update { it.copy(selectedId = id, linkedLights = links, message = null) }
        persist()
        if (resume) playInternal()
    }

    override fun setPlayWithLight(screenLamp: Boolean, enabled: Boolean) {
        mutableState.update { if (screenLamp) it.copy(withScreenLamp = enabled) else it.copy(withFlashlight = enabled) }
        persist()
        val source = if (screenLamp) "screen" else "flashlight"
        val app = context.applicationContext as LumaApplication
        if (!enabled) lightStopped(source)
        else if (if (screenLamp) app.screenLamp.state.value.isOn else app.flashlight.state.value.isOn) lightStarted(source)
    }

    internal fun lightStarted(source: String) {
        if (!(if (source == "screen") state.value.withScreenLamp else state.value.withFlashlight)) return
        if (state.value.linkedLights.isEmpty() && (state.value.isPlaying || state.value.isPreparing)) return
        mutableState.update { it.copy(linkedLights = it.linkedLights + source) }; persist()
        playInternal()
    }

    internal fun resumeLinkedPlaybackAllowed(): Boolean {
        if (state.value.linkedLights.isEmpty()) return true
        val app = context.applicationContext as LumaApplication
        val now = SystemClock.elapsedRealtime()
        val flashSession = FlashlightSessionStore(context).load()
        val live = state.value.linkedLights.filter { source ->
            if (source == "screen") app.screenLamp.state.value.isOn
            else app.flashlight.state.value.isOn || (flashSession != null && (flashSession.deadline == 0L || flashSession.deadline > now))
        }.toSet()
        mutableState.update { it.copy(linkedLights = live) }; persist()
        if (live.isEmpty()) { stop(); return false }
        return true
    }

    internal fun lightStopped(source: String) {
        if (source !in state.value.linkedLights) return
        mutableState.update { it.copy(linkedLights = it.linkedLights - source) }; persist()
        if (state.value.linkedLights.isEmpty()) stop()
    }

    override fun playWithLight(screenLamp: Boolean) {
        val app = context.applicationContext as LumaApplication
        val on = if (screenLamp) app.screenLamp.state.value.isOn else app.flashlight.state.value.isOn
        val enabled = if (screenLamp) state.value.withScreenLamp else state.value.withFlashlight
        if (enabled && on) lightStarted(if (screenLamp) "screen" else "flashlight") else play()
    }

    override fun play() {
        mutableState.update { it.copy(linkedLights = emptySet()) }; persist()
        playInternal()
    }

    private fun playInternal() {
        if (state.value.isPlaying || state.value.isPreparing || state.value.selected == null) return
        if (state.value.unavailableSoundId != null || !validateSelectedSound()) return
        mutableState.update { it.copy(isPreparing = true, message = null) }
        try { ContextCompat.startForegroundService(context, Intent(context, SoundPlaybackService::class.java).setAction(SoundPlaybackService.PLAY)) }
        catch (_: Exception) { stopped("Couldn't start sound playback. Reopen the app and try again.") }
    }

    override fun pause() {
        if (state.value.isPlaying || state.value.isPreparing) send(SoundPlaybackService.PAUSE)
    }
    override fun stop() {
        mutableState.update { it.copy(linkedLights = emptySet()) }; persist()
        clearSession(); resumePosition = 0; deadline = 0
        context.stopService(Intent(context, SoundPlaybackService::class.java))
        context.getSystemService(NotificationManager::class.java).cancel(SoundPlaybackService.NOTIFICATION_ID)
        mutableState.update { it.copy(isPlaying = false, isPreparing = false, remainingSeconds = 0) }
    }
    override fun setVolume(percent: Int) {
        mutableState.update { it.copy(volume = percent.coerceIn(0, 100)) }; persist()
        if (state.value.isPlaying || state.value.isPreparing) send(SoundPlaybackService.VOLUME)
    }
    override fun setTimer(seconds: Int) {
        mutableState.update { it.copy(timerSeconds = seconds.coerceIn(0, 86400)) }; persist()
        if (state.value.isPlaying || state.value.isPreparing) send(SoundPlaybackService.TIMER)
    }
    override fun setFadeGradually(enabled: Boolean) {
        mutableState.update { it.copy(fadeGradually = enabled) }; fadeConfigChanged()
    }
    override fun setFadeMode(mode: DimmingMode) {
        mutableState.update { it.copy(fadeMode = mode) }; fadeConfigChanged()
    }
    override fun setFadeRate(percent: Int) {
        mutableState.update { it.copy(fadePercent = percent.coerceIn(1, 100)) }; fadeConfigChanged()
    }
    private fun fadeConfigChanged() {
        persist()
        if (state.value.isPlaying || state.value.isPreparing) send(SoundPlaybackService.VOLUME)
    }
    internal fun restartFade() {
        fadeStartedAt = SystemClock.elapsedRealtime()
        fadeStartVolume = state.value.volume
    }
    internal fun effectiveVolume(now: Long = SystemClock.elapsedRealtime()): Int {
        val current = state.value
        return if (current.fadeGradually && deadline > 0 && fadeStartedAt > 0)
            fadingSoundVolume(fadeStartVolume, now - fadeStartedAt, deadline - fadeStartedAt, current.fadeMode, current.fadePercent)
        else current.volume
    }
    internal fun appliedVolume(percent: Int) { mutableState.update { it.copy(currentVolume = percent) } }
    internal fun validateSelectedSound(): Boolean = state.value.selected?.let(::validateSound) ?: false

    private fun validateSound(sound: AmbientSound): Boolean {
        if (sound.remote) {
            val exists = sound.downloaded && File(sound.uri.toUri().path.orEmpty()).isFile
            if (!exists) {
                if (state.value.selectedId == sound.id) stop()
                mutableState.update { current -> current.copy(sounds = current.sounds.map {
                    if (it.id == sound.id) it.copy(uri = "drive:${sound.id}", downloaded = false, downloading = false) else it
                }, message = "${sound.title} is no longer downloaded. Download it again to play.") }
            }
            return exists
        }
        if (!sound.imported) return true
        val accessible = try {
            context.contentResolver.openAssetFileDescriptor(sound.uri.toUri(), "r")?.use { true } ?: false
        } catch (_: Exception) { false }
        if (!accessible) {
            if (state.value.selectedId == sound.id) stop()
            mutableState.update { it.copy(unavailableSoundId = sound.id, message = null) }
        }
        return accessible
    }

    override fun removeSound(id: String) {
        val removed = state.value.sounds.firstOrNull { it.id == id } ?: return
        if (!removed.imported && !(removed.remote && removed.downloaded)) return
        if (state.value.selectedId == id) stop()
        if (removed.remote) {
            File(removed.uri.toUri().path.orEmpty()).delete()
            mutableState.update { current -> current.copy(
                sounds = current.sounds.map { sound -> if (sound.id == id) sound.copy(uri = "drive:$id", downloaded = false, downloading = false) else sound },
                selectedId = if (current.selectedId == id) current.sounds.firstOrNull { it.id != id && (it.downloaded || it.imported) }?.id.orEmpty() else current.selectedId)
            }
        } else {
            mutableState.update { current -> current.copy(sounds = current.sounds.filterNot { it.id == id },
                selectedId = if (current.selectedId == id) current.sounds.firstOrNull { it.id != id && (it.downloaded || it.imported) }?.id.orEmpty() else current.selectedId,
                unavailableSoundId = current.unavailableSoundId.takeUnless { missing -> missing == id }) }
            try { context.contentResolver.releasePersistableUriPermission(removed.uri.toUri(), Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            catch (_: Exception) { }
        }
        persist()
    }
    override fun dismissMessage() { mutableState.update { it.copy(message = null) } }

    fun importSound(uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val title = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }?.take(80) ?: "My sound"
            val existing = state.value.sounds.firstOrNull { it.uri == uri.toString() }
            val track = existing ?: AmbientSound(UUID.randomUUID().toString(), title, uri.toString(), true)
            stop()
            mutableState.update { it.copy(sounds = if (existing == null) it.sounds + track else it.sounds, selectedId = track.id,
                message = "Added $title. Press play to listen.") }
            persist()
        } catch (_: Exception) { mutableState.update { it.copy(message = "Couldn't open that audio file. Choose a downloaded audio file from Files.") } }
    }

    fun resetIdleControls() {
        if (state.value.isPlaying || state.value.isPreparing) return
        resumePosition = 0; deadline = 0; clearSession()
        context.getSystemService(NotificationManager::class.java).cancel(SoundPlaybackService.NOTIFICATION_ID)
        mutableState.update { SoundState(sounds = it.sounds, withFlashlight = it.withFlashlight, withScreenLamp = it.withScreenLamp) }; persist()
    }
    internal fun playing() { mutableState.update { it.copy(isPlaying = true, isPreparing = false, message = null) } }
    internal fun preparing() { mutableState.update { it.copy(isPreparing = true, isPlaying = false, message = null) } }
    internal fun interrupted() { mutableState.update { it.copy(isPlaying = false, isPreparing = false) } }
    internal fun paused(position: Int) {
        resumePosition = position; clearSession()
        mutableState.update { it.copy(isPlaying = false, isPreparing = false) }
    }
    internal fun stopped(message: String? = null) {
        mutableState.update { it.copy(linkedLights = emptySet()) }; persist()
        clearSession(); resumePosition = 0; deadline = 0
        mutableState.update { it.copy(isPlaying = false, isPreparing = false, remainingSeconds = 0, message = message ?: it.message) }
    }
    internal fun updateRemaining() {
        mutableState.update { it.copy(remainingSeconds = if (deadline > 0) remainingSeconds(deadline, SystemClock.elapsedRealtime()) else 0) }
    }
    internal fun saveSession(position: Int) {
        session.edit { putBoolean("running", true); putInt("boot", boot); putLong("deadline", deadline); putInt("position", position)
            putLong("fade_start", fadeStartedAt); putInt("fade_volume", fadeStartVolume) }
    }
    internal fun loadSession(): SoundSession? {
        if (!session.getBoolean("running", false) || session.getInt("boot", -2) != boot) return null
        return SoundSession(session.getLong("deadline", 0), session.getInt("position", 0),
            session.getLong("fade_start", SystemClock.elapsedRealtime()), session.getInt("fade_volume", state.value.volume))
    }
    internal fun clearSession() { session.edit(commit = true) { clear() } }
    private fun send(action: String) {
        try { context.startService(Intent(context, SoundPlaybackService::class.java).setAction(action)) }
        catch (_: Exception) { stop() }
    }
    private fun persist() {
        val current = state.value
        prefs.edit {
            putBoolean("with_flashlight", current.withFlashlight); putBoolean("with_screen", current.withScreenLamp)
            putStringSet("linked_lights", current.linkedLights)
            putString("selected", current.selectedId); putInt("volume", current.volume); putInt("timer", current.timerSeconds)
            putBoolean("fade", current.fadeGradually); putString("fade_mode", current.fadeMode.name); putInt("fade_rate", current.fadePercent)
            putString("imports", JSONArray().apply { current.sounds.filter { it.imported }.forEach {
                put(JSONObject().put("id", it.id).put("title", it.title).put("uri", it.uri))
            } }.toString())
        }
    }
}

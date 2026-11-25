package com.zakaria5729.flash_table_lamp

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
internal fun SoundsControls(state: SoundState, actions: SoundActions, onToggle: () -> Unit, onPick: () -> Unit) {
    var timerDialog by rememberSaveable { mutableStateOf(false) }
    val selectedSound = state.selected
    Text("A softer soundtrack.", fontSize = 29.sp, fontWeight = FontWeight.Medium)
        Text("Download a sound to your device, then play it on repeat.", fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 19.sp)
    LampCard {
        Text(state.selected?.title ?: "Choose a sound", fontSize = 24.sp, fontWeight = FontWeight.Medium)
        Text(when { state.isPreparing -> "Preparing sound…"; state.isPlaying -> "Playing · loops continuously"; else -> "Ready when you are" },
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onToggle, enabled = selectedSound != null && (!selectedSound.remote || selectedSound.downloaded),
                modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                Text(if (state.isPlaying || state.isPreparing) "Pause sound" else "Play sound")
            }
            OutlinedButton(onClick = actions::stop, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) { Text("Stop sound") }
        }
        Spacer(Modifier.height(18.dp))
        PanelHeading("Volume", if (state.isPlaying && state.fadeGradually && state.timerSeconds > 0) "${state.currentVolume}% now · ${state.volume}% start" else "${state.volume}%")
        Slider(state.volume.toFloat(), { actions.setVolume(it.roundToInt()) }, valueRange = 0f..100f,
            modifier = Modifier.semantics { contentDescription = "Sound volume" })
        Text("Sound-only playback runs independently. Linked playback stops when its last linked light stops.", fontSize = 10.sp,
            lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    LampCard {
        PanelHeading("Your sound library", "${state.sounds.size} sounds")
        if (state.remoteLibraryLoading) Text("Loading sounds from Drive…", fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        state.remoteLibraryError?.let { error ->
            Text(error, fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = actions::refreshSoundLibrary) { Text("Retry loading sounds") }
        }
        state.sounds.forEach { sound ->
            Row(Modifier.fillMaxWidth().clickable { actions.selectSound(sound.id) }.padding(vertical = 8.dp)
                .semantics { contentDescription = "Select sound ${sound.title}" }, verticalAlignment = Alignment.CenterVertically) {
                RadioButton(state.selectedId == sound.id, onClick = { actions.selectSound(sound.id) })
                Column(Modifier.weight(1f)) {
                    Text(sound.title, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(when {
                        sound.remote && sound.downloaded -> "Downloaded · saved on this device"
                        sound.remote -> "Available to download"
                        else -> "From your device"
                    }, fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (sound.remote) {
                    TextButton(onClick = {
                        if (sound.downloaded) actions.removeSound(sound.id) else actions.downloadSound(sound.id)
                    }, enabled = !sound.downloading) {
                        Text(when { sound.downloading -> "Downloading…"; sound.downloaded -> "Remove"; else -> "Download" }, fontSize = 10.sp)
                    }
                }
                if (sound.imported) TextButton(onClick = { actions.removeSound(sound.id) }) { Text("Remove", fontSize = 10.sp) }
            }
        }
        OutlinedButton(onClick = onPick, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) { Text("Add audio from device") }
        Text("Downloaded Drive sounds stay on this device and loop until you stop playback. Removing a download only deletes this app's copy.",
            fontSize = 10.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 10.dp))
    }
    LampCard {
        PanelHeading("Sound sleep timer", if (state.isPlaying && state.timerSeconds > 0) formatCountdown(state.remainingSeconds) else durationLabel(state.timerSeconds))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(0 to "Off", 300 to "5m", 900 to "15m", 1800 to "30m").forEach { (seconds, label) ->
                FilterChip(state.timerSeconds == seconds, { actions.setTimer(seconds) }, label = { Text(label) })
            }
        }
        TextButton(onClick = { timerDialog = true }, contentPadding = PaddingValues(0.dp)) { Text("Custom sound timer ↗") }
        SettingToggle("Fade sound gradually", "Lower this app's volume while the sound timer runs",
            state.fadeGradually, actions::setFadeGradually, enabled = state.timerSeconds > 0)
        if (state.timerSeconds == 0) Text("Set a sound timer to enable fading.", fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (state.fadeGradually && state.timerSeconds > 0) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DimmingMode.entries.forEach { mode ->
                    FilterChip(state.fadeMode == mode, { actions.setFadeMode(mode) }, label = {
                        Text(if (mode == DimmingMode.Auto) "Auto" else "/ ${mode.unit}", fontSize = 11.sp)
                    })
                }
            }
            if (state.fadeMode != DimmingMode.Auto) {
                PanelHeading("Volume reduction", "${state.fadePercent}% / ${state.fadeMode.unit}")
                Slider(state.fadePercent.toFloat(), { actions.setFadeRate(it.roundToInt()) }, valueRange = 1f..100f,
                    modifier = Modifier.semantics { contentDescription = "Sound fade percentage" })
            }
            Text(if (state.fadeMode == DimmingMode.Auto) "Fade evenly to silence when the sound timer finishes."
                else "Reduce volume by ${state.fadePercent} percentage points per ${state.fadeMode.unit}. Playback stops at silence or when the timer finishes.",
                fontSize = 10.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (timerDialog) CustomTimerDialog(state.timerSeconds, { timerDialog = false }, subject = "sound") {
        actions.setTimer(it); timerDialog = false
    }
}

@Composable
fun EmbeddedSounds(state: SoundState, actions: SoundActions, onPick: () -> Unit,
                   screenLamp: Boolean = false, onOpenSounds: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        LampCard {
            PanelHeading("Relaxing sounds", if (state.isPlaying) "Playing" else "Optional")
            SettingToggle("Play sound with this light", "Start sound with the light and stop it when the light stops",
                if (screenLamp) state.withScreenLamp else state.withFlashlight,
                { actions.setPlayWithLight(screenLamp, it) })
            Text(state.selected?.title ?: "Choose a sound", modifier = Modifier.padding(top = 8.dp), fontSize = 13.sp)
            TextButton(onClick = onOpenSounds, modifier = Modifier.align(Alignment.End)) { Text("Open sound page ↗") }
        }
    }
}

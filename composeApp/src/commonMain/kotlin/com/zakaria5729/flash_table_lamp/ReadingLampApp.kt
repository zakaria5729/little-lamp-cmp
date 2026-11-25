package com.zakaria5729.flash_table_lamp

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun ReadingLampApp(
    state: ScreenLampState,
    flashlight: FlashlightState,
    actions: ScreenLampActions,
    sound: SoundState,
    soundActions: SoundActions,
    onStartScreen: () -> Unit,
    onToggleFlashlight: () -> Unit,
    onEmergency: () -> Unit,
    onToggleSound: () -> Unit,
    onPickSound: () -> Unit,
    notificationsGranted: Boolean = false,
    onRequestNotificationPermission: () -> Unit = {},
    onOpenNotificationSettings: () -> Unit = {},
    brandLogo: @Composable () -> Unit = {},
    flashlightContent: @Composable (Boolean, () -> Unit) -> Unit,
) {
    val dark = when (state.theme) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Dark -> true
        ThemeMode.Light -> false
    }
    MaterialTheme(colorScheme = lumaColorScheme(dark)) {
        sound.sounds.firstOrNull { it.id == sound.unavailableSoundId }?.let { missing ->
            AlertDialog(onDismissRequest = {}, title = { Text("Sound file unavailable") },
                text = { Text("${missing.title} is missing or can no longer be opened on this device. Remove this entry and add the file again if needed.") },
                confirmButton = { TextButton(onClick = { soundActions.removeSound(missing.id) }) { Text("Remove sound") } })
        }
        if (state.isOn && state.fullscreen) {
            LampDisplay(state, flashlight, actions, onToggleFlashlight)
            return@MaterialTheme
        }
        val drawer = rememberDrawerState(DrawerValue.Closed)
        val scope = rememberCoroutineScope()
        var contextualReturnPage by rememberSaveable { mutableStateOf<LampPage?>(null) }
        val contextualTarget = when (state.page) {
            LampPage.Colors, LampPage.Zones, LampPage.Sounds -> state.page
            else -> null
        }
        LaunchedEffect(state.page) {
            if (contextualTarget == null && contextualReturnPage != null) contextualReturnPage = null
        }
        val navigateFromLamp: (LampPage, LampPage) -> Unit = { destination, origin ->
            contextualReturnPage = origin
            actions.selectPage(destination)
        }
        val openSounds: (LampPage) -> Unit = { origin -> navigateFromLamp(LampPage.Sounds, origin) }
        val goBack = {
            val destination = contextualReturnPage
            if (destination != null) {
                contextualReturnPage = null
                actions.selectPage(destination)
            }
        }
        BackHandler(enabled = contextualTarget != null && contextualReturnPage != null, onBack = goBack)
        val snackbar = remember { SnackbarHostState() }
        LaunchedEffect(state.page) {
            if (state.page == LampPage.Sounds) soundActions.refreshSoundLibrary()
        }
        LaunchedEffect(state.message) {
            state.message?.let { snackbar.showSnackbar(it); actions.dismissMessage() }
        }
        LaunchedEffect(sound.message) { sound.message?.let { snackbar.showSnackbar(it); soundActions.dismissMessage() } }
        ModalNavigationDrawer(
            drawerState = drawer,
            drawerContent = {
                ModalDrawerSheet(Modifier.widthIn(max = 320.dp).semantics {
                    contentDescription = if (drawer.isOpen) "Navigation menu open" else "Navigation menu"
                }) {
                    Row(Modifier.padding(horizontal = 24.dp, vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                        brandLogo()
                        Column(Modifier.padding(start = 14.dp)) {
                            Text(APP_NAME, fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp)
                            Text("LIGHT FOR YOUR EVERYDAY", fontSize = 9.sp, letterSpacing = 1.2.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    Text("YOUR LIGHTS", Modifier.padding(start = 28.dp, bottom = 12.dp), fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.primary, letterSpacing = 1.5.sp)
                    LampPage.entries.forEach { page ->
                        NavigationDrawerItem(
                            label = { Text(page.title) }, selected = state.page == page,
                            icon = { Text(when (page) {
                                LampPage.Flashlight -> "ϟ"; LampPage.Screen -> "◉"; LampPage.Colors -> "◐"
                                LampPage.Zones -> "▦"; LampPage.Sounds -> "♫"; LampPage.Presets -> "♡"; LampPage.Settings -> "⚙"
                            }, fontSize = 22.sp) },
                            onClick = { contextualReturnPage = null; actions.selectPage(page); scope.launch { drawer.close() } },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                                .semantics { contentDescription = "Navigate to ${page.title}" },
                        )
                    }
                    HorizontalDivider(Modifier.padding(24.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = .5f))
                    NavigationDrawerItem(label = { Text("Smart reading mode") }, selected = false,
                        icon = { Text("Aa", fontWeight = FontWeight.Medium) },
                        onClick = { scope.launch { drawer.close() }; actions.readingMode() },
                        modifier = Modifier.padding(horizontal = 12.dp))
                    NavigationDrawerItem(label = { Text("Emergency light") }, selected = false,
                        icon = { Text("✦", fontSize = 22.sp) },
                        onClick = { scope.launch { drawer.close() }; onEmergency() },
                        modifier = Modifier.padding(horizontal = 12.dp))
                    }
                    Text("A little light, on your terms.\nNo camera access. No recording.",
                        Modifier.padding(28.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp, lineHeight = 19.sp)
                }
            },
        ) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                snackbarHost = { SnackbarHost(snackbar) },
                topBar = {
                    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp).height(64.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        if (contextualTarget != null && contextualReturnPage != null) {
                            IconButton(onClick = goBack, modifier = Modifier.semantics { contentDescription = "Back to ${contextualReturnPage?.title}" }) {
                                Text("‹", fontSize = 36.sp, color = MaterialTheme.colorScheme.onSurface)
                            }
                        } else {
                            IconButton(onClick = { scope.launch { drawer.open() } },
                                modifier = Modifier.semantics { contentDescription = "Open navigation menu" }) {
                                val color = MaterialTheme.colorScheme.onSurface
                                Canvas(Modifier.size(22.dp)) {
                                    listOf(.2f, .5f, .8f).forEach { y ->
                                        drawLine(color, Offset(0f, size.height * y), Offset(size.width * .85f, size.height * y), 2.dp.toPx())
                                    }
                                }
                            }
                        }
                        Column(Modifier.padding(start = 8.dp).weight(1f)) {
                            Text(state.page.title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                            Text(if (flashlight.isOn) "REAR LIGHT IS ON" else "MAKE ROOM FOR A LITTLE LIGHT",
                                fontSize = 8.sp, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (state.batteryPercent >= 0) {
                            BatteryIndicator(state.batteryPercent, state.charging)
                        }
                    }
                },
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding).navigationBarsPadding(), contentAlignment = Alignment.TopCenter) {
                    if (state.page == LampPage.Flashlight) {
                        flashlightContent(dark) { openSounds(LampPage.Flashlight) }
                    } else {
                        Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().verticalScroll(remember(state.page) { ScrollState(0) })
                            .semantics { contentDescription = "${state.page.title} controls" }
                            .padding(horizontal = 24.dp).padding(top = 16.dp, bottom = 28.dp),
                            verticalArrangement = Arrangement.spacedBy(18.dp)) {
                            when (state.page) {
                                LampPage.Screen -> {
                                    ScreenControls(state, flashlight, actions, onStartScreen,
                                        onOpenColors = { navigateFromLamp(LampPage.Colors, LampPage.Screen) },
                                        onOpenZones = { navigateFromLamp(LampPage.Zones, LampPage.Screen) })
                                    EmbeddedSounds(sound, soundActions, onPickSound, screenLamp = true,
                                        onOpenSounds = { openSounds(LampPage.Screen) })
                                }
                                LampPage.Colors -> AdvancedColorControls(state, actions)
                                LampPage.Sounds -> SoundsControls(sound, soundActions, onToggleSound, onPickSound)
                                LampPage.Zones -> ZoneControls(state, actions)
                                LampPage.Presets -> PresetControls(state, actions)
                                LampPage.Settings -> SettingsControls(state, flashlight, actions, notificationsGranted, onRequestNotificationPermission, onOpenNotificationSettings)
                                else -> Unit
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScreenControls(state: ScreenLampState, flashlight: FlashlightState, actions: ScreenLampActions, onStart: () -> Unit,
                           onOpenColors: () -> Unit, onOpenZones: () -> Unit) {
    var customTimer by rememberSaveable { mutableStateOf(false) }
    Text("Your quiet corner, lit.", fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-1).sp, fontWeight = FontWeight.Medium)
    Text("Settle in with a light that feels like you.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    LampPreview(state, Modifier.fillMaxWidth().height(176.dp))
    Button(onClick = onStart, enabled = !state.isStopping, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().height(54.dp)) {
        Text(if (state.isOn) "Return to screen lamp" else "Turn screen lamp on", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
    if (state.isOn) TextButton(onClick = actions::stopSmoothly, enabled = !state.isStopping, modifier = Modifier.fillMaxWidth()) { Text("Turn screen lamp off") }
    if (state.batteryPercent in 0..20 && !state.charging) {
        Text("A softer light uses less battery. Try 25% brightness or Ultra-dim.",
            color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, lineHeight = 18.sp)
    }
    LampCard {
        PanelHeading("Brightness", "${state.brightness}%")
        Slider(state.brightness.toFloat(), { actions.setBrightness(it.roundToInt()) }, valueRange = 1f..100f,
            modifier = Modifier.semantics { contentDescription = "Screen brightness" })
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf(5, 25, 50, 100).forEach { value ->
                FilterChip(selected = state.brightness == value, onClick = { actions.setBrightness(value) }, label = { Text("$value%") })
            }
        }
        SettingToggle("Ultra-dim night mode", "Darker colors with minimum screen brightness", state.ultraDim, actions::setUltraDim)
    }
    LampCard {
        PanelHeading("Light color", "")
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Warm" to 0xFFFFD9A0.toInt(), "Neutral" to 0xFFFFF5E3.toInt(), "Cool" to 0xFFD8E8FF.toInt()).forEach { (name, color) ->
                FilterChip(state.color == color && !state.zonesEnabled && !state.gradient, {
                    actions.setZonesEnabled(false); actions.setGradient(false); actions.setColor(color, false)
                }, label = { Text(name) })
            }
        }
        TextButton(onClick = onOpenColors, contentPadding = PaddingValues(0.dp)) {
            Text("All colors & gradients ↗")
        }
        TextButton(onClick = onOpenZones, contentPadding = PaddingValues(0.dp)) {
            Text(if (state.zonesEnabled) "Edit ${state.zoneCount} lighting zones ↗" else "Split-screen lighting zones ↗")
        }
    }
    LampCard {
        PanelHeading("Sleep timer", if (state.isOn && state.timerSeconds > 0) formatCountdown(state.remainingSeconds) else durationLabel(state.timerSeconds))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf(0 to "Off", 300 to "5m", 900 to "15m", 1800 to "30m", 3600 to "1h").forEach { (seconds, label) ->
                FilterChip(state.timerSeconds == seconds, { actions.setTimer(seconds) }, label = { Text(label) })
            }
        }
        TextButton(onClick = { customTimer = true }, contentPadding = PaddingValues(0.dp)) { Text("Custom screen timer ↗") }
        SettingToggle("Dim gradually", "Fade your screen light as the timer runs", state.dim, actions::setDim,
            enabled = state.timerSeconds > 0)
        if (state.dim && state.timerSeconds > 0) {
            RateOptions(state.dimmingMode, state.dimRate, actions::setMode, actions::setRate)
        }
    }
    LampCard {
        SettingToggle("Dual light mode", "Also turn on the rear LED, with its own controls", state.dual, actions::setDual,
            enabled = flashlight.hasFlash)
        if (state.dual) Text("LED brightness and fading are configured in Flashlight. Starting both lights uses this sleep timer.",
            fontSize = 11.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Text("Screen light needs this view open. The rear LED keeps working when the app is closed.",
        fontSize = 11.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (customTimer) CustomTimerDialog(state.timerSeconds, { customTimer = false }) {
        actions.setTimer(it); customTimer = false
    }
}

@Composable
private fun PresetControls(state: ScreenLampState, actions: ScreenLampActions) {
    var showSave by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    Text("Your favorite little lights.", fontSize = 27.sp, fontWeight = FontWeight.Medium)
    Text("Save up to 12 combinations of color, brightness, timer and fading.", fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 19.sp)
    Button(onClick = { showSave = true }, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(16.dp)) {
        Text("Save current lighting preset")
    }
    if (state.presets.isEmpty()) LampCard {
        Text("Make this space yours.", fontSize = 18.sp, fontWeight = FontWeight.Medium)
        Text("Choose your light in Screen lamp, then save it here.", fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
    }
    state.presets.forEach { preset ->
        LampCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).background(Color(preset.color), RoundedCornerShape(12.dp)))
                Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                    Text(preset.name, fontWeight = FontWeight.SemiBold)
                    Text("${preset.brightness}% · ${durationLabel(preset.timerSeconds)}", fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { actions.applyPreset(preset) }) { Text("Use") }
            }
            TextButton(onClick = { actions.deletePreset(preset.name) }, contentPadding = PaddingValues(0.dp)) { Text("Delete", fontSize = 11.sp) }
        }
    }
    if (showSave) AlertDialog(onDismissRequest = { showSave = false }, title = { Text("Save your light") },
        text = { OutlinedTextField(name, { if (it.length <= 24) name = it }, label = { Text("Preset name") }, singleLine = true) },
        confirmButton = { TextButton(onClick = { actions.savePreset(name); name = ""; showSave = false }) { Text("Save preset") } },
        dismissButton = { TextButton(onClick = { showSave = false }) { Text("Cancel") } })
}

@Composable
private fun SettingsControls(
    state: ScreenLampState,
    flashlight: FlashlightState,
    actions: ScreenLampActions,
    notificationsGranted: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onOpenNotificationSettings: () -> Unit = {},
) {
    Text("Settings", fontSize = 27.sp, fontWeight = FontWeight.Medium)
    Text("Appearance, notifications, battery, and device details.", fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 19.sp)
    LampCard {
        PanelHeading("Notifications", if (notificationsGranted) "Already given" else "Not given")
        Row(
            Modifier.fillMaxWidth().clickable {
                if (notificationsGranted) onOpenNotificationSettings() else onRequestNotificationPermission()
            }.padding(vertical = 8.dp)
                .semantics { contentDescription = if (notificationsGranted) "Notification permission already given" else "Grant notification permission" },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text("Notification permission", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text(
                    if (notificationsGranted) "Already given · alerts and background controls enabled"
                    else "Tap to allow timer and background light notifications",
                    fontSize = 10.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            if (notificationsGranted) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = .15f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        "Already given",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            } else {
                Button(
                    onClick = onRequestNotificationPermission,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text("Give permission", fontSize = 11.sp)
                }
            }
        }
        TextButton(onClick = onOpenNotificationSettings, contentPadding = PaddingValues(0.dp)) {
            Text("System notification settings ↗", fontSize = 11.sp)
        }
    }
    LampCard {
        PanelHeading("Appearance", "")
        ThemeMode.entries.forEach { theme ->
            Row(Modifier.fillMaxWidth().clickable { actions.setTheme(theme) }.padding(vertical = 4.dp)
                .semantics { contentDescription = "Use ${theme.name} theme"; selected = state.theme == theme },
                verticalAlignment = Alignment.CenterVertically) {
                RadioButton(state.theme == theme, onClick = { actions.setTheme(theme) })
                Column {
                    Text(theme.name, fontSize = 14.sp)
                    if (theme == ThemeMode.System) Text("Follow your device appearance", fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    LampCard {
        PanelHeading("Smooth light transitions", "")
        SettingToggle("Fade in & fade out", "Soften starting, stopping, and color changes", state.smoothTransitions, actions::setSmoothTransitions)
        if (state.smoothTransitions) {
            Text("Transition duration · ${state.transitionMillis / 1000f} sec", fontSize = 12.sp)
            Slider(state.transitionMillis.toFloat(), { actions.setTransitionMillis(it.roundToInt()) }, valueRange = 200f..2000f,
                modifier = Modifier.semantics { contentDescription = "Light transition duration" })
        }
    }
    LampCard {
        PanelHeading("Battery", if (state.batteryPercent >= 0) "${state.batteryPercent}%" else "—")
        Text(if (state.charging) "Your phone is charging." else "Lower brightness uses less power. Screen-off LED mode avoids keeping the display awake.",
            fontSize = 12.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 12.dp))
        SettingToggle("Stop lights at 5% battery", "Only when unplugged. This can end a timer early.", state.stopAtCriticalBattery, actions::setCriticalStop)
        SettingToggle("Emergency light also uses LED", "Adds the rear flashlight at maximum brightness", state.emergencyLed, actions::setEmergencyLed,
            enabled = flashlight.hasFlash)
    }
    LampCard {
        PanelHeading("Your device", "")
        Text(if (!flashlight.hasFlash) "Rear flashlight unavailable" else if (flashlight.supportsDimming)
            "Rear LED · ${flashlight.maxStrength} brightness levels" else "Rear LED · fixed brightness",
            fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
        Text("No camera or microphone permission. Background services run while the rear LED or sound playback is active. Android Force stop and uninstall stop them.",
            fontSize = 11.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
internal fun RateOptions(mode: DimmingMode, percent: Int, onMode: (DimmingMode) -> Unit, onRate: (Int) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        DimmingMode.entries.forEach { option ->
            FilterChip(mode == option, { onMode(option) }, label = { Text(option.label) })
        }
    }
    if (mode != DimmingMode.Auto) {
        var value by rememberSaveable(percent) { mutableStateOf(percent.toString()) }
        OutlinedTextField(value, { text ->
            if (text.length <= 3 && text.all(Char::isDigit)) {
                value = text
                text.toIntOrNull()?.takeIf { it in 1..100 }?.let(onRate)
            }
        }, label = { Text("Brightness reduction") }, suffix = { Text("% / ${mode.unit}") },
            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = value.toIntOrNull()?.let { it in 1..100 } != true, modifier = Modifier.fillMaxWidth())
    }
    Text(if (mode == DimmingMode.Auto) "Fade to minimum across the full timer."
        else "Reduce $percent% brightness per ${mode.unit}; hold at minimum until the timer ends.",
        fontSize = 10.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp))
}

@Composable
internal fun LampPreview(state: ScreenLampState, modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(28.dp)), contentAlignment = Alignment.Center) {
        LampBackdrop(state, Modifier.matchParentSize(), animate = false)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("✦", fontSize = 46.sp, color = Color.Black.copy(alpha = .55f))
            Text(if (state.ultraDim) "A SOFTER KIND OF LIGHT" else if (state.zonesEnabled) "${state.zoneCount} PERSONAL LIGHTING ZONES" else if (state.cycleColors) "COLORS, IN MOTION" else if (state.gradient) "YOUR PERSONAL GLOW" else "A MOMENT OF WARMTH",
                color = if (state.ultraDim) Color.White.copy(alpha = .6f) else Color.Black.copy(alpha = .6f),
                fontSize = 9.sp, letterSpacing = 1.4.sp)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LampDisplay(state: ScreenLampState, flashlight: FlashlightState, actions: ScreenLampActions, onToggleLed: () -> Unit) {
    var controls by rememberSaveable { mutableStateOf(true) }
    Box(Modifier.fillMaxSize()
        .semantics { contentDescription = "Full-screen colored lamp" }) {
        LampBackdrop(state, Modifier.matchParentSize())
        if (state.touchLocked) {
            Box(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(28.dp)
                .clip(CircleShape).background(Color.Black.copy(alpha = .38f))
                .combinedClickable(onClick = {}, onLongClick = { actions.setLocked(false) })
                .padding(horizontal = 22.dp, vertical = 16.dp)
                .semantics { contentDescription = "Hold to unlock lamp controls" }) {
                Text("Hold to unlock", color = Color.White, fontSize = 13.sp)
            }
        } else if (controls) {
            Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding().padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Surface(color = Color.Black.copy(alpha = .35f), shape = CircleShape) {
                    Text(if (state.timerSeconds > 0) "◷  ${formatCountdown(state.remainingSeconds)}" else "No timer",
                        Modifier.padding(horizontal = 16.dp, vertical = 10.dp), color = Color.White, fontSize = 12.sp)
                }
                TextButton(onClick = { controls = false }) { Text("Hide controls", color = Color.White) }
            }
            Surface(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(20.dp).widthIn(max = 480.dp),
                color = Color.Black.copy(alpha = .68f), shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(20.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("SCREEN LIGHT", color = Color.White.copy(alpha = .7f), fontSize = 10.sp, letterSpacing = 1.sp)
                        Text("${state.currentBrightness}%", color = Color.White, fontWeight = FontWeight.Medium)
                    }
                    Slider(state.brightness.toFloat(), { actions.setBrightness(it.roundToInt()) }, valueRange = 1f..100f, enabled = !state.isStopping,
                        modifier = Modifier.semantics { contentDescription = "Active screen brightness" })
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = actions::showControls, enabled = !state.isStopping) { Text("Settings", color = Color.White) }
                        TextButton(onClick = { actions.setLocked(true) }, enabled = !state.isStopping) { Text("Lock controls", color = Color.White) }
                        TextButton(onClick = actions::stopSmoothly, enabled = !state.isStopping) { Text(if (state.isStopping) "Fading out…" else "Screen off", color = Color.White) }
                    }
                    if (flashlight.isOn) TextButton(onClick = onToggleLed, modifier = Modifier.fillMaxWidth()) {
                        Text("Rear LED is on · turn LED off", color = Color.White)
                    }

                }
            }
        } else {
            TextButton(onClick = { controls = true }, modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(20.dp)) {
                Text("Show controls", color = Color.White.copy(alpha = .6f))
            }
        }
    }
}

@Composable
internal fun LampCard(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .6f), RoundedCornerShape(24.dp))
        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(24.dp)).padding(20.dp), content = content)
}

@Composable
internal fun PanelHeading(title: String, detail: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        if (detail.isNotEmpty()) Text(detail, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
internal fun SettingToggle(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, fontSize = 10.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp))
        }
        Switch(checked, onChange, enabled = enabled, colors = lampSwitchColors(), modifier = Modifier.semantics { contentDescription = title })
    }
}

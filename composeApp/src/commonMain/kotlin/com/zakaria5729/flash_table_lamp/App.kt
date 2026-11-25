package com.zakaria5729.flash_table_lamp

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val Background = Color(0xFF0C1015)
private val Card = Color(0xFF171D25)
private val Border = Color(0xFF2A323D)
private val Amber = Color(0xFFF3CE7B)
private val Muted = Color(0xFF929DAE)
private val White = Color(0xFFF1F3F5)

@Composable
fun App(
    state: FlashlightState,
    onToggle: () -> Unit,
    onStrengthChange: (Int) -> Unit,
    onTimerChange: (Int) -> Unit,
    onDimGraduallyChange: (Boolean) -> Unit,
    onDimmingModeChange: (DimmingMode) -> Unit,
    onDimmingRateChange: (Int) -> Unit,
    onDismissMessage: () -> Unit,
    dark: Boolean = true,
    embedded: Boolean = false,
    onRhythmChange: (Boolean) -> Unit = {},
    onRhythmModeChange: (RhythmMode) -> Unit = {},
    onRhythmTimesChange: (Int, Int) -> Unit = { _, _ -> },
    soundContent: @Composable () -> Unit = {},
) {
    MaterialTheme(
        colorScheme = lumaColorScheme(dark),
    ) {
        val Background = MaterialTheme.colorScheme.background
        val Card = MaterialTheme.colorScheme.surface
        val Border = MaterialTheme.colorScheme.outline
        val Amber = MaterialTheme.colorScheme.primary
        val Muted = MaterialTheme.colorScheme.onSurfaceVariant
        val White = MaterialTheme.colorScheme.onSurface
        var showCustomTimer by rememberSaveable { mutableStateOf(false) }
        val scroll = rememberScrollState()
        val snackbar = remember { SnackbarHostState() }
        LaunchedEffect(state.message) {
            state.message?.let {
                snackbar.showSnackbar(it, duration = SnackbarDuration.Long)
                onDismissMessage()
            }
        }
        Scaffold(
            containerColor = Background,
            contentWindowInsets = if (embedded) WindowInsets(0, 0, 0, 0) else ScaffoldDefaults.contentWindowInsets,
            snackbarHost = { SnackbarHost(snackbar) },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
                Column(
                    Modifier.widthIn(max = 480.dp).fillMaxWidth()
                        .verticalScroll(scroll).semantics { contentDescription = "Flashlight controls" }
                        .padding(horizontal = 24.dp)
                        .padding(top = 16.dp, bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (!embedded) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(42.dp).clip(RoundedCornerShape(14.dp))
                                .background(Amber.copy(alpha = .12f)),
                            contentAlignment = Alignment.Center,
                        ) { LampIcon(Modifier.size(23.dp), Amber) }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(APP_NAME, fontSize = 25.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.8).sp)
                            Text("A LITTLE LIGHT, ANYTIME", fontSize = 9.sp, color = Muted, letterSpacing = 1.6.sp)
                        }
                        Spacer(Modifier.weight(1f))
                        Text("FLASHLIGHT", color = Amber, fontSize = 9.sp, letterSpacing = 1.1.sp,
                            modifier = Modifier.border(1.dp, Border, CircleShape).padding(horizontal = 12.dp, vertical = 8.dp))
                    }
                    Spacer(Modifier.height(32.dp)) }
                    Row(
                        Modifier.clip(CircleShape).background(Card).padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        Box(Modifier.size(6.dp).background(if (state.isOn) Color(0xFF98D9AE) else Muted, CircleShape))
                        Text(when {
                            !state.hasFlash -> "NO FLASHLIGHT FOUND"
                            state.isStopping -> "FADING OUT"
                            state.isStarting -> "TURNING ON"
                            state.rhythmEnabled && state.isOn -> if (state.torchLit) "RHYTHM · LIGHT ON" else "RHYTHM · LIGHT OFF"
                            state.isOn -> "LIGHT IS ON"
                            !state.isAvailable -> "FLASHLIGHT BUSY"
                            else -> "READY WHEN YOU ARE"
                        }, color = if (state.isOn) Color(0xFF98D9AE) else Muted, fontSize = 10.sp, letterSpacing = 1.3.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                    PowerControl(state, onToggle)
                    Text(if (state.isOn) "Let there be light." else "Brighten your moment.",
                        fontSize = 25.sp, fontWeight = FontWeight.Medium, letterSpacing = (-.7).sp)
                    Spacer(Modifier.height(6.dp))
                    Text(if (state.isOn && state.rhythmEnabled) "Tap the power button to stop the rhythm" else if (state.isOn) "Tap the power button to turn off" else "Tap the power button to turn on",
                        fontSize = 12.sp, color = Muted)
                    Spacer(Modifier.height(28.dp))
                    ControlCard {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            SectionLabel("01", if (state.dimGradually && state.timerSeconds > 0) "Starting brightness" else "Brightness")
                            Spacer(Modifier.weight(1f))
                            Text("${state.brightnessPercent}", color = Amber, fontSize = 26.sp, fontWeight = FontWeight.Medium)
                            Text(" %", color = Muted, fontSize = 12.sp)
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AdjustButton("Decrease brightness", "−", state.supportsDimming && state.strength > 1) {
                                onStrengthChange(state.strength - 1)
                            }
                            Slider(
                                value = state.strength.toFloat(),
                                onValueChange = { onStrengthChange(it.roundToInt()) },
                                valueRange = 1f..state.maxStrength.coerceAtLeast(2).toFloat(),
                                steps = (state.maxStrength - 2).coerceAtLeast(0),
                                enabled = state.supportsDimming,
                                modifier = Modifier.weight(1f).padding(horizontal = 8.dp).semantics {
                                    contentDescription = "Brightness"
                                    stateDescription = "${state.brightnessPercent} percent, level ${state.strength} of ${state.maxStrength}"
                                },
                                colors = SliderDefaults.colors(thumbColor = Amber, activeTrackColor = Amber,
                                    inactiveTrackColor = Border),
                            )
                            AdjustButton("Increase brightness", "+", state.supportsDimming && state.strength < state.maxStrength) {
                                onStrengthChange(state.strength + 1)
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("SOFT", fontSize = 9.sp, color = Muted, letterSpacing = 1.sp)
                            Text(when {
                                state.isFading -> "NOW ${state.currentBrightnessPercent}% · FADING"
                                state.supportsDimming -> "${state.strength} OF ${state.maxStrength} LEVELS"
                                else -> "FIXED BRIGHTNESS"
                            }, fontSize = 9.sp, color = if (state.isFading) Amber else Muted, letterSpacing = 1.sp)
                            Text("BRIGHT", fontSize = 9.sp, color = Muted, letterSpacing = 1.sp)
                        }
                        if (!state.supportsDimming) {
                            Spacer(Modifier.height(12.dp))
                            Text("This device supports on/off only. Adjustable brightness needs compatible hardware and Android 13 or newer.",
                                fontSize = 11.sp, color = Muted, lineHeight = 17.sp)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    ControlCard {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            SectionLabel("02", "Auto-off timer")
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { showCustomTimer = true }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                                Text("Custom ↗", fontSize = 12.sp)
                            }
                        }
                        val countdown = if (state.isOn && state.timerSeconds > 0) state.remainingSeconds else state.timerSeconds
                        Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(if (state.timerSeconds == 0) "∞" else formatCountdown(countdown),
                                fontFamily = FontFamily.Monospace, fontSize = 38.sp, fontWeight = FontWeight.Light,
                                letterSpacing = (-1.5).sp, modifier = Modifier.semantics { contentDescription = "Timer ${formatCountdown(countdown)}" })
                            Spacer(Modifier.width(14.dp))
                            Text(when {
                                state.timerSeconds == 0 -> "Stays on until\nyou turn it off"
                                state.isOn -> "remaining until\nthe light turns off"
                                else -> "Starts when you\nturn on the light"
                            }, fontSize = 11.sp, color = Muted, lineHeight = 17.sp)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            listOf(0 to "Off", 60 to "1m", 300 to "5m", 900 to "15m", 1800 to "30m").forEach { (seconds, label) ->
                                val selected = state.timerSeconds == seconds
                                Surface(
                                    onClick = { onTimerChange(seconds) },
                                    color = if (selected) Amber else Background,
                                    contentColor = if (selected) Background else Muted,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f).height(42.dp).semantics {
                                        contentDescription = if (seconds == 0) "No auto-off timer" else "Set timer to ${durationLabel(seconds)}"
                                        this.selected = selected
                                    },
                                ) { Box(contentAlignment = Alignment.Center) { Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium) } }
                            }
                        }
                        if (state.timerSeconds !in listOf(0, 60, 300, 900, 1800)) {
                            Text("Custom timer · ${durationLabel(state.timerSeconds)}", color = Amber, fontSize = 11.sp,
                                modifier = Modifier.padding(top = 12.dp))
                        }
                        if (state.isOn && state.timerSeconds > 0) {
                            Spacer(Modifier.height(14.dp))
                            LinearProgressIndicator(
                                progress = { (state.remainingSeconds.toFloat() / state.timerSeconds).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth().height(3.dp),
                                color = Amber, trackColor = Border,
                            )
                            Text("Choose a duration to restart the countdown", fontSize = 10.sp, color = Muted,
                                modifier = Modifier.padding(top = 8.dp))
                        }
                        Spacer(Modifier.height(18.dp))
                        HorizontalDivider(color = Border)
                        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                                Text("Dim gradually", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                Spacer(Modifier.height(4.dp))
                                Text(when {
                                    !state.supportsDimming -> "Not supported on this device"
                                    state.timerSeconds == 0 -> "Set a timer to fade the light"
                                    else -> "Fade while the timer runs"
                                }, fontSize = 10.sp, color = Muted, lineHeight = 15.sp)
                            }
                            Switch(
                                checked = state.dimGradually,
                                onCheckedChange = onDimGraduallyChange,
                                enabled = state.supportsDimming && state.timerSeconds > 0,
                                colors = lampSwitchColors(),
                                modifier = Modifier.semantics { contentDescription = "Dim gradually with timer" },
                            )
                        }
                        if (state.dimGradually && state.timerSeconds > 0 && state.supportsDimming) {
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                DimmingMode.entries.forEach { mode ->
                                    FilterChip(selected = state.dimmingMode == mode,
                                        onClick = { onDimmingModeChange(mode) }, label = { Text(mode.label) })
                                }
                            }
                            if (state.dimmingMode != DimmingMode.Auto) {
                                var rate by rememberSaveable(state.dimPercentPerMinute) { mutableStateOf(state.dimPercentPerMinute.toString()) }
                                val valid = rate.toIntOrNull()?.let { it in 1..100 } == true
                                OutlinedTextField(
                                    value = rate,
                                    onValueChange = { value ->
                                        if (value.length <= 3 && value.all(Char::isDigit)) {
                                            rate = value
                                            value.toIntOrNull()?.takeIf { it in 1..100 }?.let(onDimmingRateChange)
                                        }
                                    },
                                    label = { Text("Brightness reduction") },
                                    suffix = { Text("% / ${state.dimmingMode.unit}") },
                                    singleLine = true, isError = !valid,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Dim percentage per ${state.dimmingMode.unit}" },
                                )
                            }
                            Text(if (state.dimmingMode == DimmingMode.Auto) "Spreads the fade across the full timer."
                                else "Drops ${state.dimPercentPerMinute}% brightness per ${state.dimmingMode.unit}, then stays at minimum until the timer ends.",
                                fontSize = 10.sp, color = Muted, lineHeight = 15.sp, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    ControlCard {
                        SectionLabel("03", "On / off rhythm")
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Repeat light on and off", Modifier.weight(1f), fontSize = 12.sp, color = Muted)
                            Switch(state.rhythmEnabled, onRhythmChange, enabled = !state.isStopping,
                                colors = lampSwitchColors(),
                                modifier = Modifier.semantics { contentDescription = "Flashlight rhythm" })
                        }
                        if (state.rhythmEnabled) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(state.rhythmMode == RhythmMode.Auto, { onRhythmModeChange(RhythmMode.Auto) }, label = { Text("Auto · 1 sec") })
                                FilterChip(state.rhythmMode == RhythmMode.Custom, { onRhythmModeChange(RhythmMode.Custom) }, label = { Text("Custom") })
                            }
                            if (state.rhythmMode == RhythmMode.Custom) {
                                Text("Light on · ${state.rhythmOnSeconds} sec", fontSize = 12.sp)
                                Slider(state.rhythmOnSeconds.toFloat(), { onRhythmTimesChange(it.roundToInt(), state.rhythmOffSeconds) }, valueRange = 1f..60f,
                                    modifier = Modifier.semantics { contentDescription = "Rhythm light on delay" })
                                Text("Light off · ${state.rhythmOffSeconds} sec", fontSize = 12.sp)
                                Slider(state.rhythmOffSeconds.toFloat(), { onRhythmTimesChange(state.rhythmOnSeconds, it.roundToInt()) }, valueRange = 1f..60f,
                                    modifier = Modifier.semantics { contentDescription = "Rhythm light off delay" })
                            }
                            Text("${state.onSeconds} seconds on, ${state.offSeconds} seconds off. Minimum interval: 1 second. The timer still stops the whole session.",
                                fontSize = 10.sp, lineHeight = 16.sp, color = Muted)
                            Text("Rhythm keeps background controls active between pulses. In the notification's Custom timer input, use rhythm auto, rhythm off, or rhythm 2/3.",
                                modifier = Modifier.padding(top = 8.dp), fontSize = 10.sp, lineHeight = 16.sp, color = Muted)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    soundContent()
                    Spacer(Modifier.height(22.dp))
                    Text("${if (state.isOn) "Light stays on with your screen locked" else "Made for the moments you need a little light"}",
                        color = Muted, fontSize = 10.sp)
                }
            }
        }
        if (showCustomTimer) {
            CustomTimerDialog(state.timerSeconds, { showCustomTimer = false }) {
                onTimerChange(it)
                showCustomTimer = false
            }
        }
    }
}

@Composable
private fun PowerControl(state: FlashlightState, onToggle: () -> Unit) {
    val Background = MaterialTheme.colorScheme.background
    val Card = MaterialTheme.colorScheme.surface
    val Border = MaterialTheme.colorScheme.outline
    val Amber = MaterialTheme.colorScheme.primary
    val lit = state.isOn && (!state.rhythmEnabled || state.torchLit)
    val glow by animateFloatAsState(if (lit) .22f else .045f)
    val buttonColor by animateColorAsState(if (lit) Amber else Card)
    Box(Modifier.size(224.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Brush.radialGradient(listOf(Amber.copy(alpha = glow), Color.Transparent)), radius = size.minDimension / 2)
            drawCircle(Border, radius = size.minDimension * .44f, style = Stroke(1.dp.toPx()))
            repeat(48) { index ->
                val angle = index * PI / 24
                val ringRadius = size.minDimension * .44f
                val outer = Offset(center.x + cos(angle).toFloat() * ringRadius, center.y + sin(angle).toFloat() * ringRadius)
                val innerRadius = ringRadius - if (index % 4 == 0) 7.dp.toPx() else 3.dp.toPx()
                val inner = Offset(center.x + cos(angle).toFloat() * innerRadius, center.y + sin(angle).toFloat() * innerRadius)
                drawLine(if (state.isOn) Amber.copy(alpha = .55f) else Border, inner, outer, 1.dp.toPx())
            }
        }
        Surface(
            onClick = onToggle,
            enabled = state.hasFlash && !state.isStarting && !state.isStopping && (state.isAvailable || state.isOn),
            color = buttonColor,
            shape = CircleShape,
            border = androidx.compose.foundation.BorderStroke(1.dp, if (state.isOn) Amber else Border),
            modifier = Modifier.size(146.dp).semantics {
                contentDescription = if (state.isOn) "Turn flashlight off" else "Turn flashlight on"
                role = Role.Switch
                toggleableState = if (state.isOn) androidx.compose.ui.state.ToggleableState.On else androidx.compose.ui.state.ToggleableState.Off
            },
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Canvas(Modifier.size(42.dp)) {
                    val color = if (lit) Background else Amber
                    drawArc(color, -45f, 270f, false, topLeft = Offset(size.width * .13f, size.height * .15f),
                        size = androidx.compose.ui.geometry.Size(size.width * .74f, size.height * .74f),
                        style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                    drawLine(color, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height * .45f),
                        3.dp.toPx(), StrokeCap.Round)
                }
                Spacer(Modifier.height(12.dp))
                Text(when { state.isStopping -> "FADING OUT"; state.isStarting -> "FADING IN"; state.isOn && state.rhythmEnabled -> "STOP RHYTHM"; state.isOn -> "TURN OFF"; else -> "TURN ON" }, fontSize = 10.sp,
                    letterSpacing = 1.5.sp, fontWeight = FontWeight.SemiBold, color = if (lit) Background else Amber)
            }
        }
    }
}

@Composable
private fun ControlCard(content: @Composable ColumnScope.() -> Unit) {
    val Border = MaterialTheme.colorScheme.outline
    val Card = MaterialTheme.colorScheme.surface
    Column(Modifier.fillMaxWidth().border(1.dp, Border.copy(alpha = .6f), RoundedCornerShape(22.dp))
        .background(Card, RoundedCornerShape(22.dp)).padding(20.dp), content = content)
}

@Composable
private fun SectionLabel(number: String, label: String) {
    val Muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(number, color = Muted.copy(alpha = .65f), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.width(10.dp))
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun AdjustButton(description: String, label: String, enabled: Boolean, onClick: () -> Unit) {
    val White = MaterialTheme.colorScheme.onSurface
    val Muted = MaterialTheme.colorScheme.onSurfaceVariant
    OutlinedIconButton(onClick, enabled = enabled, modifier = Modifier.size(44.dp)
        .semantics { contentDescription = description }, shape = RoundedCornerShape(12.dp)) {
        Text(label, fontSize = 22.sp, color = if (enabled) White else Muted.copy(alpha = .4f))
    }
}

@Composable
private fun LampIcon(modifier: Modifier, color: Color) {
    Canvas(modifier) {
        val path = Path().apply {
            moveTo(size.width * .25f, size.height * .08f)
            lineTo(size.width * .75f, size.height * .08f)
            lineTo(size.width * .75f, size.height * .35f)
            lineTo(size.width * .6f, size.height * .52f)
            lineTo(size.width * .6f, size.height * .92f)
            lineTo(size.width * .4f, size.height * .92f)
            lineTo(size.width * .4f, size.height * .52f)
            lineTo(size.width * .25f, size.height * .35f)
            close()
        }
        drawPath(path, color, style = Stroke(1.6.dp.toPx()))
        drawLine(color, Offset(size.width * .25f, size.height * .28f), Offset(size.width * .75f, size.height * .28f), 1.6.dp.toPx())
    }
}

@Composable
internal fun CustomTimerDialog(initialSeconds: Int, onDismiss: () -> Unit, subject: String = "light", onConfirm: (Int) -> Unit) {
    val Muted = MaterialTheme.colorScheme.onSurfaceVariant
    var minutes by rememberSaveable { mutableStateOf((initialSeconds / 60).toString()) }
    var seconds by rememberSaveable { mutableStateOf((initialSeconds % 60).toString()) }
    val minuteCount = minutes.toIntOrNull() ?: 0
    val secondCount = seconds.toIntOrNull() ?: 0
    val total = minuteCount * 60 + secondCount
    val valid = secondCount in 0..59 && total in 1..86400
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("A little light, on your time.", fontSize = 21.sp) },
        text = {
            Column {
                Text("Automatically stop the $subject after:", color = Muted, fontSize = 13.sp)
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(minutes, { value -> if (value.length <= 4 && value.all(Char::isDigit)) minutes = value },
                        label = { Text("Minutes") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                    OutlinedTextField(seconds, { value -> if (value.length <= 2 && value.all(Char::isDigit)) seconds = value },
                        label = { Text("Seconds") }, singleLine = true, isError = secondCount > 59,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                Text(if (secondCount > 59) "Seconds must be between 0 and 59." else "Choose between 1 second and 24 hours.", color = Muted, fontSize = 11.sp)
                Text("If the light is on, this restarts the countdown.", color = Muted, fontSize = 11.sp)
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(total) }, enabled = valid) { Text("Set timer") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

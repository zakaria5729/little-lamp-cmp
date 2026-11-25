package com.zakaria5729.flash_table_lamp

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.*

@Composable
internal fun LampBackdrop(state: ScreenLampState, modifier: Modifier = Modifier, animate: Boolean = true) {
    val pattern = screenLightPattern(state)
    Crossfade(pattern, modifier.background(Color.Black), animationSpec = tween(colorTransitionMillis(state, animate)), label = "Lamp colors") { value ->
        Canvas(Modifier.fillMaxSize()) {
            val colors = value.colors.map(::Color)
            if (value.zones) {
                colors.forEachIndexed { index, color ->
                    if (value.layout == ZoneLayout.Stacked) {
                        val height = size.height / colors.size
                        drawRect(color, Offset(0f, index * height), Size(size.width, height + 1))
                    } else if (value.layout == ZoneLayout.Grid) {
                        val width = size.width / 2
                        val height = size.height / ((colors.size + 1) / 2)
                        drawRect(color, Offset(index % 2 * width, index / 2 * height), Size(width + 1, height + 1))
                    } else {
                        val width = size.width / colors.size
                        drawRect(color, Offset(index * width, 0f), Size(width + 1, size.height))
                    }
                }
            } else if (colors.size == 1) drawRect(colors.first())
            else {
                val radians = value.angle * PI / 180
                val direction = Offset(cos(radians).toFloat(), sin(radians).toFloat())
                val reach = (abs(size.width * direction.x) + abs(size.height * direction.y)) / 2
                drawRect(Brush.linearGradient(colors, center - direction * reach, center + direction * reach))
            }
        }
    }
}

@Composable
internal fun AdvancedColorControls(state: ScreenLampState, actions: ScreenLampActions) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    Text("Find your kind of glow.", fontSize = 28.sp, fontWeight = FontWeight.Medium)
    LampPreview(state, Modifier.fillMaxWidth().height(180.dp))
    LampCard {
        SettingToggle("Color gradient", "Blend two, three, or four editable colors", state.gradient, actions::setGradient)
        if (state.gradient) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (2..4).forEach { count -> FilterChip(state.gradientCount == count,
                    { actions.setGradientCount(count); selected = selected.coerceAtMost(count - 1) }, label = { Text("$count colors") }) }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(state.gradientCount) { index ->
                    FilterChip(selected == index, { selected = index }, label = { Text("Color ${index + 1}") })
                }
            }
        } else {
            selected = 0
            TextButton(onClick = { actions.setZonesEnabled(false); actions.setCycleColors(false) }, contentPadding = PaddingValues(0.dp)) { Text("Use solid screen color") }
        }
        val index = selected.coerceIn(0, if (state.gradient) state.gradientCount - 1 else 0)
        val colors = listOf(state.color, state.secondaryColor) + state.gradientExtras
        ColorEditor(colors[index], { actions.setGradientColor(index, it) })
        if (state.gradient) {
            Spacer(Modifier.height(16.dp))
            PanelHeading("Direction", "${state.gradientAngle}°")
            Slider(state.gradientAngle.toFloat(), { actions.setGradientAngle(it.roundToInt()) }, valueRange = 0f..360f,
                modifier = Modifier.semantics { contentDescription = "Gradient direction" })
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0 to "Across", 90 to "Down", 45 to "Diagonal").forEach { (angle, label) ->
                    FilterChip(state.gradientAngle == angle, { actions.setGradientAngle(angle) }, label = { Text(label) })
                }
            }
        }
    }
    LampCard {
        PanelHeading("Gradient presets", "")
        val presets = linkedMapOf(
            "Sunset" to listOf(0xFFFFB66E, 0xFFEE7C88, 0xFF8C78C9),
            "Ocean" to listOf(0xFF8FE3CF, 0xFF5BAFDC, 0xFF546AC2),
            "Forest" to listOf(0xFFB8DEA7, 0xFF68AB8C, 0xFF3D7479),
            "Aurora" to listOf(0xFF8AE0B8, 0xFF89BEF8, 0xFFAB8FDD, 0xFFE6A0DD),
        )
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            presets.forEach { (name, colors) ->
                AssistChip(onClick = {
                    actions.setGradient(true); actions.setGradientCount(colors.size)
                    colors.forEachIndexed { i, color -> actions.setGradientColor(i, color.toInt()) }
                    selected = 0
                }, label = { Text(name) })
            }
        }
    }
    ColorCycleControls(state, actions)
}

@Composable
internal fun ZoneControls(state: ScreenLampState, actions: ScreenLampActions) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    Text("More than one kind of light.", fontSize = 27.sp, fontWeight = FontWeight.Medium)
    Text("Give each part of the screen its own color and intensity.", fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    LampPreview(state, Modifier.fillMaxWidth().height(210.dp))
    LampCard {
        SettingToggle("Split-screen lighting zones", "Independent colors with a shared sleep timer", state.zonesEnabled, actions::setZonesEnabled)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(2, 3, 4, 6, 8).forEach { count -> FilterChip(state.zoneCount == count,
                { actions.setZoneCount(count); selected = selected.coerceAtMost(count - 1) }, label = { Text("$count zones") }) }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ZoneLayout.entries.filter { it != ZoneLayout.Grid || state.zoneCount >= 4 }.forEach { layout -> FilterChip(state.zoneLayout == layout,
                { actions.setZoneLayout(layout) }, label = { Text(layout.label) }) }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(state.zoneCount) { index -> FilterChip(selected == index, { selected = index }, label = { Text("Zone ${index + 1}") }) }
        }
        val index = selected.coerceIn(0, state.zoneCount - 1)
        val zone = state.zones[index]
        PanelHeading("Zone ${index + 1} brightness", "${zone.brightness}%")
        Slider(zone.brightness.toFloat(), { actions.setZoneBrightness(index, it.roundToInt()) }, valueRange = 0f..100f,
            modifier = Modifier.semantics { contentDescription = "Zone ${index + 1} brightness" })
        ColorEditor(zone.color, { actions.setZoneColor(index, it) }, "Zone ${index + 1} ")
        Text("Zone brightness dims its color. Screen brightness affects every zone.", fontSize = 10.sp,
            lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    ColorCycleControls(state, actions)
}

@Composable
internal fun ColorCycleControls(state: ScreenLampState, actions: ScreenLampActions) {
    LampCard {
        SettingToggle("Change colors automatically", "Cycle colors while the full-screen lamp is open", state.cycleColors, actions::setCycleColors)
        if (state.cycleColors) {
            if (state.zonesEnabled) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ZoneAnimation.entries.forEach { mode -> FilterChip(state.zoneAnimation == mode,
                        { actions.setZoneAnimation(mode) }, label = { Text(mode.label) }) }
                }
                Text(when (state.zoneAnimation) {
                    ZoneAnimation.Together -> "Every zone cycles through the selected palette together."
                    ZoneAnimation.Sequential -> "One zone lights up at a time. Other zones stay at 20% of their configured brightness. Each zone changes color on its turn."
                    ZoneAnimation.Random -> "Each zone independently picks a new random color at every interval."
                }, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!state.zonesEnabled || state.zoneAnimation != ZoneAnimation.Random) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CyclePalette.entries.forEach { palette -> FilterChip(state.cyclePalette == palette,
                    { actions.setCyclePalette(palette) }, label = { Text(palette.label) }) }
            }
            PanelHeading("Change every", "${state.cycleInterval} sec")
            Slider(state.cycleInterval.toFloat(), { actions.setCycleInterval(it.roundToInt()) }, valueRange = 1f..60f,
                modifier = Modifier.semantics { contentDescription = "Color change interval" })
            Text("Colors fade out and in smoothly, including at the 1-second minimum. Works with solid light, gradients, and zones. Your manual colors return when switched off.",
                fontSize = 10.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ColorEditor(color: Int, onColor: (Int) -> Unit, prefix: String = "") {
    val colors = listOf(0xFFFFD9A0, 0xFFFFF5E3, 0xFFD8E8FF, 0xFFFF7777, 0xFF9CDBAA, 0xFF9FADFF, 0xFFD3A5ED, 0xFFFFFFFF)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        colors.forEachIndexed { index, value ->
            val choice = value.toInt()
            Box(Modifier.size(35.dp).border(if (color == choice) 3.dp else 1.dp,
                if (color == choice) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape)
                .padding(4.dp).background(Color(choice), CircleShape).clickable { onColor(choice) }
                .semantics { contentDescription = "Choose ${prefix}color ${index + 1}" })
        }
    }
    listOf("Red" to 16, "Green" to 8, "Blue" to 0).forEach { (name, shift) ->
        val channel = (color ushr shift) and 255
        PanelHeading(name, channel.toString())
        Slider(channel.toFloat(), { value ->
            onColor((color and (255 shl shift).inv()) or (value.roundToInt().coerceIn(0, 255) shl shift))
        }, valueRange = 0f..255f, modifier = Modifier.semantics { contentDescription = "$prefix$name color channel" })
    }
    Text("#${(color and 0xFFFFFF).toString(16).uppercase().padStart(6, '0')}", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
}

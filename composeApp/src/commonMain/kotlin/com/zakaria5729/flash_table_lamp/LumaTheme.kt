package com.zakaria5729.flash_table_lamp

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

fun lumaColorScheme(dark: Boolean) = if (dark) darkColorScheme(
    primary = Color(0xFFF3CE7B), onPrimary = Color(0xFF19160F),
    secondary = Color(0xFFF3CE7B), secondaryContainer = Color(0xFF403723), onSecondaryContainer = Color(0xFFFFE5AE),
    background = Color(0xFF0C1015), surface = Color(0xFF171D25),
    surfaceContainer = Color(0xFF1C2430), onBackground = Color(0xFFF1F3F5),
    surfaceContainerLow = Color(0xFF141A21), surfaceContainerHigh = Color(0xFF202833), surfaceContainerHighest = Color(0xFF2A323D),
    onSurface = Color(0xFFF1F3F5), onSurfaceVariant = Color(0xFF929DAE), outline = Color(0xFF2A323D),
) else lightColorScheme(
    primary = Color(0xFF815A18), onPrimary = Color.White,
    secondary = Color(0xFF815A18), secondaryContainer = Color(0xFFF2DFC0), onSecondaryContainer = Color(0xFF533908),
    background = Color(0xFFF7F4EE), surface = Color(0xFFFFFCF7),
    surfaceContainer = Color(0xFFF0EBE2), onBackground = Color(0xFF22252A),
    surfaceContainerLow = Color(0xFFF4EFE7), surfaceContainerHigh = Color(0xFFEDE5D9), surfaceContainerHighest = Color(0xFFE4DCCF),
    onSurface = Color(0xFF22252A), onSurfaceVariant = Color(0xFF676B73), outline = Color(0xFFD9D3C8),
)

@Composable
internal fun lampSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
    checkedTrackColor = MaterialTheme.colorScheme.primary,
    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    uncheckedBorderColor = MaterialTheme.colorScheme.outline,
)

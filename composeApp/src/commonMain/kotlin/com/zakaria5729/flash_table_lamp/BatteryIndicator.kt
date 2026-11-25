package com.zakaria5729.flash_table_lamp

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun BatteryIndicator(percent: Int, charging: Boolean) {
    val level = percent.coerceIn(0, 100)
    val color = when {
        charging -> MaterialTheme.colorScheme.primary
        level <= 20 -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(Modifier.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
        .padding(horizontal = 10.dp, vertical = 7.dp)
        .semantics(mergeDescendants = true) {
            contentDescription = "Battery $level percent${if (charging) ", charging" else ""}"
        }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(Modifier.size(22.dp, 14.dp)) {
            val top = size.height * .14f
            val bodyWidth = size.width * .82f
            val bodyHeight = size.height * .72f
            drawRoundRect(color, Offset(0f, top), Size(bodyWidth, bodyHeight), CornerRadius(2.dp.toPx()), style = Stroke(1.dp.toPx()))
            val inset = 2.dp.toPx()
            val fillWidth = (bodyWidth - inset * 2) * level / 100f
            if (fillWidth > 0) drawRect(color.copy(alpha = if (charging) .3f else 1f), Offset(inset, top + inset), Size(fillWidth, bodyHeight - inset * 2))
            drawRoundRect(color, Offset(bodyWidth + 1.dp.toPx(), size.height * .35f), Size(2.dp.toPx(), size.height * .3f), CornerRadius(1.dp.toPx()))
            if (charging) {
                val bolt = Path().apply {
                    moveTo(bodyWidth * .57f, size.height * .2f)
                    lineTo(bodyWidth * .36f, size.height * .53f)
                    lineTo(bodyWidth * .58f, size.height * .53f)
                    lineTo(bodyWidth * .43f, size.height * .8f)
                }
                drawPath(bolt, color, style = Stroke(1.4.dp.toPx()))
            }
        }
        Text("$level%", fontSize = 11.sp, color = color)
    }
}

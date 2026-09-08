package com.syncwatch.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syncwatch.app.data.models.SyncStatus
import com.syncwatch.app.data.models.SyncTelemetry
import com.syncwatch.app.ui.theme.*

@Composable
fun TelemetryCard(
    telemetry: SyncTelemetry,
    modifier: Modifier = Modifier
) {
    val (statusText, statusColor) = when (telemetry.status) {
        SyncStatus.IN_SYNC -> "IN SYNC (0ms)" to SuccessGreen
        SyncStatus.SPEEDING_UP -> "LAG-FREE SPEED UP (${String.format("%.2f", telemetry.playbackSpeed)}x)" to AccentCyan
        SyncStatus.SLOWING_DOWN -> "LAG-FREE SLOW DOWN (${String.format("%.2f", telemetry.playbackSpeed)}x)" to WarningAmber
        SyncStatus.SEEKING -> "HARD SEEKING" to ErrorRed
        SyncStatus.BUFFERING -> "BUFFERING..." to WarningAmber
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(DarkSurface.copy(alpha = 0.90f))
            .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(statusColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = statusText,
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Wifi,
                        contentDescription = "Ping",
                        tint = TextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${telemetry.rttMs}ms",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                TelemetryMetric(
                    label = "Clock Offset",
                    value = "${if (telemetry.clockOffsetMs >= 0) "+" else ""}${telemetry.clockOffsetMs}ms"
                )
                TelemetryMetric(
                    label = "Drift (Δt)",
                    value = "${if (telemetry.driftMs >= 0) "+" else ""}${telemetry.driftMs}ms",
                    valueColor = if (Math.abs(telemetry.driftMs) < 100) SuccessGreen else WarningAmber
                )
                TelemetryMetric(
                    label = "Rate",
                    value = String.format("%.2fx", telemetry.playbackSpeed),
                    valueColor = HostGold
                )
            }
        }
    }
}

@Composable
private fun TelemetryMetric(
    label: String,
    value: String,
    valueColor: Color = TextPrimary
) {
    Column {
        Text(text = label, color = TextSecondary, fontSize = 10.sp)
        Text(text = value, color = valueColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

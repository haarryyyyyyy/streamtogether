package com.syncwatch.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syncwatch.app.data.models.Participant
import com.syncwatch.app.ui.theme.*

@Composable
fun ParticipantListDialog(
    participants: List<Participant>,
    isCurrentHost: Boolean,
    currentGuestId: String,
    onKickParticipant: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Watching Now (${participants.size})",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = TextSecondary)
                }
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(participants, key = { it.guestId }) { p ->
                    val isMe = p.guestId == currentGuestId
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (p.isBuffering) WarningAmber else SuccessGreen
                                        )
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = if (isMe) "${p.displayName} (You)" else p.displayName,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = TextPrimary
                                        )
                                        if (p.isHost) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "👑 Host",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = HostGold
                                            )
                                        }
                                    }
                                    if (p.isBuffering) {
                                        Text("Buffering...", fontSize = 11.sp, color = WarningAmber)
                                    } else {
                                        Text("In Sync", fontSize = 11.sp, color = TextMuted)
                                    }
                                }
                            }

                            if (isCurrentHost && !p.isHost && !isMe) {
                                IconButton(
                                    onClick = { onKickParticipant(p.guestId) },
                                    colors = IconButtonDefaults.iconButtonColors(contentColor = ErrorRed)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.PersonRemove,
                                        contentDescription = "Remove participant",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = AccentCyan)
            }
        }
    )
}

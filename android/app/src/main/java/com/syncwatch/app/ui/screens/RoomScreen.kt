package com.syncwatch.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.exoplayer.ExoPlayer
import com.syncwatch.app.data.models.*
import com.syncwatch.app.sync.LagFreeSyncEngine
import com.syncwatch.app.ui.components.ChatOverlay
import com.syncwatch.app.ui.components.ExoPlayerView
import com.syncwatch.app.ui.components.ParticipantListDialog
import com.syncwatch.app.ui.components.TelemetryCard
import com.syncwatch.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomScreen(
    roomState: RoomState,
    telemetry: SyncTelemetry,
    messages: List<ChatMessage>,
    connectionStatus: ConnectionStatus,
    myGuestId: String,
    onPlay: (Double) -> Unit,
    onPause: (Double) -> Unit,
    onSeek: (Double) -> Unit,
    onChangeMedia: (String, String) -> Unit,
    onSetControlMode: (String) -> Unit,
    onSetRoomLock: (Boolean) -> Unit,
    onKickParticipant: (String) -> Unit,
    onBufferingChanged: (Boolean) -> Unit,
    onSendMessage: (String) -> Unit,
    onUpdateSettings: (SyncSettings) -> Unit,
    onLeaveRoom: () -> Unit,
    syncEngine: LagFreeSyncEngine
) {
    val context = LocalContext.current
    var exoPlayerInstance by remember { mutableStateOf<ExoPlayer?>(null) }
    var isChatOpen by remember { mutableStateOf(false) }
    var isParticipantsOpen by remember { mutableStateOf(false) }
    var isHostSettingsOpen by remember { mutableStateOf(false) }
    var showTelemetry by remember { mutableStateOf(false) }
    var lastReadMessageCount by remember { mutableStateOf(0) }

    val unreadCount = remember(messages.size, isChatOpen) {
        if (isChatOpen) {
            lastReadMessageCount = messages.size
            0
        } else {
            (messages.size - lastReadMessageCount).coerceAtLeast(0)
        }
    }

    val isControlAllowed = roomState.isHost || roomState.controlMode == "SHARED"

    fun copyRoomCode() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("WatchTogether Room Code", roomState.pin)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Room Code copied: ${roomState.pin}", Toast.LENGTH_SHORT).show()
    }

    fun shareRoomCode() {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(
                Intent.EXTRA_TEXT,
                "🎬 Join my WatchTogether room: ${roomState.pin}\nWatching: ${roomState.mediaTitle}\nOpen WatchTogether and enter the code to join!"
            )
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share WatchTogether Room"))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CinemaDarkBg)
    ) {
        // Video Player Container
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Bar
            Surface(
                color = DarkSurface.copy(alpha = 0.92f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Back button & Room Code Chip
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onLeaveRoom) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Leave Room", tint = TextPrimary)
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = DarkSurfaceElevated,
                            border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(AccentCyan, AccentIndigo))),
                            modifier = Modifier.clickable { copyRoomCode() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = roomState.pin.ifEmpty { "ROOM" },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = AccentCyan
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Outlined.ContentCopy,
                                    contentDescription = "Copy",
                                    tint = AccentCyan,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }

                        IconButton(onClick = { shareRoomCode() }) {
                            Icon(Icons.Outlined.Share, contentDescription = "Share", tint = TextSecondary, modifier = Modifier.size(20.dp))
                        }
                    }

                    // Center / Right Actions
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Connection / Status Badge
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = when (connectionStatus) {
                                ConnectionStatus.CONNECTED, ConnectionStatus.IN_SYNC -> SuccessGreen.copy(alpha = 0.15f)
                                ConnectionStatus.RECONNECTING, ConnectionStatus.CONNECTING -> WarningAmber.copy(alpha = 0.15f)
                                ConnectionStatus.DISCONNECTED -> ErrorRed.copy(alpha = 0.15f)
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when (connectionStatus) {
                                                ConnectionStatus.CONNECTED, ConnectionStatus.IN_SYNC -> SuccessGreen
                                                ConnectionStatus.RECONNECTING, ConnectionStatus.CONNECTING -> WarningAmber
                                                ConnectionStatus.DISCONNECTED -> ErrorRed
                                            }
                                        )
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = when (connectionStatus) {
                                        ConnectionStatus.IN_SYNC -> "In Sync"
                                        ConnectionStatus.CONNECTED -> "Connected"
                                        ConnectionStatus.RECONNECTING -> "Reconnecting..."
                                        ConnectionStatus.CONNECTING -> "Connecting..."
                                        ConnectionStatus.DISCONNECTED -> "Offline"
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = when (connectionStatus) {
                                        ConnectionStatus.CONNECTED, ConnectionStatus.IN_SYNC -> SuccessGreen
                                        ConnectionStatus.RECONNECTING, ConnectionStatus.CONNECTING -> WarningAmber
                                        ConnectionStatus.DISCONNECTED -> ErrorRed
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        // Host Control Modal Trigger
                        if (roomState.isHost) {
                            IconButton(onClick = { isHostSettingsOpen = true }) {
                                Icon(Icons.Filled.AdminPanelSettings, contentDescription = "Host Controls", tint = HostGold)
                            }
                        }

                        // Participants Presence Button
                        IconButton(onClick = { isParticipantsOpen = true }) {
                            BadgedBox(
                                badge = {
                                    Badge(containerColor = AccentIndigo) {
                                        Text("${roomState.participants.size.coerceAtLeast(1)}")
                                    }
                                }
                            ) {
                                Icon(Icons.Outlined.People, contentDescription = "Participants", tint = TextPrimary)
                            }
                        }

                        // Chat Toggle Button with Unread Badge
                        IconButton(onClick = { isChatOpen = !isChatOpen }) {
                            BadgedBox(
                                badge = {
                                    if (unreadCount > 0) {
                                        Badge(containerColor = AccentCyan) {
                                            Text("$unreadCount", color = Color.Black, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            ) {
                                Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = "Chat", tint = TextPrimary)
                            }
                        }
                    }
                }
            }

            // Video Player Section
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.Black)
            ) {
                ExoPlayerView(
                    mediaUrl = roomState.mediaUrl,
                    onPlayerReady = { exo ->
                        exoPlayerInstance = exo
                        syncEngine.attachPlayer(exo)
                    },
                    onBufferingChanged = { isBuffering ->
                        onBufferingChanged(isBuffering)
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Host / Shared mode banner
                Surface(
                    color = DarkSurface.copy(alpha = 0.8f),
                    shape = RoundedCornerShape(bottomEnd = 10.dp),
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (roomState.controlMode == "HOST_ONLY") "👑 Host Only Control" else "👥 Shared Controls",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (roomState.controlMode == "HOST_ONLY") HostGold else AccentCyan
                        )
                        if (roomState.isLocked) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("• 🔒 Locked", fontSize = 11.sp, color = WarningAmber)
                        }
                    }
                }

                // Media Title Pill
                Surface(
                    color = DarkSurface.copy(alpha = 0.8f),
                    shape = RoundedCornerShape(bottomStart = 10.dp),
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Text(
                        text = roomState.mediaTitle,
                        fontSize = 11.sp,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                // Diagnostics Floating Toggle
                IconButton(
                    onClick = { showTelemetry = !showTelemetry },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Speed,
                        contentDescription = "Sync Diagnostics",
                        tint = if (showTelemetry) AccentCyan else TextMuted
                    )
                }
            }
        }

        // Diagnostics Card Overlay
        if (showTelemetry) {
            TelemetryCard(
                telemetry = telemetry,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 60.dp, end = 12.dp)
            )
        }

        // Slide-in Chat Overlay
        AnimatedVisibility(
            visible = isChatOpen,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            ChatOverlay(
                messages = messages,
                onSendMessage = onSendMessage,
                onClose = { isChatOpen = false }
            )
        }
    }

    // Participant List Dialog
    if (isParticipantsOpen) {
        ParticipantListDialog(
            participants = roomState.participants,
            isCurrentHost = roomState.isHost,
            currentGuestId = myGuestId,
            onKickParticipant = { targetId ->
                onKickParticipant(targetId)
            },
            onDismiss = { isParticipantsOpen = false }
        )
    }

    // Host Settings Dialog
    if (isHostSettingsOpen && roomState.isHost) {
        var selectedMode by remember { mutableStateOf(roomState.controlMode) }
        var isLocked by remember { mutableStateOf(roomState.isLocked) }
        var showChangeMediaDialog by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { isHostSettingsOpen = false },
            containerColor = DarkSurface,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("👑 Host Room Management", fontWeight = FontWeight.Bold, color = TextPrimary)
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("CONTROL PERMISSIONS", fontSize = 11.sp, color = AccentIndigo, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedMode = "HOST_ONLY" },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedMode == "HOST_ONLY",
                            onClick = { selectedMode = "HOST_ONLY" },
                            colors = RadioButtonDefaults.colors(selectedColor = AccentCyan)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text("Host Control Only", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                            Text("Only host can play, pause, seek", fontSize = 11.sp, color = TextMuted)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedMode = "SHARED" },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedMode == "SHARED",
                            onClick = { selectedMode = "SHARED" },
                            colors = RadioButtonDefaults.colors(selectedColor = AccentCyan)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text("Shared Controls", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                            Text("All participants can play/pause/seek", fontSize = 11.sp, color = TextMuted)
                        }
                    }

                    Divider(color = DarkBorder, modifier = Modifier.padding(vertical = 12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Lock Room", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                            Text("Prevent new members from joining", fontSize = 11.sp, color = TextMuted)
                        }
                        Switch(
                            checked = isLocked,
                            onCheckedChange = { isLocked = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = AccentCyan)
                        )
                    }

                    Divider(color = DarkBorder, modifier = Modifier.padding(vertical = 12.dp))

                    OutlinedButton(
                        onClick = {
                            isHostSettingsOpen = false
                            showChangeMediaDialog = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Filled.VideoLibrary, contentDescription = null, tint = AccentCyan)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Switch Movie / Stream", color = TextPrimary, fontSize = 13.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSetControlMode(selectedMode)
                        onSetRoomLock(isLocked)
                        isHostSettingsOpen = false
                        Toast.makeText(context, "Host settings applied", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentCyan)
                ) {
                    Text("Apply Changes", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { isHostSettingsOpen = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )

        if (showChangeMediaDialog) {
            var selectedMovie by remember { mutableStateOf(SAMPLE_MOVIES[0]) }
            var isCustom by remember { mutableStateOf(false) }
            var customUrl by remember { mutableStateOf("") }
            var customTitle by remember { mutableStateOf("") }

            AlertDialog(
                onDismissRequest = { showChangeMediaDialog = false },
                containerColor = DarkSurface,
                title = { Text("Switch Media Stream", color = TextPrimary, fontWeight = FontWeight.Bold) },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SAMPLE_MOVIES.forEach { m ->
                            val isSel = !isCustom && selectedMovie.id == m.id
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable {
                                        isCustom = false
                                        selectedMovie = m
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSel) AccentIndigo.copy(alpha = 0.3f) else DarkSurfaceElevated
                                )
                            ) {
                                Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(selected = isSel, onClick = { isCustom = false; selectedMovie = m })
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(m.title, fontSize = 12.sp, color = TextPrimary)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { isCustom = true },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isCustom) AccentIndigo.copy(alpha = 0.3f) else DarkSurfaceElevated
                            )
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(selected = isCustom, onClick = { isCustom = true })
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Custom Video URL", fontSize = 12.sp, color = TextPrimary)
                                }
                                if (isCustom) {
                                    OutlinedTextField(
                                        value = customTitle,
                                        onValueChange = { customTitle = it },
                                        label = { Text("Title") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    OutlinedTextField(
                                        value = customUrl,
                                        onValueChange = { customUrl = it },
                                        label = { Text("Stream URL") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val url = if (isCustom) customUrl.trim() else selectedMovie.url
                            val title = if (isCustom) (if (customTitle.isBlank()) "Stream" else customTitle.trim()) else selectedMovie.title
                            if (url.isNotBlank()) {
                                onChangeMedia(url, title)
                                showChangeMediaDialog = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentCyan)
                    ) {
                        Text("Switch", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showChangeMediaDialog = false }) {
                        Text("Cancel", color = TextSecondary)
                    }
                }
            )
        }
    }
}

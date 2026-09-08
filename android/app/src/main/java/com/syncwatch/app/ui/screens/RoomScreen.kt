package com.syncwatch.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.exoplayer.ExoPlayer
import com.syncwatch.app.data.models.*
import com.syncwatch.app.sync.LagFreeSyncEngine
import com.syncwatch.app.ui.components.ChatOverlay
import com.syncwatch.app.ui.components.ExoPlayerView
import com.syncwatch.app.ui.components.ParticipantListDialog
import com.syncwatch.app.ui.components.TelemetryCard
import com.syncwatch.app.ui.theme.*
import com.syncwatch.app.utils.MediaUtils
import java.text.SimpleDateFormat
import java.util.*

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
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    var exoPlayerInstance by remember { mutableStateOf<ExoPlayer?>(null) }
    var isParticipantsOpen by remember { mutableStateOf(false) }
    var isHostSettingsOpen by remember { mutableStateOf(false) }
    var showTelemetry by remember { mutableStateOf(false) }
    var showSubtitleDialog by remember { mutableStateOf(false) }
    var areSubtitlesEnabled by remember { mutableStateOf(true) }

    var isLandscapeChatOpen by remember { mutableStateOf(false) }
    var chatInputText by remember { mutableStateOf("") }
    val chatListState = rememberLazyListState()

    // Auto-scroll to latest chat message
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            chatListState.animateScrollToItem(messages.size - 1)
        }
    }

    fun toggleSubtitles() {
        val player = exoPlayerInstance ?: return
        val currentParams = player.trackSelectionParameters
        val currentlyDisabled = currentParams.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)

        if (currentlyDisabled) {
            player.trackSelectionParameters = currentParams.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setPreferredTextLanguage("en")
                .build()
            areSubtitlesEnabled = true
            Toast.makeText(context, "Subtitles Enabled", Toast.LENGTH_SHORT).show()
        } else {
            player.trackSelectionParameters = currentParams.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
            areSubtitlesEnabled = false
            Toast.makeText(context, "Subtitles Disabled", Toast.LENGTH_SHORT).show()
        }
    }

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

    if (isLandscape) {
        // ==========================================
        // LANDSCAPE MODE: Fullscreen Theater View
        // ==========================================
        Box(
            modifier = Modifier
                .fillMaxSize()
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
                onUserPlayPauseChanged = { isPlaying, posSec ->
                    if (isPlaying) onPlay(posSec) else onPause(posSec)
                },
                onUserSeek = { posSec ->
                    onSeek(posSec)
                },
                modifier = Modifier.fillMaxSize()
            )

            // Top overlay bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent)
                        )
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onLeaveRoom) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                    Text(
                        text = roomState.mediaTitle,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 240.dp)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { toggleSubtitles() }) {
                        Icon(
                            imageVector = if (areSubtitlesEnabled) Icons.Filled.ClosedCaption else Icons.Outlined.ClosedCaptionDisabled,
                            contentDescription = "Subtitles",
                            tint = if (areSubtitlesEnabled) AccentCyan else TextMuted
                        )
                    }
                    IconButton(onClick = { isParticipantsOpen = true }) {
                        Icon(Icons.Outlined.People, contentDescription = "Participants", tint = TextPrimary)
                    }
                    IconButton(onClick = { isLandscapeChatOpen = !isLandscapeChatOpen }) {
                        Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = "Chat", tint = TextPrimary)
                    }
                }
            }

            // Slide-in Landscape Chat
            AnimatedVisibility(
                visible = isLandscapeChatOpen,
                enter = slideInHorizontally(initialOffsetX = { it }),
                exit = slideOutHorizontally(targetOffsetX = { it }),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(320.dp)
            ) {
                ChatOverlay(
                    messages = messages,
                    onSendMessage = onSendMessage,
                    onClose = { isLandscapeChatOpen = false }
                )
            }
        }
    } else {
        // =========================================================================
        // PORTRAIT (VERTICAL) MODE: YouTube-Style Layout with Inline Live Chat Below
        // =========================================================================
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(CinemaDarkBg)
        ) {
            // 1. YouTube-style Top Video Container (16:9 Aspect Ratio)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
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
                    onUserPlayPauseChanged = { isPlaying, posSec ->
                        if (isPlaying) onPlay(posSec) else onPause(posSec)
                    },
                    onUserSeek = { posSec ->
                        onSeek(posSec)
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Top Floating Back & Status Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onLeaveRoom) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Leave Room",
                            tint = TextPrimary
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Subtitle Quick CC Button
                        IconButton(onClick = { toggleSubtitles() }) {
                            Icon(
                                imageVector = if (areSubtitlesEnabled) Icons.Filled.ClosedCaption else Icons.Outlined.ClosedCaptionDisabled,
                                contentDescription = "Subtitles",
                                tint = if (areSubtitlesEnabled) AccentCyan else Color.White.copy(alpha = 0.6f)
                            )
                        }

                        // Diagnostics Toggle
                        IconButton(onClick = { showTelemetry = !showTelemetry }) {
                            Icon(
                                imageVector = Icons.Outlined.Speed,
                                contentDescription = "Telemetry",
                                tint = if (showTelemetry) AccentCyan else Color.White.copy(alpha = 0.6f)
                            )
                        }
                    }
                }

                // Diagnostics Card
                if (showTelemetry) {
                    TelemetryCard(
                        telemetry = telemetry,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 48.dp, end = 8.dp)
                    )
                }
            }

            // 2. Video Title & Room Action Header Bar
            Surface(
                color = DarkSurface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    // Movie Title extracted from link
                    Text(
                        text = roomState.mediaTitle,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Chips & Quick Actions Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Room Code Chip
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = DarkSurfaceElevated,
                            modifier = Modifier.clickable { copyRoomCode() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = roomState.pin.ifEmpty { "ROOM" },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = AccentCyan
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Outlined.ContentCopy,
                                    contentDescription = "Copy",
                                    tint = AccentCyan,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }

                        // Share
                        IconButton(
                            onClick = { shareRoomCode() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Outlined.Share, contentDescription = "Share", tint = TextSecondary, modifier = Modifier.size(16.dp))
                        }

                        // Status Badge
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = when (connectionStatus) {
                                ConnectionStatus.CONNECTED, ConnectionStatus.IN_SYNC -> SuccessGreen.copy(alpha = 0.15f)
                                ConnectionStatus.RECONNECTING, ConnectionStatus.CONNECTING -> WarningAmber.copy(alpha = 0.15f)
                                ConnectionStatus.DISCONNECTED -> ErrorRed.copy(alpha = 0.15f)
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
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
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = when (connectionStatus) {
                                        ConnectionStatus.CONNECTED, ConnectionStatus.IN_SYNC -> SuccessGreen
                                        ConnectionStatus.RECONNECTING, ConnectionStatus.CONNECTING -> WarningAmber
                                        ConnectionStatus.DISCONNECTED -> ErrorRed
                                    }
                                )
                            }
                        }

                        // Participants Count
                        IconButton(
                            onClick = { isParticipantsOpen = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            BadgedBox(
                                badge = {
                                    Badge(containerColor = AccentIndigo) {
                                        Text("${roomState.participants.size.coerceAtLeast(1)}")
                                    }
                                }
                            ) {
                                Icon(Icons.Outlined.People, contentDescription = "Participants", tint = TextPrimary, modifier = Modifier.size(18.dp))
                            }
                        }

                        // Host Settings (if Host)
                        if (roomState.isHost) {
                            IconButton(
                                onClick = { isHostSettingsOpen = true },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Filled.AdminPanelSettings, contentDescription = "Host Controls", tint = HostGold, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = DarkBorder)

            // 3. YouTube-Style Embedded Inline Chat (Takes all remaining screen space)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(DarkSurfaceElevated.copy(alpha = 0.5f))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                // Live Chat Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(AccentCyan)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Live Chat",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "(${messages.size})",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }

                    Text(
                        text = if (roomState.controlMode == "HOST_ONLY") "👑 Host Control" else "👥 Shared Control",
                        fontSize = 11.sp,
                        color = if (roomState.controlMode == "HOST_ONLY") HostGold else AccentCyan
                    )
                }

                // Chat Messages List
                LazyColumn(
                    state = chatListState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(messages, key = { it.id }) { msg ->
                        val timeStr = remember(msg.timestamp) {
                            val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
                            sdf.format(Date(msg.timestamp))
                        }

                        if (msg.isSystem) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Surface(
                                    color = DarkSurface.copy(alpha = 0.7f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = msg.text,
                                        fontSize = 10.sp,
                                        color = TextSecondary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(DarkSurface, shape = RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = msg.sender,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (msg.isHost) HostGold else AccentCyan
                                        )
                                        if (msg.isHost) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("👑", fontSize = 10.sp)
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = timeStr,
                                            fontSize = 9.sp,
                                            color = TextMuted
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = msg.text,
                                        fontSize = 12.sp,
                                        color = TextPrimary
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Inline Chat Input Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkSurface, shape = RoundedCornerShape(10.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = chatInputText,
                        onValueChange = { chatInputText = it },
                        placeholder = { Text("Send a chat...", fontSize = 12.sp, color = TextMuted) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (chatInputText.isNotBlank()) {
                                    onSendMessage(chatInputText.trim())
                                    chatInputText = ""
                                }
                            }
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    IconButton(
                        onClick = {
                            if (chatInputText.isNotBlank()) {
                                onSendMessage(chatInputText.trim())
                                chatInputText = ""
                            }
                        },
                        colors = IconButtonDefaults.iconButtonColors(contentColor = AccentCyan)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", modifier = Modifier.size(18.dp))
                    }
                }
            }
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

    // Host Settings Dialog (Change Media, Toggle Lock, Control Mode)
    if (isHostSettingsOpen && roomState.isHost) {
        var selectedMode by remember { mutableStateOf(roomState.controlMode) }
        var isLocked by remember { mutableStateOf(roomState.isLocked) }
        var showChangeMediaDialog by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { isHostSettingsOpen = false },
            containerColor = DarkSurface,
            title = {
                Text("👑 Host Room Management", fontWeight = FontWeight.Bold, color = TextPrimary)
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

                    HorizontalDivider(color = DarkBorder, modifier = Modifier.padding(vertical = 12.dp))

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

                    HorizontalDivider(color = DarkBorder, modifier = Modifier.padding(vertical = 12.dp))

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
                        Text("Change Movie Stream", color = TextPrimary, fontSize = 13.sp)
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

        // Switch Media Dialog (Paste URL or Pick Local File)
        if (showChangeMediaDialog) {
            var selectedTab by remember { mutableStateOf(0) }
            var newUrlInput by remember { mutableStateOf("") }
            var newFileUri by remember { mutableStateOf<Uri?>(null) }
            var newFileName by remember { mutableStateOf("") }

            val changeFilePicker = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.GetContent()
            ) { uri: Uri? ->
                if (uri != null) {
                    newFileUri = uri
                    newFileName = MediaUtils.getFileNameFromUri(context, uri)
                }
            }

            val detectedTitle = remember(selectedTab, newUrlInput, newFileName) {
                if (selectedTab == 0) {
                    if (newUrlInput.isNotBlank()) MediaUtils.extractTitleFromUrl(newUrlInput) else "Movie Stream"
                } else {
                    if (newFileName.isNotBlank()) newFileName else "Local Movie"
                }
            }

            AlertDialog(
                onDismissRequest = { showChangeMediaDialog = false },
                containerColor = DarkSurface,
                title = { Text("Change Movie Stream", color = TextPrimary, fontWeight = FontWeight.Bold) },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        TabRow(
                            selectedTabIndex = selectedTab,
                            containerColor = DarkSurfaceElevated,
                            contentColor = AccentCyan,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .padding(bottom = 12.dp)
                        ) {
                            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("🌐 Paste Link") })
                            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("📁 Local File") })
                        }

                        if (selectedTab == 0) {
                            OutlinedTextField(
                                value = newUrlInput,
                                onValueChange = { newUrlInput = it },
                                label = { Text("New Video URL") },
                                placeholder = { Text("https://example.com/movie.mp4") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            OutlinedButton(
                                onClick = { changeFilePicker.launch("video/*") },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(if (newFileName.isNotBlank()) newFileName else "Select Local Video")
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text("New Title: $detectedTitle", fontSize = 12.sp, color = AccentCyan, fontWeight = FontWeight.Bold)
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val finalUrl = if (selectedTab == 0) newUrlInput.trim() else newFileUri?.toString() ?: ""
                            if (finalUrl.isNotBlank()) {
                                onChangeMedia(finalUrl, detectedTitle)
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

package com.syncwatch.app.ui.screens

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import com.syncwatch.app.MainActivity
import com.syncwatch.app.data.models.*
import com.syncwatch.app.sync.LagFreeSyncEngine
import com.syncwatch.app.ui.components.ChatOverlay
import com.syncwatch.app.ui.components.ExoPlayerView
import com.syncwatch.app.ui.components.ParticipantListDialog
import com.syncwatch.app.ui.theme.*
import com.syncwatch.app.utils.MediaUtils
import java.util.Locale
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun RoomScreen(
    roomState: RoomState,
    telemetry: SyncTelemetry,
    messages: List<ChatMessage>,
    connectionStatus: ConnectionStatus,
    myGuestId: String,
    localHostFileUri: Uri? = null,
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
    val canControlPlayback = roomState.isHost || roomState.controlMode == "SHARED"

    val activity = context as? Activity
    fun toggleOrientation() {
        if (isLandscape) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }

    var showExitConfirmDialog by remember { mutableStateOf(false) }
    var showChangeMediaDialog by remember { mutableStateOf(false) }
    var showAudioTrackDialog by remember { mutableStateOf(false) }
    var isParticipantsOpen by remember { mutableStateOf(false) }
    var isHostSettingsOpen by remember { mutableStateOf(false) }
    var areSubtitlesEnabled by remember { mutableStateOf(true) }

    var isLandscapeChatOpen by remember { mutableStateOf(false) }
    var chatInputText by remember { mutableStateOf("") }
    val chatListState = rememberLazyListState()

    // Background upload state for host sharing local video
    val uploadState by com.syncwatch.app.data.network.StreamUploadManager.uploadState.collectAsState()

    // Hardware & Gesture Back Button in Landscape returns to Portrait; in Portrait prompts exit
    BackHandler(enabled = true) {
        if (isLandscape) {
            toggleOrientation()
        } else {
            showExitConfirmDialog = true
        }
    }

    val effectiveMediaUrl = remember(roomState.mediaUrl, localHostFileUri) {
        if (roomState.isHost && localHostFileUri != null) {
            localHostFileUri.toString()
        } else {
            roomState.mediaUrl
        }
    }

    var tracksState by remember { mutableStateOf(Tracks.EMPTY) }

    // Hoisted ExoPlayer: Preserved across configuration/orientation changes
    val exoPlayer = remember(effectiveMediaUrl) {
        val renderersFactory = DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            .setEnableDecoderFallback(true)

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 15_000,
                /* maxBufferMs = */ 50_000,
                /* bufferForPlaybackMs = */ 1_000,
                /* bufferForPlaybackAfterRebufferMs = */ 2_000
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        ExoPlayer.Builder(context, renderersFactory)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setSeekParameters(SeekParameters.CLOSEST_SYNC)
            .setLoadControl(loadControl)
            .build().apply {
                volume = 1.0f
                playWhenReady = false
                trackSelectionParameters = trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                    .build()

                if (effectiveMediaUrl.isNotEmpty()) {
                    val uri = Uri.parse(effectiveMediaUrl)
                    val mediaItemBuilder = MediaItem.Builder().setUri(uri)

                    if (effectiveMediaUrl.endsWith(".m3u8", ignoreCase = true) || effectiveMediaUrl.contains(".m3u8?", ignoreCase = true)) {
                        mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
                    } else if (effectiveMediaUrl.endsWith(".mpd", ignoreCase = true) || effectiveMediaUrl.contains(".mpd?", ignoreCase = true)) {
                        mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_MPD)
                    }

                    setMediaItem(mediaItemBuilder.build())
                    prepare()
                }
            }
    }

    // Attach listeners and syncEngine to the hoisted player
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                val isBuffering = playbackState == Player.STATE_BUFFERING
                onBufferingChanged(isBuffering)
            }

            override fun onTracksChanged(tracks: Tracks) {
                tracksState = tracks
                // Automatically ensure the first available audio track is selected
                val audioGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
                if (audioGroups.isNotEmpty() && !audioGroups.any { it.isSelected }) {
                    val firstGroup = audioGroups.first()
                    exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
                        .setOverrideForType(TrackSelectionOverride(firstGroup.mediaTrackGroup, listOf(0)))
                        .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                        .build()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                error.printStackTrace()
            }
        }

        exoPlayer.addListener(listener)
        syncEngine.attachPlayer(exoPlayer)

        onDispose {
            exoPlayer.removeListener(listener)
            syncEngine.detachPlayer()
            exoPlayer.release()
        }
    }

    // State for Custom Video Controls
    var isCurrentlyPlaying by remember { mutableStateOf(false) }
    var currentPosSec by remember { mutableStateOf(0.0) }
    var totalDurationSec by remember { mutableStateOf(0.0) }
    var isUserScrubbing by remember { mutableStateOf(false) }
    var scrubPositionSec by remember { mutableStateOf(0f) }
    var controlsVisible by remember { mutableStateOf(true) }
    var lastInteractionTime by remember { mutableStateOf(System.currentTimeMillis()) }

    // Realtime progress ticker and auto-hide timer
    LaunchedEffect(exoPlayer) {
        while (true) {
            isCurrentlyPlaying = exoPlayer.isPlaying
            if (!isUserScrubbing) {
                currentPosSec = exoPlayer.currentPosition / 1000.0
            }
            val dur = exoPlayer.duration
            totalDurationSec = if (dur > 0) dur / 1000.0 else 0.0

            // Auto-hide controls after 3.5 seconds of inactivity if playing
            if (controlsVisible && isCurrentlyPlaying && (System.currentTimeMillis() - lastInteractionTime > 3500)) {
                controlsVisible = false
            }

            delay(250)
        }
    }

    fun triggerUserInteraction() {
        controlsVisible = true
        lastInteractionTime = System.currentTimeMillis()
    }

    fun handlePlayPauseToggle() {
        triggerUserInteraction()
        if (!canControlPlayback) {
            Toast.makeText(context, "Only host can control playback in Host Control mode", Toast.LENGTH_SHORT).show()
            return
        }
        val targetPlay = !exoPlayer.isPlaying
        val pos = exoPlayer.currentPosition / 1000.0
        if (targetPlay) {
            exoPlayer.play()
            onPlay(pos)
        } else {
            exoPlayer.pause()
            onPause(pos)
        }
    }

    fun handleSeekTo(targetSec: Double) {
        triggerUserInteraction()
        if (!canControlPlayback) {
            Toast.makeText(context, "Only host can seek in Host Control mode", Toast.LENGTH_SHORT).show()
            return
        }
        exoPlayer.seekTo((targetSec * 1000).toLong())
        onSeek(targetSec)
    }

    fun handleReplay10() {
        val target = (exoPlayer.currentPosition / 1000.0 - 10.0).coerceAtLeast(0.0)
        handleSeekTo(target)
    }

    fun handleForward10() {
        val dur = if (totalDurationSec > 0) totalDurationSec else Double.MAX_VALUE
        val target = (exoPlayer.currentPosition / 1000.0 + 10.0).coerceAtMost(dur)
        handleSeekTo(target)
    }

    fun toggleSubtitles() {
        triggerUserInteraction()
        val currentParams = exoPlayer.trackSelectionParameters
        val currentlyDisabled = currentParams.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)

        if (currentlyDisabled) {
            exoPlayer.trackSelectionParameters = currentParams.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setPreferredTextLanguage("en")
                .build()
            areSubtitlesEnabled = true
            Toast.makeText(context, "Subtitles Enabled", Toast.LENGTH_SHORT).show()
        } else {
            exoPlayer.trackSelectionParameters = currentParams.buildUpon()
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

    fun formatTime(seconds: Double): String {
        val totalSec = Math.max(0L, seconds.toLong())
        val hours = totalSec / 3600
        val minutes = (totalSec % 3600) / 60
        val secs = totalSec % 60
        return if (hours > 0) {
            String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, secs)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", minutes, secs)
        }
    }

    // =========================================================================
    // CUSTOM DESIGNED VIDEO CONTROLS OVERLAY COMPOSABLE
    // =========================================================================
    @Composable
    fun CustomVideoPlayerControlsOverlay(
        modifier: Modifier = Modifier,
        isLandscapeMode: Boolean
    ) {
        val screenWidthDp = LocalConfiguration.current.screenWidthDp
        val isCompactScreen = screenWidthDp < 360
        val isLargeScreen = screenWidthDp >= 600

        val centerBtnSpacing = if (isCompactScreen) 16.dp else if (isLargeScreen) 44.dp else 28.dp
        val playBtnSize = if (isCompactScreen) 50.dp else if (isLargeScreen) 74.dp else 64.dp
        val playIconSize = if (isCompactScreen) 28.dp else if (isLargeScreen) 42.dp else 36.dp
        val jumpBtnSize = if (isCompactScreen) 36.dp else if (isLargeScreen) 50.dp else 44.dp
        val jumpIconSize = if (isCompactScreen) 20.dp else if (isLargeScreen) 28.dp else 24.dp
        val maxTitleWidth = if (isLandscapeMode) (screenWidthDp * 0.42f).dp else (screenWidthDp * 0.45f).dp

        Box(
            modifier = modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    controlsVisible = !controlsVisible
                    if (controlsVisible) lastInteractionTime = System.currentTimeMillis()
                }
        ) {
            ExoPlayerView(
                exoPlayer = exoPlayer,
                modifier = Modifier.fillMaxSize()
            )

            // Animated Overlay (Top Bar, Center Buttons, Bottom Scrubber Bar)
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.75f),
                                    Color.Black.copy(alpha = 0.25f),
                                    Color.Black.copy(alpha = 0.85f)
                                )
                            )
                        )
                ) {
                    // TOP BAR
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .padding(horizontal = if (isCompactScreen) 8.dp else 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = {
                                if (isLandscapeMode) toggleOrientation() else showExitConfirmDialog = true
                            }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = TextPrimary
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = roomState.mediaTitle,
                                fontSize = if (isCompactScreen) 12.sp else 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = maxTitleWidth)
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Audio Tracks button
                            IconButton(onClick = {
                                triggerUserInteraction()
                                showAudioTrackDialog = true
                            }) {
                                Icon(Icons.Filled.Audiotrack, contentDescription = "Audio Tracks", tint = AccentCyan)
                            }

                            // Subtitle CC button
                            IconButton(onClick = { toggleSubtitles() }) {
                                Icon(
                                    imageVector = if (areSubtitlesEnabled) Icons.Filled.ClosedCaption else Icons.Outlined.ClosedCaptionDisabled,
                                    contentDescription = "Subtitles",
                                    tint = if (areSubtitlesEnabled) AccentCyan else TextMuted
                                )
                            }

                            // Fullscreen Rotation Toggle
                            IconButton(onClick = { toggleOrientation() }) {
                                Icon(
                                    imageVector = if (isLandscapeMode) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                                    contentDescription = "Toggle Fullscreen",
                                    tint = TextPrimary
                                )
                            }

                            if (isLandscapeMode) {
                                IconButton(onClick = { isParticipantsOpen = true }) {
                                    Icon(Icons.Outlined.People, contentDescription = "Participants", tint = TextPrimary)
                                }
                                IconButton(onClick = { isLandscapeChatOpen = !isLandscapeChatOpen }) {
                                    Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = "Chat", tint = TextPrimary)
                                }
                            }
                        }
                    }

                    // CENTER PLAYBACK CONTROLS (Rewind 10s | Play/Pause | Forward 10s)
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(centerBtnSpacing),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Replay 10s
                        Box(
                            modifier = Modifier
                                .size(jumpBtnSize)
                                .clip(CircleShape)
                                .background(DarkSurface.copy(alpha = 0.6f))
                                .clickable { handleReplay10() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Replay10,
                                contentDescription = "Replay 10s",
                                tint = TextPrimary,
                                modifier = Modifier.size(jumpIconSize)
                            )
                        }

                        // Play / Pause Radiant Center Button
                        Box(
                            modifier = Modifier
                                .size(playBtnSize)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(AccentCyan, AccentIndigo)
                                    )
                                )
                                .border(2.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                                .clickable { handlePlayPauseToggle() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isCurrentlyPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = Color.Black,
                                modifier = Modifier.size(playIconSize)
                            )
                        }

                        // Forward 10s
                        Box(
                            modifier = Modifier
                                .size(jumpBtnSize)
                                .clip(CircleShape)
                                .background(DarkSurface.copy(alpha = 0.6f))
                                .clickable { handleForward10() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Forward10,
                                contentDescription = "Forward 10s",
                                tint = TextPrimary,
                                modifier = Modifier.size(jumpIconSize)
                            )
                        }
                    }

                    // BOTTOM BAR (Current Time | Slider Scrubber | Duration | PiP)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = if (isCompactScreen) 8.dp else 14.dp, vertical = 6.dp)
                    ) {
                        // Slider Scrubber
                        val displayPos = if (isUserScrubbing) scrubPositionSec.toDouble() else currentPosSec
                        val maxDur = totalDurationSec.coerceAtLeast(1.0)

                        Slider(
                            value = (displayPos / maxDur).toFloat().coerceIn(0f, 1f),
                            onValueChange = { frac ->
                                triggerUserInteraction()
                                isUserScrubbing = true
                                scrubPositionSec = (frac * maxDur).toFloat()
                            },
                            onValueChangeFinished = {
                                isUserScrubbing = false
                                handleSeekTo(scrubPositionSec.toDouble())
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = AccentCyan,
                                activeTrackColor = AccentCyan,
                                inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(24.dp)
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = formatTime(displayPos),
                                    fontSize = if (isCompactScreen) 11.sp else 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = " / ${formatTime(totalDurationSec)}",
                                    fontSize = if (isCompactScreen) 11.sp else 12.sp,
                                    color = TextSecondary
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Picture in Picture (PiP) button
                                IconButton(
                                    onClick = {
                                        val mainActivity = context as? MainActivity
                                        mainActivity?.enterPipMode()
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.PictureInPictureAlt,
                                        contentDescription = "Picture in Picture",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val screenWidth = LocalConfiguration.current.screenWidthDp
    val responsiveChatWidth = (screenWidth * 0.35f).coerceIn(280f, 440f).dp

    if (isLandscape) {
        // ==========================================
        // LANDSCAPE MODE: Fullscreen Theater View
        // ==========================================
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            CustomVideoPlayerControlsOverlay(
                isLandscapeMode = true,
                modifier = Modifier.fillMaxSize()
            )

            // Slide-in Landscape Chat
            AnimatedVisibility(
                visible = isLandscapeChatOpen,
                enter = slideInHorizontally(initialOffsetX = { it }),
                exit = slideOutHorizontally(targetOffsetX = { it }),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(responsiveChatWidth)
                    .imePadding()
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
        // PORTRAIT (VERTICAL) MODE: YouTube-Style Fixed Player & Bottom Live Chat
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
                CustomVideoPlayerControlsOverlay(
                    isLandscapeMode = false,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Stream Upload & Sharing Status Banner
            if (roomState.isHost && uploadState is com.syncwatch.app.data.network.UploadState.Uploading) {
                val upState = uploadState as com.syncwatch.app.data.network.UploadState.Uploading
                Surface(
                    color = DarkSurfaceElevated,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(8.dp),
                    border = CardDefaults.outlinedCardBorder().copy(brush = Brush.horizontalGradient(listOf(AccentCyan, AccentIndigo)))
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.CloudUpload, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Sharing movie with guests...", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            }
                            Text("${(upState.progress * 100).toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AccentCyan)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { upState.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = AccentCyan,
                            trackColor = Color.White.copy(alpha = 0.1f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${upState.bytesUploaded / (1024 * 1024)} MB / ${upState.totalBytes / (1024 * 1024)} MB • Live stream relayed to room guests",
                            fontSize = 10.sp,
                            color = TextMuted
                        )
                    }
                }
            } else if (roomState.isHost && uploadState is com.syncwatch.app.data.network.UploadState.Success) {
                Surface(
                    color = DarkSurfaceElevated,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Movie shared with room • Live streaming to guests", fontSize = 11.sp, color = TextSecondary)
                    }
                }
            } else if (!roomState.isHost && roomState.mediaUrl.contains("/api/v1/stream/")) {
                Surface(
                    color = DarkSurfaceElevated,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Sensors, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("📡 Streaming Host's shared movie in real-time", fontSize = 11.sp, color = TextSecondary)
                    }
                }
            }

            // 2. Video Title & Room Action Header Bar
            Surface(
                color = DarkSurface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
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

            // 3. YouTube-Style Embedded Inline Chat (Takes remaining space and resizes smoothly above keyboard)
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

    // Leave Room Confirmation Dialog
    if (showExitConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showExitConfirmDialog = false },
            containerColor = DarkSurface,
            title = {
                Text("Leave Watch Room?", fontWeight = FontWeight.Bold, color = TextPrimary)
            },
            text = {
                Text(
                    "Are you sure you want to leave this session? You will be disconnected from the synchronized stream.",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showExitConfirmDialog = false
                        onLeaveRoom()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                ) {
                    Text("Leave Room", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirmDialog = false }) {
                    Text("Stay", color = TextSecondary)
                }
            }
        )
    }

    // Audio Track Selection Dialog
    if (showAudioTrackDialog) {
        val tracks = remember(exoPlayer.currentTracks) {
            val audioTracks = mutableListOf<String>()
            for (group in exoPlayer.currentTracks.groups) {
                if (group.type == C.TRACK_TYPE_AUDIO) {
                    for (i in 0 until group.length) {
                        val format = group.getTrackFormat(i)
                        val lang = format.language ?: "Audio Track ${i + 1}"
                        val label = format.label ?: lang
                        val details = "$label (${format.sampleMimeType ?: "audio"})"
                        audioTracks.add(details)
                    }
                }
            }
            audioTracks
        }

        AlertDialog(
            onDismissRequest = { showAudioTrackDialog = false },
            containerColor = DarkSurface,
            title = { Text("🎵 Audio Tracks", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (tracks.isEmpty()) {
                        Text("Default audio output active", color = TextSecondary, fontSize = 13.sp)
                    } else {
                        tracks.forEach { name ->
                            TextButton(
                                onClick = {
                                    showAudioTrackDialog = false
                                    Toast.makeText(context, "Selected $name", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(name, color = AccentCyan, fontSize = 13.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAudioTrackDialog = false }) {
                    Text("Close", color = TextSecondary)
                }
            }
        )
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
    }

    // Switch Media Dialog (Paste URL or Pick Local File) — Declared at top level so it never gets closed unexpectedly
    if (showChangeMediaDialog) {
        var selectedTab by remember { mutableStateOf(0) }
        var newUrlInput by remember { mutableStateOf("") }
        var newFileUri by remember { mutableStateOf<Uri?>(null) }
        var newFileName by remember { mutableStateOf("") }

        val changeFilePicker = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument()
        ) { uri: Uri? ->
            if (uri != null) {
                try {
                    val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    context.contentResolver.takePersistableUriPermission(uri, flags)
                } catch (e: Exception) {}
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
                            onClick = { changeFilePicker.launch(arrayOf("video/*", "video/mp4", "video/mkv", "video/webm", "video/avi")) },
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

    // Audio & Subtitle Track Selector Dialog
    if (showAudioTrackDialog) {
        val audioTracks = remember(tracksState) {
            val list = mutableListOf<Triple<Int, Int, String>>() // groupIndex, trackIndex, label
            for (gIdx in 0 until tracksState.groups.size) {
                val group = tracksState.groups[gIdx]
                if (group.type == C.TRACK_TYPE_AUDIO) {
                    for (tIdx in 0 until group.length) {
                        val format = group.getTrackFormat(tIdx)
                        val lang = format.language ?: "und"
                        val displayLang = if (lang != "und") Locale(lang).displayLanguage else ""
                        val label = format.label ?: if (displayLang.isNotEmpty()) displayLang else "Track ${list.size + 1}"
                        val channels = if (format.channelCount > 0) " • ${format.channelCount}ch" else ""
                        val mime = format.sampleMimeType?.substringAfter('/') ?: ""
                        val codec = if (mime.isNotEmpty()) " ($mime)" else ""
                        val fullLabel = "$label$codec$channels"
                        list.add(Triple(gIdx, tIdx, fullLabel))
                    }
                }
            }
            list
        }

        val subtitleTracks = remember(tracksState) {
            val list = mutableListOf<Triple<Int, Int, String>>()
            for (gIdx in 0 until tracksState.groups.size) {
                val group = tracksState.groups[gIdx]
                if (group.type == C.TRACK_TYPE_TEXT) {
                    for (tIdx in 0 until group.length) {
                        val format = group.getTrackFormat(tIdx)
                        val lang = format.language ?: "und"
                        val displayLang = if (lang != "und") Locale(lang).displayLanguage else ""
                        val label = format.label ?: if (displayLang.isNotEmpty()) displayLang else "Subtitle ${list.size + 1}"
                        list.add(Triple(gIdx, tIdx, label))
                    }
                }
            }
            list
        }

        AlertDialog(
            onDismissRequest = { showAudioTrackDialog = false },
            containerColor = DarkSurface,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Audiotrack, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Audio & Subtitle Tracks", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    item {
                        Text("AUDIO TRACKS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AccentCyan)
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    if (audioTracks.isEmpty()) {
                        item {
                            Text("Default Audio Track (Embedded)", fontSize = 13.sp, color = TextSecondary, modifier = Modifier.padding(vertical = 4.dp))
                        }
                    } else {
                        items(audioTracks) { (gIdx, tIdx, label) ->
                            val isSelected = tracksState.groups.getOrNull(gIdx)?.isTrackSelected(tIdx) == true
                            Surface(
                                color = if (isSelected) AccentCyan.copy(alpha = 0.15f) else DarkSurfaceElevated,
                                shape = RoundedCornerShape(8.dp),
                                border = if (isSelected) BorderStroke(1.dp, AccentCyan) else null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                                    .clickable {
                                        val group = tracksState.groups.getOrNull(gIdx)
                                        if (group != null) {
                                            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
                                                .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, listOf(tIdx)))
                                                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                                                .build()
                                            Toast.makeText(context, "Audio switched to $label", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(label, fontSize = 13.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, color = if (isSelected) AccentCyan else TextPrimary)
                                    if (isSelected) {
                                        Icon(Icons.Filled.Check, contentDescription = "Selected", tint = AccentCyan, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }

                    if (subtitleTracks.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(14.dp))
                            Text("SUBTITLES", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AccentIndigo)
                            Spacer(modifier = Modifier.height(6.dp))
                        }

                        item {
                            val isSubDisabled = exoPlayer.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
                            Surface(
                                color = if (isSubDisabled) AccentIndigo.copy(alpha = 0.15f) else DarkSurfaceElevated,
                                shape = RoundedCornerShape(8.dp),
                                border = if (isSubDisabled) BorderStroke(1.dp, AccentIndigo) else null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                                    .clickable {
                                        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
                                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                                            .build()
                                        areSubtitlesEnabled = false
                                        Toast.makeText(context, "Subtitles turned off", Toast.LENGTH_SHORT).show()
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Off", fontSize = 13.sp, fontWeight = if (isSubDisabled) FontWeight.Bold else FontWeight.Normal, color = if (isSubDisabled) AccentIndigo else TextPrimary)
                                    if (isSubDisabled) {
                                        Icon(Icons.Filled.Check, contentDescription = "Selected", tint = AccentIndigo, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }

                        items(subtitleTracks) { (gIdx, tIdx, label) ->
                            val isSelected = !exoPlayer.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT) &&
                                    tracksState.groups.getOrNull(gIdx)?.isTrackSelected(tIdx) == true
                            Surface(
                                color = if (isSelected) AccentCyan.copy(alpha = 0.15f) else DarkSurfaceElevated,
                                shape = RoundedCornerShape(8.dp),
                                border = if (isSelected) BorderStroke(1.dp, AccentCyan) else null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                                    .clickable {
                                        val group = tracksState.groups.getOrNull(gIdx)
                                        if (group != null) {
                                            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
                                                .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, listOf(tIdx)))
                                                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                                .build()
                                            areSubtitlesEnabled = true
                                            Toast.makeText(context, "Subtitles: $label", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(label, fontSize = 13.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, color = if (isSelected) AccentCyan else TextPrimary)
                                    if (isSelected) {
                                        Icon(Icons.Filled.Check, contentDescription = "Selected", tint = AccentCyan, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showAudioTrackDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentCyan)
                ) {
                    Text("Done", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}



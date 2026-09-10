package com.syncwatch.app

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.syncwatch.app.data.local.UserPreferences
import com.syncwatch.app.data.models.ChatMessage
import com.syncwatch.app.data.models.ConnectionStatus
import com.syncwatch.app.data.network.ClockSyncManager
import com.syncwatch.app.data.network.SyncWebSocketClient
import com.syncwatch.app.sync.LagFreeSyncEngine
import com.syncwatch.app.ui.screens.HomeScreen
import com.syncwatch.app.ui.screens.RoomScreen
import com.syncwatch.app.ui.theme.CinemaDarkBg
import com.syncwatch.app.ui.theme.SyncWatchTheme
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val clockSyncManager = ClockSyncManager()
    private val webSocketClient = SyncWebSocketClient(clockSyncManager)
    private val syncEngine = LagFreeSyncEngine(clockSyncManager)
    private lateinit var userPreferences: UserPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        userPreferences = UserPreferences(this)

        lifecycleScope.launch {
            webSocketClient.errorFlow.collectLatest { error ->
                Toast.makeText(this@MainActivity, error, Toast.LENGTH_LONG).show()
            }
        }

        setContent {
            SyncWatchTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = CinemaDarkBg
                ) {
                    val roomState by webSocketClient.roomState.collectAsState()
                    val telemetry by syncEngine.telemetry.collectAsState()
                    val connectionStatus by webSocketClient.connectionStatus.collectAsState()

                    val messages = remember { mutableStateListOf<ChatMessage>() }

                    LaunchedEffect(Unit) {
                        webSocketClient.chatFlow.collect { chat ->
                            messages.add(chat)
                        }
                    }

                    var localHostFileUri by remember { mutableStateOf<android.net.Uri?>(null) }

                    // Keep sync engine aligned with room state
                    LaunchedEffect(roomState.roomId) {
                        if (roomState.roomId.isNotEmpty()) {
                            syncEngine.startSyncLoop(webSocketClient.roomState)
                            // Record to recent rooms history
                            userPreferences.addRecentRoom(roomState.roomId, roomState.mediaTitle)

                            // If host created room with a local video file, initiate stream upload to server
                            if (roomState.isHost && localHostFileUri != null) {
                                val serverHttpUrl = userPreferences.getHttpBaseUrl()
                                val streamUrl = "$serverHttpUrl/api/v1/stream/${roomState.roomId}"
                                webSocketClient.sendChangeMedia(streamUrl, roomState.mediaTitle)
                                com.syncwatch.app.data.network.StreamUploadManager.uploadVideo(
                                    context = this@MainActivity,
                                    roomCode = roomState.roomId,
                                    videoUri = localHostFileUri!!,
                                    serverHttpUrl = serverHttpUrl
                                )
                            }
                        } else {
                            syncEngine.stopSyncLoop()
                        }
                    }

                    if (roomState.roomId.isEmpty()) {
                        HomeScreen(
                            onCreateRoom = { guestId, displayName, mediaUrl, mediaTitle, serverUrl ->
                                messages.clear()
                                userPreferences.saveDisplayName(displayName)
                                userPreferences.saveServerUrl(serverUrl)

                                if (mediaUrl.startsWith("content://") || mediaUrl.startsWith("file://")) {
                                    localHostFileUri = android.net.Uri.parse(mediaUrl)
                                } else {
                                    localHostFileUri = null
                                }

                                webSocketClient.connect(serverUrl) {
                                    webSocketClient.createRoom(
                                        guestId = guestId,
                                        displayName = displayName,
                                        mediaUrl = mediaUrl,
                                        mediaTitle = mediaTitle
                                    )
                                }
                            },
                            onJoinRoom = { roomCode, guestId, displayName, serverUrl ->
                                messages.clear()
                                localHostFileUri = null
                                userPreferences.saveDisplayName(displayName)
                                userPreferences.saveServerUrl(serverUrl)

                                webSocketClient.connect(serverUrl) {
                                    webSocketClient.joinRoom(
                                        roomCode = roomCode,
                                        guestId = guestId,
                                        displayName = displayName
                                    )
                                }
                            }
                        )
                    } else {
                        RoomScreen(
                            roomState = roomState,
                            telemetry = telemetry,
                            messages = messages,
                            connectionStatus = connectionStatus,
                            myGuestId = webSocketClient.myGuestId,
                            localHostFileUri = localHostFileUri,
                            onPlay = { pos -> webSocketClient.sendPlay(pos) },
                            onPause = { pos -> webSocketClient.sendPause(pos) },
                            onSeek = { pos -> webSocketClient.sendSeek(pos) },
                            onChangeMedia = { url, title ->
                                if (url.startsWith("content://") || url.startsWith("file://")) {
                                    val uri = android.net.Uri.parse(url)
                                    localHostFileUri = uri
                                    val serverHttpUrl = userPreferences.getHttpBaseUrl()
                                    val streamUrl = "$serverHttpUrl/api/v1/stream/${roomState.roomId}"
                                    webSocketClient.sendChangeMedia(streamUrl, title)
                                    com.syncwatch.app.data.network.StreamUploadManager.uploadVideo(
                                        context = this@MainActivity,
                                        roomCode = roomState.roomId,
                                        videoUri = uri,
                                        serverHttpUrl = serverHttpUrl
                                    )
                                } else {
                                    localHostFileUri = null
                                    com.syncwatch.app.data.network.StreamUploadManager.cancelUpload()
                                    webSocketClient.sendChangeMedia(url, title)
                                }
                            },
                            onSetControlMode = { mode -> webSocketClient.sendControlMode(mode) },
                            onSetRoomLock = { locked -> webSocketClient.sendRoomLock(locked) },
                            onKickParticipant = { targetId -> webSocketClient.sendKickParticipant(targetId) },
                            onBufferingChanged = { isBuffering -> webSocketClient.sendBufferingState(isBuffering) },
                            onSendMessage = { text -> webSocketClient.sendChat(text) },
                            onSendHeartbeat = { pos, isPlaying -> webSocketClient.sendHeartbeat(pos, isPlaying) },
                            onUpdateSettings = { settings -> syncEngine.settings = settings },
                            onLeaveRoom = {
                                localHostFileUri = null
                                com.syncwatch.app.data.network.StreamUploadManager.cancelUpload()
                                webSocketClient.disconnect()
                            },
                            syncEngine = syncEngine,
                            infoFlow = webSocketClient.infoToastFlow
                        )
                    }
                }
            }
        }
    }

    fun enterPipMode() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            try {
                val params = android.app.PictureInPictureParams.Builder()
                    .setAspectRatio(android.util.Rational(16, 9))
                    .build()
                enterPictureInPictureMode(params)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (webSocketClient.roomState.value.roomId.isNotEmpty()) {
            enterPipMode()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        webSocketClient.disconnect()
    }
}

package com.syncwatch.app.data.network

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.syncwatch.app.data.models.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.*
import java.util.concurrent.TimeUnit

class SyncWebSocketClient(
    val clockSyncManager: ClockSyncManager
) {
    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .readTimeout(15, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var ntpJob: Job? = null
    private var reconnectJob: Job? = null

    private var currentServerUrl: String = ""
    private var currentRoomCode: String = ""
    private var currentGuestId: String = ""
    private var currentDisplayName: String = ""
    private var autoReconnectEnabled = true

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus

    private val _roomState = MutableStateFlow(RoomState())
    val roomState: StateFlow<RoomState> = _roomState

    private val _chatFlow = MutableSharedFlow<ChatMessage>()
    val chatFlow: SharedFlow<ChatMessage> = _chatFlow

    private val _errorFlow = MutableSharedFlow<String>()
    val errorFlow: SharedFlow<String> = _errorFlow

    private val _infoToastFlow = MutableSharedFlow<String>()
    val infoToastFlow: SharedFlow<String> = _infoToastFlow

    var myGuestId: String = ""
        private set

    fun connect(serverUrl: String, onConnected: (() -> Unit)? = null) {
        currentServerUrl = serverUrl
        autoReconnectEnabled = true
        _connectionStatus.value = ConnectionStatus.CONNECTING

        val request = Request.Builder().url(serverUrl).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                _connectionStatus.value = ConnectionStatus.CONNECTED
                startNtpLoop()
                onConnected?.invoke()
            }

            override fun onMessage(ws: WebSocket, text: String) {
                handleIncomingMessage(text)
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                _connectionStatus.value = ConnectionStatus.DISCONNECTED
                stopNtpLoop()
                if (autoReconnectEnabled && currentRoomCode.isNotEmpty()) {
                    triggerAutoReconnect()
                }
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                _connectionStatus.value = ConnectionStatus.DISCONNECTED
                stopNtpLoop()
                if (autoReconnectEnabled && currentRoomCode.isNotEmpty()) {
                    triggerAutoReconnect()
                } else {
                    scope.launch { _errorFlow.emit(t.localizedMessage ?: "Failed to connect to server") }
                }
            }
        })
    }

    fun disconnect() {
        autoReconnectEnabled = false
        reconnectJob?.cancel()
        reconnectJob = null
        stopNtpLoop()
        try {
            webSocket?.close(1000, "User Left")
        } catch (e: Exception) {}
        webSocket = null
        _connectionStatus.value = ConnectionStatus.DISCONNECTED
        _roomState.value = RoomState()
        currentRoomCode = ""
    }

    private fun triggerAutoReconnect() {
        if (reconnectJob?.isActive == true) return
        reconnectJob = scope.launch {
            _connectionStatus.value = ConnectionStatus.RECONNECTING
            _infoToastFlow.emit("Reconnecting...")
            
            var attempt = 0
            while (autoReconnectEnabled && currentRoomCode.isNotEmpty() && _connectionStatus.value != ConnectionStatus.CONNECTED) {
                attempt++
                val delayMs = (1500L * attempt).coerceAtMost(10000L)
                delay(delayMs)

                try {
                    connect(currentServerUrl) {
                        // Re-join existing room seamlessly
                        joinRoom(
                            roomCode = currentRoomCode,
                            guestId = currentGuestId,
                            displayName = currentDisplayName
                        )
                        scope.launch { _infoToastFlow.emit("Back in sync") }
                    }
                    break
                } catch (e: Exception) {
                    // Retry
                }
            }
        }
    }

    private fun startNtpLoop() {
        ntpJob?.cancel()
        ntpJob = scope.launch {
            while (_connectionStatus.value == ConnectionStatus.CONNECTED || _connectionStatus.value == ConnectionStatus.IN_SYNC) {
                sendNtpPing()
                delay(3000) // Clock synchronization every 3 seconds
            }
        }
    }

    private fun stopNtpLoop() {
        ntpJob?.cancel()
        ntpJob = null
    }

    private fun sendNtpPing() {
        sendPacket(SyncPacket.NtpPing(clientTime = System.currentTimeMillis()))
    }

    fun createRoom(guestId: String, displayName: String, mediaUrl: String, mediaTitle: String) {
        myGuestId = guestId
        currentGuestId = guestId
        currentDisplayName = displayName
        val packet = SyncPacket.CreateRoom(
            guestId = guestId,
            displayName = displayName,
            mediaUrl = mediaUrl,
            mediaTitle = mediaTitle
        )
        sendPacket(packet)
    }

    fun joinRoom(roomCode: String, guestId: String, displayName: String) {
        myGuestId = guestId
        currentGuestId = guestId
        currentDisplayName = displayName
        currentRoomCode = roomCode
        val packet = SyncPacket.JoinRoom(
            roomCode = roomCode,
            guestId = guestId,
            displayName = displayName
        )
        sendPacket(packet)
    }

    fun sendPlay(positionSec: Double) {
        sendPacket(SyncPacket.ActionPlay(positionSec = positionSec))
    }

    fun sendPause(positionSec: Double) {
        sendPacket(SyncPacket.ActionPause(positionSec = positionSec))
    }

    fun sendSeek(positionSec: Double) {
        sendPacket(SyncPacket.ActionSeek(positionSec = positionSec))
    }

    fun sendChangeMedia(mediaUrl: String, mediaTitle: String) {
        sendPacket(SyncPacket.ChangeMedia(mediaUrl = mediaUrl, mediaTitle = mediaTitle))
    }

    fun sendControlMode(mode: String) {
        sendPacket(SyncPacket.SetControlMode(mode = mode))
    }

    fun sendRoomLock(isLocked: Boolean) {
        sendPacket(SyncPacket.SetRoomLock(isLocked = isLocked))
    }

    fun sendKickParticipant(targetGuestId: String) {
        sendPacket(SyncPacket.KickParticipant(targetGuestId = targetGuestId))
    }

    fun sendBufferingState(isBuffering: Boolean) {
        sendPacket(SyncPacket.BufferingState(isBuffering = isBuffering))
    }

    fun sendChat(text: String) {
        sendPacket(SyncPacket.SendChat(text = text))
    }

    private fun sendPacket(obj: Any) {
        try {
            val json = gson.toJson(obj)
            webSocket?.send(json)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun parseParticipants(array: JsonArray?): List<Participant> {
        if (array == null) return emptyList()
        val list = mutableListOf<Participant>()
        for (elem in array) {
            if (elem.isJsonObject) {
                val obj = elem.asJsonObject
                list.add(
                    Participant(
                        guestId = obj.get("guestId")?.asString ?: "",
                        displayName = obj.get("displayName")?.asString ?: "Guest",
                        isHost = obj.get("isHost")?.asBoolean ?: false,
                        isBuffering = obj.get("isBuffering")?.asBoolean ?: false,
                        isOnline = obj.get("isOnline")?.asBoolean ?: true,
                        joinedAt = obj.get("joinedAt")?.asLong ?: 0L
                    )
                )
            }
        }
        return list
    }

    private fun handleIncomingMessage(jsonStr: String) {
        try {
            val jsonObject = JsonParser.parseString(jsonStr).asJsonObject
            val type = jsonObject.get("type")?.asString ?: return

            when (type) {
                "NTP_PONG" -> {
                    val clientTime = jsonObject.get("clientTime")?.asLong ?: return
                    val serverTime = jsonObject.get("serverTime")?.asLong ?: return
                    clockSyncManager.processPong(clientTime, serverTime)
                }

                "ROOM_CREATED", "ROOM_JOINED" -> {
                    val roomObj = jsonObject.getAsJsonObject("roomState")
                    val isHost = jsonObject.get("isHost")?.asBoolean ?: false
                    currentRoomCode = jsonObject.get("roomCode")?.asString ?: ""
                    
                    val participants = parseParticipants(roomObj.getAsJsonArray("participants"))

                    _roomState.value = RoomState(
                        roomId = currentRoomCode,
                        pin = currentRoomCode,
                        hostId = roomObj.get("hostId")?.asString ?: "",
                        hostName = roomObj.get("hostName")?.asString ?: "Host",
                        isHost = isHost,
                        mediaUrl = roomObj.get("mediaUrl")?.asString ?: "",
                        mediaTitle = roomObj.get("mediaTitle")?.asString ?: "Video",
                        isPlaying = roomObj.get("isPlaying")?.asBoolean ?: false,
                        targetPositionSec = roomObj.get("positionSec")?.asDouble ?: 0.0,
                        anchorServerTime = roomObj.get("anchorServerTime")?.asLong ?: System.currentTimeMillis(),
                        controlMode = roomObj.get("controlMode")?.asString ?: "HOST_ONLY",
                        isLocked = roomObj.get("isLocked")?.asBoolean ?: false,
                        participants = participants,
                        peerCount = participants.size.coerceAtLeast(1)
                    )
                    _connectionStatus.value = ConnectionStatus.IN_SYNC
                }

                "SYNC_STATE" -> {
                    val isPlaying = jsonObject.get("isPlaying")?.asBoolean ?: false
                    val positionSec = jsonObject.get("positionSec")?.asDouble ?: 0.0
                    val anchorServerTime = jsonObject.get("anchorServerTime")?.asLong ?: System.currentTimeMillis()

                    _roomState.value = _roomState.value.copy(
                        isPlaying = isPlaying,
                        targetPositionSec = positionSec,
                        anchorServerTime = anchorServerTime
                    )
                }

                "MEDIA_CHANGED" -> {
                    val mediaUrl = jsonObject.get("mediaUrl")?.asString ?: ""
                    val mediaTitle = jsonObject.get("mediaTitle")?.asString ?: ""
                    val positionSec = jsonObject.get("positionSec")?.asDouble ?: 0.0
                    val anchorServerTime = jsonObject.get("anchorServerTime")?.asLong ?: System.currentTimeMillis()

                    _roomState.value = _roomState.value.copy(
                        mediaUrl = mediaUrl,
                        mediaTitle = mediaTitle,
                        isPlaying = false,
                        targetPositionSec = positionSec,
                        anchorServerTime = anchorServerTime
                    )
                }

                "CONTROL_MODE_CHANGED" -> {
                    val mode = jsonObject.get("controlMode")?.asString ?: "HOST_ONLY"
                    _roomState.value = _roomState.value.copy(controlMode = mode)
                    val modeLabel = if (mode == "HOST_ONLY") "Host Control Only" else "Shared Control Mode"
                    scope.launch { _infoToastFlow.emit("Control mode changed: $modeLabel") }
                }

                "ROOM_LOCK_CHANGED" -> {
                    val isLocked = jsonObject.get("isLocked")?.asBoolean ?: false
                    _roomState.value = _roomState.value.copy(isLocked = isLocked)
                    val lockLabel = if (isLocked) "Room locked by host" else "Room unlocked"
                    scope.launch { _infoToastFlow.emit(lockLabel) }
                }

                "HOST_CHANGED" -> {
                    val newHostId = jsonObject.get("newHostId")?.asString ?: ""
                    val newHostName = jsonObject.get("newHostName")?.asString ?: "Host"
                    val isMe = newHostId == myGuestId
                    _roomState.value = _roomState.value.copy(
                        hostId = newHostId,
                        hostName = newHostName,
                        isHost = isMe
                    )
                    scope.launch { _infoToastFlow.emit("$newHostName is now the Host") }
                }

                "PARTICIPANT_JOINED", "PARTICIPANT_LEFT", "PARTICIPANT_BUFFERING" -> {
                    val participants = parseParticipants(jsonObject.getAsJsonArray("participants"))
                    _roomState.value = _roomState.value.copy(
                        participants = participants,
                        peerCount = participants.size.coerceAtLeast(1)
                    )
                }

                "CHAT_MESSAGE" -> {
                    val msgObj = jsonObject.getAsJsonObject("message")
                    val chat = ChatMessage(
                        id = msgObj.get("id")?.asString ?: java.util.UUID.randomUUID().toString(),
                        senderId = msgObj.get("senderId")?.asString ?: "",
                        sender = msgObj.get("senderName")?.asString ?: "User",
                        isHost = msgObj.get("isHost")?.asBoolean ?: false,
                        isSystem = msgObj.get("isSystem")?.asBoolean ?: false,
                        text = msgObj.get("text")?.asString ?: "",
                        timestamp = msgObj.get("timestamp")?.asLong ?: System.currentTimeMillis()
                    )
                    scope.launch { _chatFlow.emit(chat) }
                }

                "KICKED_FROM_ROOM" -> {
                    val msg = jsonObject.get("message")?.asString ?: "You were removed from the room."
                    disconnect()
                    scope.launch { _errorFlow.emit(msg) }
                }

                "ERROR" -> {
                    val msg = jsonObject.get("message")?.asString ?: "Error occurred."
                    scope.launch { _errorFlow.emit(msg) }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

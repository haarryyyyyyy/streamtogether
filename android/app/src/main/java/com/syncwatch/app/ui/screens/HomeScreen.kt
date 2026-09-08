package com.syncwatch.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syncwatch.app.data.local.UserPreferences
import com.syncwatch.app.data.models.RecentRoom
import com.syncwatch.app.ui.theme.*
import com.syncwatch.app.utils.MediaUtils
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onCreateRoom: (guestId: String, displayName: String, mediaUrl: String, mediaTitle: String, serverUrl: String) -> Unit,
    onJoinRoom: (roomCode: String, guestId: String, displayName: String, serverUrl: String) -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { UserPreferences(context) }
    val focusManager = LocalFocusManager.current

    val guestId = remember { prefs.getOrCreateGuestId() }
    var displayName by remember { mutableStateOf(prefs.getDisplayName()) }
    var roomCodeInput by remember { mutableStateOf("") }
    var serverUrl by remember { mutableStateOf(prefs.getServerUrl()) }
    var recentRooms by remember { mutableStateOf(prefs.getRecentRooms()) }

    var showCreateDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    fun validateAndJoin(targetCode: String) {
        val cleanCode = targetCode.trim().uppercase()
        val cleanName = if (displayName.isBlank()) "Guest" else displayName.trim()
        if (cleanCode.isBlank()) {
            Toast.makeText(context, "Please enter a Room Code", Toast.LENGTH_SHORT).show()
            return
        }
        prefs.saveDisplayName(cleanName)
        focusManager.clearFocus()
        onJoinRoom(cleanCode, guestId, cleanName, serverUrl)
    }

    Scaffold(
        containerColor = CinemaDarkBg,
        topBar = {
            TopAppBar(
                title = { },
                actions = {
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = "Settings",
                            tint = TextSecondary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Spacer(modifier = Modifier.height(16.dp))

                // Brand Logo & Header
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(AccentCyan.copy(alpha = 0.25f), AccentIndigo.copy(alpha = 0.15f))
                            )
                        )
                        .border(1.5.dp, AccentCyan.copy(alpha = 0.6f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Movie,
                        contentDescription = "StreamTogether Logo",
                        tint = AccentCyan,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "StreamTogether",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    letterSpacing = 0.5.sp
                )

                Text(
                    text = "Synchronized cinema with friends • Zero friction",
                    fontSize = 13.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp, bottom = 32.dp)
                )
            }

            // Display Name Input Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(DarkBorder, DarkBorder.copy(alpha = 0.5f))))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Text(
                            text = "YOUR IDENTITY",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AccentCyan,
                            letterSpacing = 1.sp
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = displayName,
                            onValueChange = {
                                displayName = it
                                prefs.saveDisplayName(it)
                            },
                            label = { Text("Display Name") },
                            placeholder = { Text("e.g. Harry") },
                            leadingIcon = {
                                Icon(Icons.Outlined.Person, contentDescription = null, tint = AccentCyan)
                            },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentCyan,
                                unfocusedBorderColor = DarkBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedContainerColor = DarkSurfaceElevated,
                                unfocusedContainerColor = DarkSurfaceElevated
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text(
                            text = "Anonymous Guest ID: $guestId (Saved locally)",
                            fontSize = 11.sp,
                            color = TextMuted,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }

            // Join or Create Room Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(DarkBorder, DarkBorder.copy(alpha = 0.5f))))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Text(
                            text = "WATCH ROOM",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AccentIndigo,
                            letterSpacing = 1.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = roomCodeInput,
                            onValueChange = { input ->
                                if (input.length <= 6 && input.all { char -> char.isDigit() }) {
                                    roomCodeInput = input
                                }
                            },
                            label = { Text("Enter 6-Digit Room Code") },
                            placeholder = { Text("e.g. 849201") },
                            leadingIcon = {
                                Icon(Icons.Outlined.MeetingRoom, contentDescription = null, tint = AccentIndigo)
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = { validateAndJoin(roomCodeInput) }
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentIndigo,
                                unfocusedBorderColor = DarkBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedContainerColor = DarkSurfaceElevated,
                                unfocusedContainerColor = DarkSurfaceElevated
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = { validateAndJoin(roomCodeInput) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentCyan)
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.Black)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Join Room", fontWeight = FontWeight.Bold, color = Color.Black, fontSize = 15.sp)
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedButton(
                            onClick = {
                                val cleanName = if (displayName.isBlank()) "Host" else displayName.trim()
                                prefs.saveDisplayName(cleanName)
                                showCreateDialog = true
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = ButtonDefaults.outlinedButtonBorder.copy(brush = Brush.linearGradient(listOf(AccentIndigo, AccentPurple)))
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, tint = AccentIndigo)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Create New Room", fontWeight = FontWeight.SemiBold, color = TextPrimary, fontSize = 15.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))
            }

            // Recently Joined Rooms History
            if (recentRooms.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Recently Joined",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondary
                        )
                        TextButton(onClick = {
                            prefs.clearRecentRooms()
                            recentRooms = emptyList()
                        }) {
                            Text("Clear", fontSize = 12.sp, color = TextMuted)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }

                items(recentRooms) { recent ->
                    val dateStr = remember(recent.lastJoinedTimestamp) {
                        val sdf = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
                        sdf.format(Date(recent.lastJoinedTimestamp))
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { validateAndJoin(recent.roomCode) },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(DarkBorder),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Tv, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(20.dp))
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = recent.roomCode,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = recent.mediaTitle,
                                    fontSize = 12.sp,
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Text(
                                text = dateStr,
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }

    // Modal Dialog: Create Room (Stream URL or Local File)
    if (showCreateDialog) {
        var selectedTab by remember { mutableStateOf(0) } // 0 = Stream URL, 1 = Local File
        var streamUrlInput by remember { mutableStateOf("") }
        var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
        var selectedFileName by remember { mutableStateOf("") }

        val filePickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument()
        ) { uri: Uri? ->
            if (uri != null) {
                try {
                    val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    context.contentResolver.takePersistableUriPermission(uri, flags)
                } catch (e: Exception) {}
                selectedFileUri = uri
                selectedFileName = MediaUtils.getFileNameFromUri(context, uri)
            }
        }

        val autoTitle = remember(selectedTab, streamUrlInput, selectedFileName) {
            if (selectedTab == 0) {
                if (streamUrlInput.isNotBlank()) MediaUtils.extractTitleFromUrl(streamUrlInput) else "Movie Stream"
            } else {
                if (selectedFileName.isNotBlank()) selectedFileName else "Local Movie"
            }
        }

        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            containerColor = DarkSurface,
            title = {
                Text("Select Movie to Stream", fontWeight = FontWeight.Bold, color = TextPrimary)
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Mode Switcher Tabs
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = DarkSurfaceElevated,
                        contentColor = AccentCyan,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .padding(bottom = 16.dp)
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text("🌐 Paste Link", fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { Text("📁 Local File", fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                        )
                    }

                    if (selectedTab == 0) {
                        // Stream URL Input
                        Text(
                            text = "Paste direct video or stream URL:",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )

                        OutlinedTextField(
                            value = streamUrlInput,
                            onValueChange = { streamUrlInput = it },
                            label = { Text("Video Link (.mp4 / .m3u8 / .mkv)") },
                            placeholder = { Text("https://example.com/movie.mp4") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentCyan,
                                unfocusedBorderColor = DarkBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedContainerColor = DarkSurfaceElevated,
                                unfocusedContainerColor = DarkSurfaceElevated
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (streamUrlInput.isNotBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                color = DarkSurfaceElevated,
                                shape = RoundedCornerShape(8.dp),
                                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DarkBorder))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Filled.Movie, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text("Detected Title:", fontSize = 10.sp, color = TextMuted)
                                        Text(autoTitle, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                    }
                                }
                            }
                        }
                    } else {
                        // Local File Picker
                        Text(
                            text = "Select video from phone storage:",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )

                        OutlinedButton(
                            onClick = { filePickerLauncher.launch(arrayOf("video/*", "video/mp4", "video/mkv", "video/webm", "video/avi")) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(10.dp),
                            border = ButtonDefaults.outlinedButtonBorder.copy(brush = Brush.linearGradient(listOf(AccentIndigo, AccentCyan)))
                        ) {
                            Icon(Icons.Outlined.FolderOpen, contentDescription = null, tint = AccentCyan)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (selectedFileName.isNotBlank()) "Change File" else "Choose Video File",
                                color = TextPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        if (selectedFileName.isNotBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                color = DarkSurfaceElevated,
                                shape = RoundedCornerShape(8.dp),
                                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DarkBorder))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Filled.VideoFile, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text("Selected Movie:", fontSize = 10.sp, color = TextMuted)
                                        Text(selectedFileName, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val finalUrl = if (selectedTab == 0) streamUrlInput.trim() else selectedFileUri?.toString() ?: ""
                        if (finalUrl.isBlank()) {
                            val msg = if (selectedTab == 0) "Please paste a video link" else "Please select a video file"
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        showCreateDialog = false
                        val cleanName = if (displayName.isBlank()) "Host" else displayName.trim()
                        onCreateRoom(guestId, cleanName, finalUrl, autoTitle, serverUrl)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentCyan)
                ) {
                    Text("Start Watch Room", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    // Modal Dialog: Settings
    if (showSettingsDialog) {
        var tempServerUrl by remember { mutableStateOf(serverUrl) }
        var tempName by remember { mutableStateOf(displayName) }

        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            containerColor = DarkSurface,
            title = {
                Text("Connection & Settings", fontWeight = FontWeight.Bold, color = TextPrimary)
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Guest Identity", fontSize = 12.sp, color = AccentCyan, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = tempName,
                        onValueChange = { tempName = it },
                        label = { Text("Display Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Signaling Server WebSocket", fontSize = 12.sp, color = AccentIndigo, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = tempServerUrl,
                        onValueChange = { tempServerUrl = it },
                        label = { Text("Server URL") },
                        placeholder = { Text("ws://<ORACLE_VM_IP>:8080 or wss://domain.com") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AssistChip(
                            onClick = { tempServerUrl = "ws://10.0.2.2:8080" },
                            label = { Text("Emulator") }
                        )
                        AssistChip(
                            onClick = { tempServerUrl = "ws://127.0.0.1:8080" },
                            label = { Text("Localhost") }
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        displayName = tempName
                        serverUrl = tempServerUrl
                        prefs.saveDisplayName(tempName)
                        prefs.saveServerUrl(tempServerUrl)
                        showSettingsDialog = false
                        Toast.makeText(context, "Settings saved", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentCyan)
                ) {
                    Text("Save", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSettingsDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}

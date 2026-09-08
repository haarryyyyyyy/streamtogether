package com.syncwatch.app.ui.screens

import android.widget.Toast
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syncwatch.app.data.local.UserPreferences
import com.syncwatch.app.data.models.MediaCatalogItem
import com.syncwatch.app.data.models.RecentRoom
import com.syncwatch.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

val SAMPLE_MOVIES = listOf(
    MediaCatalogItem(
        id = "big-buck-bunny",
        title = "Big Buck Bunny (4K Open Movie)",
        url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
        thumbnail = "",
        durationSec = 596,
        license = "Creative Commons 3.0"
    ),
    MediaCatalogItem(
        id = "tears-of-steel",
        title = "Tears of Steel (Blender VFX Sci-Fi)",
        url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4",
        thumbnail = "",
        durationSec = 734,
        license = "Creative Commons 3.0"
    ),
    MediaCatalogItem(
        id = "sintel",
        title = "Sintel (Blender Studio Animation)",
        url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4",
        thumbnail = "",
        durationSec = 888,
        license = "Creative Commons 3.0"
    ),
    MediaCatalogItem(
        id = "elephants-dream",
        title = "Elephants Dream (Open Movie)",
        url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
        thumbnail = "",
        durationSec = 654,
        license = "Creative Commons 2.5"
    )
)

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
                        contentDescription = "WatchTogether Logo",
                        tint = AccentCyan,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "WatchTogether",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    letterSpacing = 0.5.sp
                )

                Text(
                    text = "Synchronized theater with friends • Zero friction",
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
                            onValueChange = { roomCodeInput = it.uppercase() },
                            label = { Text("Enter Room Code") },
                            placeholder = { Text("e.g. WATCH-8K42") },
                            leadingIcon = {
                                Icon(Icons.Outlined.MeetingRoom, contentDescription = null, tint = AccentIndigo)
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Characters,
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

    // Modal Dialog: Create Room & Pick Media
    if (showCreateDialog) {
        var selectedMovie by remember { mutableStateOf(SAMPLE_MOVIES[0]) }
        var isCustomUrl by remember { mutableStateOf(false) }
        var customUrlInput by remember { mutableStateOf("") }
        var customTitleInput by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            containerColor = DarkSurface,
            title = {
                Text("Create Watch Room", fontWeight = FontWeight.Bold, color = TextPrimary)
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Select a video to stream:",
                        fontSize = 13.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    SAMPLE_MOVIES.forEach { movie ->
                        val isSelected = !isCustomUrl && selectedMovie.id == movie.id
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    isCustomUrl = false
                                    selectedMovie = movie
                                },
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) AccentIndigo.copy(alpha = 0.25f) else DarkSurfaceElevated
                            ),
                            border = if (isSelected) CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(AccentCyan, AccentIndigo))) else null
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        isCustomUrl = false
                                        selectedMovie = movie
                                    },
                                    colors = RadioButtonDefaults.colors(selectedColor = AccentCyan)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(movie.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                    Text("${movie.durationSec / 60} mins • ${movie.license}", fontSize = 11.sp, color = TextMuted)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { isCustomUrl = true },
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isCustomUrl) AccentIndigo.copy(alpha = 0.25f) else DarkSurfaceElevated
                        ),
                        border = if (isCustomUrl) CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(AccentCyan, AccentIndigo))) else null
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = isCustomUrl,
                                    onClick = { isCustomUrl = true },
                                    colors = RadioButtonDefaults.colors(selectedColor = AccentCyan)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Custom Direct Stream URL (MP4/HLS)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                            }

                            if (isCustomUrl) {
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = customTitleInput,
                                    onValueChange = { customTitleInput = it },
                                    label = { Text("Movie Title") },
                                    placeholder = { Text("e.g. Open Video") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = customUrlInput,
                                    onValueChange = { customUrlInput = it },
                                    label = { Text("Video URL (.mp4 / .m3u8)") },
                                    placeholder = { Text("https://example.com/stream.mp4") },
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
                        val finalUrl = if (isCustomUrl) customUrlInput.trim() else selectedMovie.url
                        val finalTitle = if (isCustomUrl) (if (customTitleInput.isBlank()) "Custom Stream" else customTitleInput.trim()) else selectedMovie.title
                        if (isCustomUrl && finalUrl.isBlank()) {
                            Toast.makeText(context, "Please enter a valid URL", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        showCreateDialog = false
                        val cleanName = if (displayName.isBlank()) "Host" else displayName.trim()
                        onCreateRoom(guestId, cleanName, finalUrl, finalTitle, serverUrl)
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
                        placeholder = { Text("ws://10.0.2.2:8080 or wss://domain.com") },
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

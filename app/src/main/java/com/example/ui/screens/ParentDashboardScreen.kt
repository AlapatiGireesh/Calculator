package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.DeviceLocation
import com.example.ui.LocationViewModel
import com.example.ui.UiState
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.maps.android.compose.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentDashboardScreen(
    viewModel: LocationViewModel,
    onNavigateToSettings: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val authState by viewModel.authState.collectAsState()
    val pairingCodeState by viewModel.pairingCodeState.collectAsState()
    val parentPairingCode by viewModel.parentPairingCode.collectAsState()
    val childrenLocations by viewModel.childrenLocations.collectAsState()

    val currentUid = remember(authState) { viewModel.getCurrentUserId() }
    val isUserLoggedIn = currentUid != null

    // If logged in, generate/retrieve the pairing code automatically; otherwise, auto sign-in anonymously
    LaunchedEffect(isUserLoggedIn) {
        if (isUserLoggedIn) {
            viewModel.loadParentPairingCode()
        } else {
            viewModel.loginAnonymously()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "GUARDIAN PATH",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 1.2.sp
                        )
                        Text(
                            text = "Family Dashboard",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("parent_back_btn")
                    ) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.testTag("parent_settings_btn")
                    ) {
                        Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings Configuration")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        val pairingCodeResolved = when (val state = pairingCodeState) {
            is UiState.Success -> state.data
            is UiState.Error -> "Error: ${state.message}"
            is UiState.Loading -> "Loading..."
            else -> parentPairingCode ?: viewModel.getPairingCode() ?: "Loading..."
        }

        // Under zero-pairing-code direct-link, we bypass user logins to display real-time telemetry instantly
        ParentMonitorDashboard(
            viewModel = viewModel,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            pairingCode = pairingCodeResolved,
            childrenLocations = childrenLocations,
            isUserLoggedIn = isUserLoggedIn,
            onDisconnect = {
                viewModel.signOut()
                onBack()
            }
        )
    }
}

@Composable
fun ParentMonitorDashboard(
    viewModel: LocationViewModel,
    modifier: Modifier = Modifier,
    pairingCode: String,
    childrenLocations: Map<String, DeviceLocation>,
    isUserLoggedIn: Boolean,
    onDisconnect: () -> Unit
) {
    val context = LocalContext.current
    var selectedChildId by remember { mutableStateOf<String?>(null) }
    var useRadarOnly by remember { mutableStateOf(false) }
    var cameraFocusTrigger by remember { mutableStateOf(0) }
    var workerToDisconnect by remember { mutableStateOf<Pair<String, String>?>(null) }

    // Disconnect confirmation dialog
    if (workerToDisconnect != null) {
        val (targetId, targetName) = workerToDisconnect!!
        AlertDialog(
            onDismissRequest = { workerToDisconnect = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.LinkOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Disconnect Worker?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to disconnect \"$targetName\"? Live tracking will pause until the worker device reconnects using fixed code 1221.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cid = targetId
                        workerToDisconnect = null
                        if (selectedChildId == cid) {
                            selectedChildId = null
                        }
                        viewModel.disconnectWorker(cid, context)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_disconnect_worker_dialog_btn")
                ) {
                    Text("Disconnect Worker", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { workerToDisconnect = null },
                    modifier = Modifier.testTag("cancel_disconnect_worker_dialog_btn")
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Dynamic 1-second real-time ticker to accurately monitor live sync vs disconnection (>30s)
    var currentTimeMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L)
            currentTimeMillis = System.currentTimeMillis()
        }
    }

    // Auto-select the latest connected child device based on updatedAt
    LaunchedEffect(childrenLocations) {
        if (childrenLocations.isNotEmpty() && selectedChildId == null) {
            val latestChildId = childrenLocations.entries.maxByOrNull { it.value.updatedAt }?.key
            if (latestChildId != null) {
                selectedChildId = latestChildId
            }
        }
    }

    val selectedChildLoc = selectedChildId?.let { childrenLocations[it] }

    val elapsedSeconds = if (selectedChildLoc != null && selectedChildLoc.updatedAt > 0) {
        ((currentTimeMillis - selectedChildLoc.updatedAt) / 1000L).coerceAtLeast(0L)
    } else {
        999L
    }
    val isChildDisconnected = selectedChildLoc != null && elapsedSeconds >= 30L

    val selectedCoord = if (selectedChildLoc != null && (selectedChildLoc.latitude != 0.0 || selectedChildLoc.longitude != 0.0)) {
        LatLng(selectedChildLoc.latitude, selectedChildLoc.longitude)
    } else {
        LatLng(37.4219999, -122.0840575) // Default Googleplex coordinates for preview/initial connection fallback
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(selectedCoord, 15f)
    }

    val markerState = rememberMarkerState(position = selectedCoord)

    // Sync marker position when coordinate changes
    LaunchedEffect(selectedCoord) {
        markerState.position = selectedCoord
    }

    // Smoothly update camera if position changes OR if user manual click requests a focus
    LaunchedEffect(selectedCoord, cameraFocusTrigger) {
        cameraPositionState.animate(
            update = CameraUpdateFactory.newLatLngZoom(selectedCoord, 15f),
            durationMs = 1000
        )
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier.weight(1f).verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Pairing Code generator panel at the top
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "MANUAL MONITORING ACTIVE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Receiver Online",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            letterSpacing = 0.5.sp
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Surface(
                            color = Color(0xFF2E7D32),
                            shape = RoundedCornerShape(50),
                        ) {
                            Text(
                                text = "Manual Sync",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Realtime telemetry links active",
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            // Check if pairing code retrieval failed due to permissions or database errors
            val isPermissionDenied = pairingCode.startsWith("Error:") || pairingCode.contains("Permission denied", ignoreCase = true)

            if (isPermissionDenied) {
                var showRulesDialog by remember { mutableStateOf(false) }

                Card(
                    modifier = Modifier.fillMaxWidth().testTag("database_error_panel"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Database Error",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "DATABASE CONFIGURATION FAILURE",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                letterSpacing = 0.5.sp
                            )
                        }

                        Text(
                            text = if (pairingCode.contains("Permission denied", ignoreCase = true)) {
                                "Firebase Realtime Database has rejected this query with 'Permission Denied'. This occurs when the database security rules are locked or restrict write/read permissions."
                            } else {
                                pairingCode.removePrefix("Error:")
                            },
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.9f)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { showRulesDialog = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Setup Security Rules", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = { 
                                    viewModel.loadParentPairingCode()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.15f),
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(0.8f)
                            ) {
                                Text("Retry Sync", fontSize = 11.sp)
                            }
                        }
                    }
                }

                if (showRulesDialog) {
                    AlertDialog(
                        onDismissRequest = { showRulesDialog = false },
                        title = {
                            Text(
                                text = "Required Firebase Rules",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        },
                        text = {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.heightIn(max = 350.dp).verticalScroll(rememberScrollState())
                            ) {
                                Text(
                                    text = "To allow the Parent and Child apps to exchange live tracking data, navigate to your Firebase Console -> Realtime Database -> Rules tab, and replace your rules with the following simple Rules:",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "{\n  \"rules\": {\n    \".read\": \"auth != null\",\n    \".write\": \"auth != null\"\n  }\n}",
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }

                                Text(
                                    text = "Alternatively, if you want structured node-specific Rules:",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "{\n  \"rules\": {\n    \"pairing_codes\": {\n      \".read\": \"auth != null\",\n      \".write\": \"auth != null\"\n    },\n    \"parent_codes\": {\n      \".read\": \"auth != null\",\n      \".write\": \"auth != null\"\n    },\n    \"devices\": {\n      \".read\": \"auth != null\",\n      \".write\": \"auth != null\"\n    },\n    \"locations\": {\n      \".read\": \"auth != null\",\n      \".write\": \"auth != null\"\n    },\n    \"child_access\": {\n      \".read\": \"auth != null\",\n      \".write\": \"auth != null\"\n    },\n    \"sync_requests\": {\n      \".read\": \"auth != null\",\n      \".write\": \"auth != null\"\n    }\n  }\n}",
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showRulesDialog = false }) {
                                Text("Done")
                            }
                        }
                    )
                }
            } else {
                // Beautiful 6-digit Pairing Code Display card for connecting worker devices
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("pairing_code_panel"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Link,
                            contentDescription = "Pairing Code Icon",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "FIXED PAIRING CODE",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = "Fixed code 1221 connects worker devices directly to this dashboard:",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                        ) {
                            Text(
                                text = "1221",
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            // Parent Action Utility section for active remote commands (Sync pull from worker device)
            val loc = selectedChildLoc
            var isSyncingByPull by remember { mutableStateOf(false) }
            val coroutineScope = rememberCoroutineScope()
            Card(
                modifier = Modifier.fillMaxWidth().testTag("sync_selected_worker_panel"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.25f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "SELECTED TRACKER NODE",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary,
                                    letterSpacing = 0.5.sp
                                )
                                if (loc != null && loc.deviceOem.isNotEmpty()) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.secondaryContainer
                                    ) {
                                        Text(
                                            text = loc.deviceOem,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = loc?.childName ?: "No Selected Tracker Device",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            if (loc != null && loc.deviceModel.isNotEmpty()) {
                                Text(
                                    text = "📱 ${loc.deviceModel}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            val df = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
                            val formattedTime = if (loc != null && loc.updatedAt > 0) df.format(Date(loc.updatedAt)) else "Pending"
                            val syncStatusText = if (loc != null) {
                                if (isChildDisconnected) {
                                    "⚠️ Disconnected • Last sync $formattedTime (${elapsedSeconds}s ago)"
                                } else {
                                    "🟢 Live GPS Sync • Last update $formattedTime (${elapsedSeconds}s ago)"
                                }
                            } else {
                                "Pair a tracker device using the 6-digit code above to begin tracking."
                            }
                            Text(
                                text = syncStatusText,
                                fontSize = 11.sp,
                                fontWeight = if (isChildDisconnected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isChildDisconnected) Color(0xFFC62828) else MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                            )
                        }

                        Button(
                            onClick = {
                                val parentId = viewModel.getOwnerId()
                                val childId = selectedChildId ?: ""
                                if (parentId.isNotEmpty() && childId.isNotEmpty()) {
                                    isSyncingByPull = true
                                    viewModel.triggerLocationPull(parentId, childId, context)
                                    coroutineScope.launch {
                                        kotlinx.coroutines.delay(2000)
                                        isSyncingByPull = false
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary
                            ),
                            shape = RoundedCornerShape(8.dp),
                            enabled = !isSyncingByPull && loc != null,
                            modifier = Modifier.testTag("parent_pull_sync_btn")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (isSyncingByPull) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = MaterialTheme.colorScheme.onSecondary,
                                        strokeWidth = 2.dp
                                    )
                                    Text("Requesting...", fontSize = 12.sp)
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Sync Request",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text("Pull Live GPS", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f),
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val isAllowed = if (selectedChildId != null) {
                            viewModel.childAccessStates.collectAsState().value[selectedChildId] ?: false
                        } else {
                            false
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "ALLOW WORKER APP INTERFACE",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary,
                                    letterSpacing = 0.5.sp
                                )
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (isAllowed) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                                ) {
                                    Text(
                                        text = if (isAllowed) "UNLOCKED" else "LOCKED (HIDDEN)",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isAllowed) Color(0xFF2E7D32) else Color(0xFFC62828),
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            Text(
                                text = if (isAllowed) "Child phone can open app interface" else "App is locked in Hidden Apps on child phone",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                            )
                        }
                        Switch(
                            checked = isAllowed,
                            onCheckedChange = { allowed ->
                                selectedChildId?.let { cid ->
                                    viewModel.setChildAccessPermission(cid, allowed, context)
                                }
                            },
                            enabled = loc != null,
                            modifier = Modifier.scale(0.85f).testTag("parent_toggle_child_access")
                        )
                    }

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f),
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "WORKER CONNECTION CONTROL",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = "Disconnect this worker to stop location pulling. Worker must re-enter pairing code to reconnect.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                selectedChildId?.let { cid ->
                                    workerToDisconnect = Pair(cid, loc?.childName ?: "Worker")
                                }
                            },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(8.dp),
                            enabled = loc != null,
                            modifier = Modifier.testTag("parent_disconnect_worker_btn")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LinkOff,
                                    contentDescription = "Disconnect Worker",
                                    modifier = Modifier.size(16.dp)
                                )
                                Text("Disconnect", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // 30-Second Disconnection Alert Banner
            if (loc != null && isChildDisconnected) {
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("child_disconnected_alert_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFFFEBEE)
                    ),
                    border = BorderStroke(1.5.dp, Color(0xFFE53935))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color(0xFFFFCDD2),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.SignalCellularConnectedNoInternet0Bar,
                                    contentDescription = "Disconnected",
                                    tint = Color(0xFFC62828),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "CHILD DEVICE DISCONNECTED",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 12.sp,
                                    color = Color(0xFFB71C1C),
                                    letterSpacing = 0.5.sp
                                )
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color(0xFFC62828)
                                ) {
                                    Text(
                                        text = "OFFLINE",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            Text(
                                text = "No GPS sync received in the last ${elapsedSeconds}s (threshold: 30s). Tap 'Pull Live GPS' above to ping the device.",
                                fontSize = 11.sp,
                                color = Color(0xFF37474F),
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            }

            // Paired nodes directory list
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 280.dp),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Paired Workers Directory",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    if (childrenLocations.isEmpty()) {
                        // Empty state helper
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.People,
                                contentDescription = "No workers paired",
                                modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No tracking devices linked.",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Ensure the Worker profile starts sharing coordinates. Use Pull Live GPS to sync.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(childrenLocations.entries.toList()) { (childId, loc) ->
                                val isSelected = selectedChildId == childId
                                ChildDirectoryRow(
                                    name = loc.childName.ifEmpty { "Worker Node (${childId.take(5)})" },
                                    battery = loc.batteryLevel,
                                    status = loc.status,
                                    lastUpdate = loc.updatedAt,
                                    deviceModel = loc.deviceModel,
                                    deviceOem = loc.deviceOem,
                                    isCloaked = loc.isCloaked,
                                    selected = isSelected,
                                    onClick = { 
                                        selectedChildId = childId
                                        cameraFocusTrigger++
                                    },
                                    onDisconnect = {
                                        workerToDisconnect = Pair(childId, loc.childName.ifEmpty { "Worker Node" })
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Parent Options Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Live Mapping Status",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.primary
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Stealth Radar", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Switch(
                    checked = useRadarOnly,
                    onCheckedChange = { useRadarOnly = it },
                    modifier = Modifier.scale(0.8f).testTag("radar_toggle_switch")
                )
            }
        }

        // Live Positions Map or Radar Panel
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .background(Color.Black, shape = RoundedCornerShape(24.dp))
                .clip(RoundedCornerShape(24.dp))
                .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline), shape = RoundedCornerShape(24.dp))
                .testTag("layout_tracking_area")
        ) {
            if (childrenLocations.isEmpty()) {
                // When NO child is paired: DO NOT SHOW ANY LOCATION OR RANDOM PIN
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        modifier = Modifier.size(64.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isUserLoggedIn) Icons.Default.Search else Icons.Default.LocationOff,
                                contentDescription = "Searching",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (isUserLoggedIn) "Searching for Paired Devices..." else "No Child Device Paired",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isUserLoggedIn) 
                            "Login successful. Waiting for your paired worker devices to send GPS coordinates to Firebase..." 
                            else "Enter Pairing Code \"$pairingCode\" on the child phone to connect. Once paired, live GPS location will be streamed continuously 24/7.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        lineHeight = 16.sp
                    )
                    
                    if (isUserLoggedIn) {
                        Spacer(modifier = Modifier.height(16.dp))
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                }
            } else if (!useRadarOnly && selectedChildLoc != null) {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    properties = MapProperties(
                        mapType = MapType.NORMAL,
                        isMyLocationEnabled = false
                    ),
                    uiSettings = MapUiSettings(
                        zoomControlsEnabled = true,
                        myLocationButtonEnabled = false,
                        mapToolbarEnabled = true
                    )
                ) {
                    Marker(
                        state = markerState,
                        title = selectedChildLoc.childName,
                        snippet = if (selectedChildLoc.latitude == 0.0 && selectedChildLoc.longitude == 0.0) {
                            "Status: Initializing / Waiting for GPS lock"
                        } else {
                            "Battery: ${selectedChildLoc.batteryLevel}% | Status: ${selectedChildLoc.status}"
                        }
                    )
                }

                // Badge indicator overlay
                Box(
                    modifier = Modifier
                        .padding(12.dp)
                        .background(
                            if (isChildDisconnected) Color(0xFFFFEBEE) else MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .border(
                            1.dp,
                            if (isChildDisconnected) Color(0xFFE53935) else Color(0xFF2E7D32).copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .align(Alignment.TopEnd)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    color = if (isChildDisconnected) Color(0xFFC62828) else Color(0xFF2E7D32),
                                    shape = RoundedCornerShape(50)
                                )
                        )
                        Text(
                            text = if (isChildDisconnected) {
                                "DISCONNECTED (${elapsedSeconds}s ago)"
                            } else if (selectedChildLoc.latitude == 0.0 && selectedChildLoc.longitude == 0.0) {
                                "Syncing GPS Coordinate..."
                            } else {
                                "LIVE GPS SYNC ACTIVE"
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isChildDisconnected) Color(0xFFC62828) else Color(0xFF2E7D32)
                        )
                    }
                }
            } else {
                // FALLBACK visual Radar Screen
                StealthRadarScreen(
                    childName = selectedChildLoc?.childName ?: "No paired nodes",
                    batteryPct = selectedChildLoc?.batteryLevel ?: 100,
                    status = selectedChildLoc?.status ?: "Scanning...",
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Sign Out and clean up
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(
                onClick = onDisconnect,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.testTag("parent_logout_btn")
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.ExitToApp, contentDescription = "Log Out")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Disconnect Profile", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun ChildDirectoryRow(
    name: String,
    battery: Int,
    status: String,
    lastUpdate: Long,
    deviceModel: String = "",
    deviceOem: String = "",
    isCloaked: Boolean = true,
    selected: Boolean,
    onClick: () -> Unit,
    onDisconnect: (() -> Unit)? = null
) {
    val df = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val formattedTime = if (lastUpdate > 0) df.format(Date(lastUpdate)) else "Pending"
    val elapsedSec = if (lastUpdate > 0) ((System.currentTimeMillis() - lastUpdate) / 1000L).coerceAtLeast(0L) else 999L
    val isDisconnected = elapsedSec >= 30L

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("child_row_${name}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
            }
        ),
        border = if (selected) {
            CardDefaults.outlinedCardBorder().copy(
                brush = Brush.linearGradient(
                    listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)
                )
            )
        } else {
            null
        }
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                // Status point icon (Green if synced within 30s, Red if disconnected >30s)
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(
                            color = if (!isDisconnected) {
                                Color(0xFF2E7D32)
                            } else {
                                Color(0xFFC62828)
                            },
                            shape = RoundedCornerShape(50)
                        )
                )

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (deviceOem.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = deviceOem,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }

                    if (deviceModel.isNotEmpty()) {
                        Text(
                            text = "📱 $deviceModel",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (battery > 20) Icons.Default.BatteryFull else Icons.Default.BatteryAlert,
                            contentDescription = "Battery",
                            tint = if (battery > 20) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(text = "$battery%", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = if (isDisconnected) "• ⚠️ Disconnected (${elapsedSec}s ago)" else "• 🟢 Live ($formattedTime)",
                            fontSize = 11.sp,
                            fontWeight = if (isDisconnected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isDisconnected) Color(0xFFC62828) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (onDisconnect != null) {
                    IconButton(
                        onClick = onDisconnect,
                        modifier = Modifier.testTag("disconnect_worker_row_${name}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.LinkOff,
                            contentDescription = "Disconnect Worker",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.85f)
                        )
                    }
                }
                IconButton(onClick = onClick) {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Focus",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

/**
 * A beautiful dynamic custom Radar drawing board that simulates high-tech locating
 * sweep lines on Canvas.
 */
@Composable
fun StealthRadarScreen(
    childName: String,
    batteryPct: Int,
    status: String,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition()
    
    // Animate sweep rotation angle
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteSpec()
    )

    // Animate pulse range size
    val pulseSize by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteAlphaSpec()
    )

    Box(
        modifier = modifier.background(Color(0xFF0C1310))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = size.minDimension / 2.3f

            // 1. Draw static grid rings
            drawCircle(color = Color(0xFF1E3524), radius = radius, center = center, style = Stroke(1.5f))
            drawCircle(color = Color(0xFF132318), radius = radius * 0.66f, center = center, style = Stroke(1f))
            drawCircle(color = Color(0xFF0F1E14), radius = radius * 0.33f, center = center, style = Stroke(0.5f))

            // 2. Draw static crosshair lines
            drawLine(
                color = Color(0xFF12231A),
                start = Offset(center.x - radius, center.y),
                end = Offset(center.x + radius, center.y),
                strokeWidth = 1f
            )
            drawLine(
                color = Color(0xFF12231A),
                start = Offset(center.x, center.y - radius),
                end = Offset(center.x, center.y + radius),
                strokeWidth = 1f
            )

            // 3. Draw pulsating circles
            drawCircle(
                color = Color(0xFF43A047).copy(alpha = 0.15f * (1f - pulseSize)),
                radius = radius * pulseSize,
                center = center
            )

            // 4. Draw active sweep line
            val endX = center.x + radius * cos(Math.toRadians(angle.toDouble())).toFloat()
            val endY = center.y + radius * sin(Math.toRadians(angle.toDouble())).toFloat()
            drawLine(
                color = Color(0xFF4CAF50).copy(alpha = 0.8f),
                start = center,
                end = Offset(endX, endY),
                strokeWidth = 2.5f
            )

            // 5. Blinking target point
            val targetLoc = Offset(center.x + radius * 0.45f, center.y - radius * 0.35f)
            // Blend opacity when sweep intersects target
            drawCircle(
                color = Color(0xFF81C784),
                radius = 7.dp.toPx(),
                center = targetLoc
            )
            drawCircle(
                color = Color(0xFFE8F5E9),
                radius = 3.dp.toPx(),
                center = targetLoc
            )
        }

        // Radar display labels overlay
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        ) {
            Text(
                text = "Target Device: $childName",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = Color(0xFF81C784)
            )
            Text(
                text = "Battery Status: $batteryPct% | $status",
                fontSize = 11.sp,
                color = Color(0xFF66BB6A)
            )
        }

        Box(
            modifier = Modifier
                .padding(16.dp)
                .background(Color(0xFF275429), shape = RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp)
                .align(Alignment.TopStart)
        ) {
            Text(
                text = "COORDINATE RADAR ACTIVE",
                fontWeight = FontWeight.Black,
                fontSize = 10.sp,
                color = Color(0xFFA5D6A7)
            )
        }
    }
}

// Private setups to handle infinite radar loops cleanly
private fun infiniteSpec(): InfiniteRepeatableSpec<Float> = infiniteRepeatable(
    animation = tween(durationMillis = 3500, easing = LinearEasing),
    repeatMode = RepeatMode.Restart
)

private fun infiniteAlphaSpec(): InfiniteRepeatableSpec<Float> = infiniteRepeatable(
    animation = tween(durationMillis = 2000, easing = LinearOutSlowInEasing),
    repeatMode = RepeatMode.Restart
)

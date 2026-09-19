package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.service.LocationTrackingService
import com.example.ui.LocationViewModel
import com.example.ui.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChildSetupScreen(
    viewModel: LocationViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var isSharingActive by remember { mutableStateOf(false) }

    // Check Service Running status
    val isServiceRunning = remember {
        mutableStateOf(isServiceRunning(context, LocationTrackingService::class.java))
    }

    LaunchedEffect(Unit) {
        isSharingActive = isServiceRunning.value
    }

    // Checking GPS/Location permissions
    var hasFineLocation by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }

    val pairedParentIdState by viewModel.pairedParentId.collectAsState()
    val isPaired = pairedParentIdState != null

    var hasBackgroundLocation by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    fun triggerStartService() {
        val intent = Intent(context, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_START_TRACKING
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        isSharingActive = true
    }

    // Combined permission launchers
    val backgroundLocationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasBackgroundLocation = granted
        if (granted) {
            if (isPaired) {
                triggerStartService()
                Toast.makeText(context, "GPS location active. Streaming to Owner Hub 24/7.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Background location enabled. Enter pairing code to connect to Owner.", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Background permission: Select 'Allow all the time' in Settings.", Toast.LENGTH_LONG).show()
        }
    }

    val foregroundPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasFineLocation = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true

        if (hasFineLocation) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Request Background Permission next
                backgroundLocationPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            } else {
                hasBackgroundLocation = true
                if (isPaired) {
                    triggerStartService()
                    Toast.makeText(context, "GPS location active. Streaming to Owner Hub 24/7.", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "GPS location enabled. Enter pairing code below to connect to Owner.", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            Toast.makeText(context, "Location permission is required for tracking.", Toast.LENGTH_LONG).show()
        }
    }

    val hasAllGpsPermissions = hasFineLocation && hasBackgroundLocation

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "WORKER TRACKER TERMINAL",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 1.2.sp
                        )
                        Text(
                            text = "Worker Setup",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("child_setup_back_btn")
                    ) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        val pairingState by viewModel.devicePairingState.collectAsState()
        val defaultWorkerName = remember { viewModel.getChildName().ifEmpty { "Worker Device" } }
        var pairingCodeInput by remember { mutableStateOf("1221") }
        var workerNameInput by remember { mutableStateOf(defaultWorkerName) }

        LaunchedEffect(isPaired, hasAllGpsPermissions) {
            if (isPaired && hasAllGpsPermissions) {
                triggerStartService()
            } else if (isPaired && !hasAllGpsPermissions) {
                // Prompt GPS permissions immediately
                val permissionsToRequest = arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
                foregroundPermissionLauncher.launch(permissionsToRequest)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!isPaired) {
                // Not paired or Disconnected - Prompt with Fixed Pairing Code 1221 interface
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("worker_pairing_card"),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Link,
                            contentDescription = "Link Hub",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(44.dp)
                        )

                        Text(
                            text = "Connect to Owner Hub",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Text(
                            text = "This worker device connects to the Owner using fixed pairing code 1221. If disconnected, tap below to reconnect immediately.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        // Highlighted Fixed Code Chip
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Fixed Pairing Code:",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "1221",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        OutlinedTextField(
                            value = pairingCodeInput,
                            onValueChange = { if (it.length <= 8) pairingCodeInput = it },
                            label = { Text("Pairing Code") },
                            placeholder = { Text("1221") },
                            leadingIcon = { Icon(Icons.Default.VpnKey, "Key") },
                            modifier = Modifier.fillMaxWidth().testTag("pairing_code_input"),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = workerNameInput,
                            onValueChange = { workerNameInput = it },
                            label = { Text("Worker / Device Display Name") },
                            placeholder = { Text("e.g. Worker Device") },
                            leadingIcon = { Icon(Icons.Default.Badge, "Name") },
                            modifier = Modifier.fillMaxWidth().testTag("worker_name_input"),
                            singleLine = true
                        )

                        if (pairingState is UiState.Loading) {
                            CircularProgressIndicator(modifier = Modifier.padding(8.dp))
                        }

                        if (pairingState is UiState.Error) {
                            Text(
                                text = (pairingState as UiState.Error).message,
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center
                            )
                        }

                        Button(
                            onClick = {
                                val codeToUse = pairingCodeInput.trim().ifEmpty { "1221" }
                                val nameToUse = workerNameInput.trim().ifEmpty { "Worker Device" }
                                viewModel.pairWithCode(codeToUse, nameToUse)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("submit_pairing_code_btn"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Link, contentDescription = "Connect")
                                Text("Connect with Fixed Code 1221", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else {
                // ==========================================
                // DEVICE PAIRED & CONNECTED TO OWNER
                // ==========================================
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("worker_status_card"),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (hasAllGpsPermissions) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                    ),
                    border = BorderStroke(1.dp, if (hasAllGpsPermissions) Color(0xFF81C784) else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(
                                imageVector = if (hasAllGpsPermissions) Icons.Default.CheckCircle else Icons.Outlined.CheckCircle,
                                contentDescription = "Worker Role Linked",
                                tint = if (hasAllGpsPermissions) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (hasAllGpsPermissions) "GPS Access Provided to Owner" else "Worker Linked to Owner Hub",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = if (hasAllGpsPermissions) Color(0xFF1B5E20) else MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = if (hasAllGpsPermissions)
                                        "24/7 background location streaming active (Fixed Code: 1221)."
                                    else
                                        "Paired with fixed code 1221. Provide GPS & background location access below.",
                                    fontSize = 12.sp,
                                    color = if (hasAllGpsPermissions) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            }
                        }

                        // Reconnect link in case parent was disconnected or changed
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = {
                                    val nameToUse = workerNameInput.trim().ifEmpty { viewModel.getChildName().ifEmpty { "Worker Device" } }
                                    viewModel.pairWithCode("1221", nameToUse)
                                },
                                modifier = Modifier.testTag("reconnect_fixed_code_btn")
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Reconnect with 1221", fontSize = 12.sp)
                            }
                        }
                    }
                }

                // ==========================================
                // PROMINENT BACK BUTTON: WORKER CAN GO TO BACK SIMPLE AS CALCULATOR
                // ==========================================
                if (hasAllGpsPermissions) {
                    Card(
                        modifier = Modifier.fillMaxWidth().testTag("back_to_calculator_card"),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.5.dp, Color(0xFF4CAF50))
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color(0xFF2E7D32),
                                    modifier = Modifier.size(42.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Calculate,
                                            contentDescription = "Calculator Disguise",
                                            tint = Color.White,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Calculator Camouflage Active",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "GPS access is active. Tap below to use app as a calculator.",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // Prominent Back Button: Worker can go to back simple as calculator
                            Button(
                                onClick = onBack,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp)
                                    .testTag("back_to_calculator_btn"),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF2E7D32),
                                    contentColor = Color.White
                                )
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Calculate,
                                        contentDescription = "Calculator"
                                    )
                                    Text(
                                        text = "Back to Calculator",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 15.sp
                                    )
                                }
                            }

                            Text(
                                text = "• App operates simple as a standard calculator.\n" +
                                       "• GPS tracking runs continuously in the background.\n" +
                                       "• Enter PIN 1221 + '=' on calculator to return to this screen.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 17.sp
                            )
                        }
                    }
                }

                // ==========================================
                // GPS PERMISSION / STATUS CARD
                // ==========================================
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("gps_provision_card"),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (hasAllGpsPermissions) Color(0xFFF1F8E9) else MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (hasAllGpsPermissions) Color(0xFFC8E6C9) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = if (hasAllGpsPermissions) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (hasAllGpsPermissions) Icons.Default.Check else Icons.Default.GpsFixed,
                                        contentDescription = "GPS",
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (hasAllGpsPermissions) "GPS Tracking: Active" else "Provide GPS Access to Owner",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = if (hasAllGpsPermissions) Color(0xFF1B5E20) else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (hasAllGpsPermissions)
                                        "Foreground & background GPS tracking authorized and streaming"
                                    else
                                        "Enable GPS & Background location so owner can track device",
                                    fontSize = 12.sp,
                                    color = if (hasAllGpsPermissions) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (!hasAllGpsPermissions) {
                            Button(
                                onClick = {
                                    val permissionsToRequest = arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                    foregroundPermissionLauncher.launch(permissionsToRequest)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("provide_all_gps_btn"),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = Color.White
                                )
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LocationOn,
                                        contentDescription = "GPS Action"
                                    )
                                    Text(
                                        text = "Provide GPS & Background GPS Access",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        } else {
                            OutlinedButton(
                                onClick = onBack,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("simple_back_calculator_btn"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", modifier = Modifier.size(18.dp))
                                    Text("Return to Calculator", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }

                // Disconnect / Reset Pairing Button
                OutlinedButton(
                    onClick = {
                        viewModel.signOut()
                        Toast.makeText(context, "Disconnected tracker. Pairing reset.", Toast.LENGTH_SHORT).show()
                        onBack()
                    },
                    modifier = Modifier.fillMaxWidth().height(46.dp).testTag("child_reset_setup_btn"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.outline
                    )
                ) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "Reset Setup", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Disconnect from Owner / Re-pair", fontSize = 12.sp)
                }
            }
        }
    }
}

// Check if modern service is active on device
private fun isServiceRunning(context: Context, serviceClass: Class<*>): Boolean {
    val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager ?: return false
    for (service in manager.getRunningServices(Int.MAX_VALUE)) {
        if (serviceClass.name == service.service.className) {
            return true
        }
    }
    return false
}

package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.TrackerSettings
import com.example.ui.LocationViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: LocationViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val isFirebaseActive by viewModel.isFirebaseActive.collectAsState()

    var dbUrl by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var projectId by remember { mutableStateOf("") }
    var appId by remember { mutableStateOf("") }

    // Load initial settings
    LaunchedEffect(Unit) {
        val settings = viewModel.getTrackerSettings()
        dbUrl = settings.databaseUrl
        apiKey = settings.apiKey
        projectId = settings.projectId
        appId = settings.appId
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
                            text = "Application Settings",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("settings_back_btn")
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Firebase Status card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isFirebaseActive) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                    } else {
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                    }
                ),
                shape = RoundedCornerShape(24.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (isFirebaseActive) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = "Active",
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier.size(32.dp)
                        )
                        Column {
                            Text(
                                text = "Firebase Connected",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "Cloud syncing & live GPS coordinates are functional.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = "Pending",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(32.dp)
                        )
                        Column {
                            Text(
                                text = "Firebase Offline Simulator Active",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "Setup cloud parameters below to sync real devices, or use simulated mode.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }

            // Active Default Firebase Configuration Panel
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Cloud Database Sync Parameters",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Customize the Google Firebase connection parameters to link physical worker and owner devices. Leave them as default to run in sandbox simulation mode.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = dbUrl,
                        onValueChange = { dbUrl = it },
                        label = { Text("Database URL") },
                        placeholder = { Text("https://your-rtdb.firebaseio.com/") },
                        modifier = Modifier.fillMaxWidth().testTag("firebase_db_url_input"),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("API Key") },
                        placeholder = { Text("AIzaSy...") },
                        modifier = Modifier.fillMaxWidth().testTag("firebase_api_key_input"),
                        singleLine = true
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = projectId,
                            onValueChange = { projectId = it },
                            label = { Text("Project ID") },
                            placeholder = { Text("my-firebase-project") },
                            modifier = Modifier.weight(1f).testTag("firebase_project_id_input"),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = appId,
                            onValueChange = { appId = it },
                            label = { Text("App ID") },
                            placeholder = { Text("1:1234:android:abcd") },
                            modifier = Modifier.weight(1.2f).testTag("firebase_app_id_input"),
                            singleLine = true
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                // Reset to default configurations
                                dbUrl = "https://gurdianlink-default-rtdb.firebaseio.com/"
                                apiKey = "AIzaSyA7xuXY7OsBjZDVe5Y0ssWaanDZMmcLa88"
                                projectId = "gurdianlink"
                                appId = "1:41955023001:android:6c32095a691225cbb40008"
                                viewModel.saveTrackerSettings(
                                    TrackerSettings(
                                        apiKey = apiKey,
                                        databaseUrl = dbUrl,
                                        projectId = projectId,
                                        appId = appId
                                    )
                                )
                                Toast.makeText(context, "Cloud profiles restored to secure defaults!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f).testTag("reset_firebase_defaults_btn"),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Reset Defaults", fontSize = 13.sp)
                        }

                        Button(
                            onClick = {
                                if (dbUrl.isBlank() || apiKey.isBlank() || projectId.isBlank() || appId.isBlank()) {
                                    Toast.makeText(context, "All configuration parameters are required for cloud sync.", Toast.LENGTH_SHORT).show()
                                } else {
                                    viewModel.saveTrackerSettings(
                                        TrackerSettings(
                                            apiKey = apiKey.trim(),
                                            databaseUrl = dbUrl.trim(),
                                            projectId = projectId.trim(),
                                            appId = appId.trim()
                                        )
                                    )
                                    Toast.makeText(context, "Cloud profiles updated and re-initialized successfully!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1.2f).testTag("save_firebase_settings_btn"),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Save & Sync", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

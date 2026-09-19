package com.example

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.launch
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.ui.platform.testTag
import android.widget.Toast
import com.example.util.DeviceHelper
import kotlin.random.Random
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.LocationViewModel
import com.example.ui.ViewModelFactory
import com.example.ui.screens.CalculatorCamouflageScreen
import com.example.ui.screens.ChildSetupScreen
import com.example.ui.screens.ParentDashboardScreen
import com.example.ui.screens.RoleSelectionScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MyApplicationTheme
import com.google.android.gms.maps.MapsInitializer

class MainActivity : ComponentActivity() {

    companion object {
        @Volatile
        var isActivityInForeground: Boolean = false
    }

    private val viewModel: LocationViewModel by viewModels {
        ViewModelFactory(this)
    }

    private fun isEmulator(): Boolean {
        val fingerprint = android.os.Build.FINGERPRINT ?: ""
        val model = android.os.Build.MODEL ?: ""
        val brand = android.os.Build.BRAND ?: ""
        val device = android.os.Build.DEVICE ?: ""
        val product = android.os.Build.PRODUCT ?: ""
        val hardware = android.os.Build.HARDWARE ?: ""
        val board = android.os.Build.BOARD ?: ""
        val manufacturer = android.os.Build.MANUFACTURER ?: ""
        val tags = android.os.Build.TAGS ?: ""

        val isVirtualHardware = hardware.contains("goldfish")
                || hardware.contains("ranchu")
                || hardware.contains("virtio")
                || hardware.contains("cutf_cvm")
                || hardware.contains("vbox")
                || hardware.contains("nox")

        val isVirtualProduct = product.contains("sdk")
                || product.contains("google_sdk")
                || product.contains("emulator")
                || product.contains("simulator")
                || product.contains("vbox")
                || product.contains("nox")
                || product.contains("cuttlefish")
                || product.contains("vsoc")
                || product.contains("gce")

        val isVirtualDevice = device.contains("generic")
                || device.contains("vsoc")
                || device.contains("cuttlefish")
                || device.contains("emulator")

        val isVirtualFingerprint = fingerprint.startsWith("generic")
                || fingerprint.startsWith("unknown")
                || fingerprint.contains("sdk_gphone")
                || fingerprint.contains("cuttlefish")
                || fingerprint.contains("test-keys")

        val isVirtualBoardOrModel = board.contains("vsoc")
                || board.contains("gce")
                || model.contains("google_sdk")
                || model.contains("Emulator")
                || model.contains("Android SDK built for x86")

        val isTestBuild = tags.contains("test-keys")

        return isVirtualHardware || isVirtualProduct || isVirtualDevice || isVirtualFingerprint || isVirtualBoardOrModel || isTestBuild
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        try {
            // Ensure LauncherActivity component is always enabled (normal calculator launcher)
            val aliasComponent = ComponentName(this, "com.example.LauncherActivity")
            packageManager.setComponentEnabledSetting(
                aliasComponent,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
        } catch (e: Exception) {
            Log.w("MainActivity", "Failed to ensure LauncherActivity enabled: ${e.message}")
        }

        try {
            MapsInitializer.initialize(applicationContext, MapsInitializer.Renderer.LATEST) { renderer ->
                Log.d("MainActivity", "Google Maps initialized with renderer: $renderer")
            }
        } catch (e: Exception) {
            Log.w("MainActivity", "MapsInitializer failed to initialize LATEST renderer: ${e.message}")
        }

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val isAppAuthenticated by viewModel.isAppAuthenticated.collectAsState()

                    if (!isAppAuthenticated) {
                        CalculatorCamouflageScreen(
                            viewModel = viewModel,
                            onUnlocked = {
                                // Handled via StateFlow
                            }
                        )
                    } else {
                        val navController = rememberNavController()
                        val userRole by viewModel.userRole.collectAsState()
                        val pairedParentId = viewModel.getPairedParentId()

                        // Route directly based on entered authentication code:
                        // 7999 -> "parent_dashboard" (Owner Hub)
                        // 1221 -> "child_setup" (Worker Tracker)
                        val startDestination = remember(userRole) {
                            when (userRole) {
                                "parent" -> "parent_dashboard"
                                "child" -> "child_setup"
                                else -> "role_selection"
                            }
                        }

                        NavHost(
                            navController = navController,
                            startDestination = startDestination
                        ) {
                            composable("role_selection") {
                                RoleSelectionScreen(
                                    viewModel = viewModel,
                                    onNavigateToParent = {
                                        navController.navigate("parent_dashboard") {
                                            popUpTo("role_selection") { inclusive = true }
                                        }
                                    },
                                    onNavigateToChild = {
                                        navController.navigate("child_setup") {
                                            popUpTo("role_selection") { inclusive = true }
                                        }
                                    },
                                    onNavigateToSettings = {
                                        navController.navigate("settings")
                                    }
                                )
                            }

                            composable("parent_dashboard") {
                                ParentDashboardScreen(
                                    viewModel = viewModel,
                                    onNavigateToSettings = {
                                        navController.navigate("settings")
                                    },
                                    onBack = {
                                        viewModel.lockApp()
                                    }
                                )
                            }

                            composable("child_setup") {
                                ChildSetupScreen(
                                    viewModel = viewModel,
                                    onBack = {
                                        viewModel.lockApp()
                                    }
                                )
                            }

                            composable("settings") {
                                SettingsScreen(
                                    viewModel = viewModel,
                                    onBack = {
                                        navController.popBackStack()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        isActivityInForeground = true
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
    }

    override fun onStop() {
        isActivityInForeground = false
        super.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.lockApp()
    }
}

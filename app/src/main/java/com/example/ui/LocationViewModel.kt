package com.example.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.DeviceLocation
import com.example.data.LocationRepository
import com.example.data.TrackerSettings
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    object Idle : UiState<Nothing>
    object Loading : UiState<Nothing>
    data class Success<out T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

class LocationViewModel(private val repository: LocationRepository) : ViewModel() {

    private val _userRole = MutableStateFlow(repository.getUserRole())
    val userRole: StateFlow<String> = _userRole.asStateFlow()

    private val _isFirebaseActive = MutableStateFlow(repository.isFirebaseInitialized())
    val isFirebaseActive: StateFlow<Boolean> = _isFirebaseActive.asStateFlow()

    private val _authState = MutableStateFlow<UiState<String>>(UiState.Idle)
    val authState: StateFlow<UiState<String>> = _authState.asStateFlow()

    private val _pairingCodeState = MutableStateFlow<UiState<String>>(UiState.Idle)
    val pairingCodeState: StateFlow<UiState<String>> = _pairingCodeState.asStateFlow()

    private val _devicePairingState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val devicePairingState: StateFlow<UiState<Unit>> = _devicePairingState.asStateFlow()

    private val _childrenLocations = MutableStateFlow<Map<String, DeviceLocation>>(emptyMap())
    val childrenLocations: StateFlow<Map<String, DeviceLocation>> = _childrenLocations.asStateFlow()

    private val _isChildAccessAllowed = MutableStateFlow(repository.isChildAccessAllowed())
    val isChildAccessAllowed: StateFlow<Boolean> = _isChildAccessAllowed.asStateFlow()

    private val _childAccessStates = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val childAccessStates: StateFlow<Map<String, Boolean>> = _childAccessStates.asStateFlow()

    private var locationsObserverJob: Job? = null

    private val _parentPairingCode = MutableStateFlow<String?>(null)
    val parentPairingCode: StateFlow<String?> = _parentPairingCode.asStateFlow()

    private val _isAppAuthenticated = MutableStateFlow(false)
    val isAppAuthenticated: StateFlow<Boolean> = _isAppAuthenticated.asStateFlow()

    fun authenticateWithCode(code: String): String? {
        return when (code) {
            "7999" -> {
                selectUserRole("parent")
                _isAppAuthenticated.value = true
                "parent"
            }
            "1221" -> {
                selectUserRole("child")
                _isAppAuthenticated.value = true
                "child"
            }
            else -> null
        }
    }

    fun lockApp() {
        _isAppAuthenticated.value = false
    }

    private val _pairedParentId = MutableStateFlow(repository.getPairedParentId())
    val pairedParentId: StateFlow<String?> = _pairedParentId.asStateFlow()

    init {
        // If already signed in as parent, start listening to children location stream updates
        val ownerId = repository.getOwnerParentId() ?: repository.getCurrentUserId()
        if (getUserRole() == "parent" && ownerId != null) {
            startObservingLocations(ownerId)
        } else if (getUserRole() == "child") {
            if (repository.getPairedParentId() != null) {
                startObservingChildAccessSelf()
                startObservingWorkerDisconnect()
                verifyWorkerStillPaired()
            }
        }
    }

    fun isFirebaseSetup(): Boolean = repository.isFirebaseInitialized()

    fun getTrackerSettings(): TrackerSettings = repository.getCustomSettings()

    fun saveTrackerSettings(settings: TrackerSettings) {
        repository.saveCustomSettings(settings)
        _isFirebaseActive.value = repository.isFirebaseInitialized()
    }

    fun getUserRole(): String {
        return repository.getUserRole()
    }

    fun selectUserRole(role: String) {
        repository.setUserRole(role)
        _userRole.value = role
        if (role == "parent") {
            val pId = repository.getCurrentUserId() ?: repository.getOwnerParentId() ?: "default_parent_123"
            repository.setOwnerParentId(pId)
            startObservingLocations(pId)
            startObservingChildAccess(pId)
        } else if (role == "child") {
            val existingPairedParent = repository.getPairedParentId()
            _pairedParentId.value = existingPairedParent
            if (existingPairedParent != null) {
                startObservingChildAccessSelf()
                startObservingWorkerDisconnect()
                verifyWorkerStillPaired()
            }
        }
    }

    fun getChildName(): String = repository.getChildName()

    fun getPairingCode(): String? = _parentPairingCode.value ?: repository.getPairingCode()

    fun getPairedParentId(): String? = repository.getPairedParentId()

    fun getOwnerId(): String = repository.getCurrentUserId() ?: repository.getOwnerParentId() ?: "default_parent_123"

    fun getCurrentUserEmail(): String? = repository.getCurrentUserEmail()

    fun getCurrentUserId(): String? = repository.getCurrentUserId()

    // --- Authentication ---

    fun login(email: String, authKey: String) {
        _authState.value = UiState.Loading
        repository.signInWithEmail(email, authKey) { success, error ->
            if (success) {
                _authState.value = UiState.Success("Sign-In Successful!")
                selectUserRole("parent")
                loadParentPairingCode()
            } else {
                _authState.value = UiState.Error(error ?: "Unknown sign-in error")
            }
        }
    }

    fun register(email: String, authKey: String) {
        _authState.value = UiState.Loading
        repository.signUpWithEmail(email, authKey) { success, error ->
            if (success) {
                _authState.value = UiState.Success("Sign-Up Successful!")
                selectUserRole("parent")
                loadParentPairingCode()
            } else {
                _authState.value = UiState.Error(error ?: "Unknown signup error")
            }
        }
    }

    fun loginAnonymously() {
        _authState.value = UiState.Loading
        repository.anonymousSignIn { success, error ->
            if (success) {
                _authState.value = UiState.Success("Instant Sign-In Successful!")
                selectUserRole("parent")
                loadParentPairingCode()
            } else {
                if (!repository.isFirebaseInitialized()) {
                    _authState.value = UiState.Success("Sandbox Mode Active")
                    selectUserRole("parent")
                    loadParentPairingCode()
                } else {
                    _authState.value = UiState.Error(error ?: "Anonymous verification failure")
                }
            }
        }
    }

    fun signOut() {
        stopObservingLocations()
        repository.setAppLauncherHidden(false)
        repository.signOut()
        _pairedParentId.value = null
        _parentPairingCode.value = null
        _userRole.value = "none"
        _authState.value = UiState.Idle
        _pairingCodeState.value = UiState.Idle
        _devicePairingState.value = UiState.Idle
    }

    // --- Pairing Codes ---

    fun loadParentPairingCode() {
        val parentId = repository.getCurrentUserId()
        if (parentId != null) {
            startObservingLocations(parentId)
        }
        val fixedCode = "1221"
        repository.setPairingCode(fixedCode)
        _parentPairingCode.value = fixedCode
        _pairingCodeState.value = UiState.Success(fixedCode)
        repository.obtainPairingCodeForParent { code, _ ->
            if (code != null) {
                _parentPairingCode.value = code
                _pairingCodeState.value = UiState.Success(code)
            }
        }
    }

    fun pairWithCode(pairingCode: String, childName: String) {
        _devicePairingState.value = UiState.Loading
        repository.pairDeviceWithCode(pairingCode, childName) { success, error ->
            if (success) {
                _pairedParentId.value = repository.getPairedParentId()
                _devicePairingState.value = UiState.Success(Unit)
                _userRole.value = "child"
                val pId = repository.getPairedParentId()
                if (pId != null) {
                    startObservingLocations(pId)
                    startObservingChildAccessSelf()
                    startObservingWorkerDisconnect()
                }
            } else {
                _devicePairingState.value = UiState.Error(error ?: "Invalid pairing request")
            }
        }
    }

    fun disconnectWorker(context: Context) {
        val cid = repository.getCurrentUserId() ?: ""
        disconnectWorker(cid, context)
    }

    fun disconnectWorker(childId: String, context: Context) {
        val parentId = getOwnerId()
        val targetId = childId.ifEmpty { repository.getCurrentUserId() ?: "" }
        if (targetId.isEmpty()) return

        val db = repository.firebaseDatabase
        if (repository.isFirebaseInitialized() && db != null) {
            viewModelScope.launch {
                try {
                    repository.disconnectWorkerDevice(parentId, targetId) { _ ->
                        // Remove from active UI observation map
                        val map = _childrenLocations.value.toMutableMap()
                        map.remove(targetId)
                        _childrenLocations.value = map

                        // If testing on same device or target is this device
                        if (targetId == repository.getCurrentUserId()) {
                            repository.setPairedParentId(null)
                            _pairedParentId.value = null
                            _devicePairingState.value = UiState.Idle
                            val stopIntent = Intent(context, com.example.service.LocationTrackingService::class.java).apply {
                                action = com.example.service.LocationTrackingService.ACTION_STOP_TRACKING
                            }
                            context.stopService(stopIntent)
                        }

                        Toast.makeText(
                            context,
                            "Worker disconnected. Worker can reconnect with fixed code 1221.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } catch (e: Exception) {
                    Log.e("LocationViewModel", "Failed to disconnect worker: ${e.message}")
                }
            }
        } else {
            // Offline / Sandbox Mode:
            viewModelScope.launch {
                val map = _childrenLocations.value.toMutableMap()
                map.remove(targetId)
                _childrenLocations.value = map

                repository.setPairedParentId(null)
                _pairedParentId.value = null
                _devicePairingState.value = UiState.Idle

                val stopIntent = Intent(context, com.example.service.LocationTrackingService::class.java).apply {
                    action = com.example.service.LocationTrackingService.ACTION_STOP_TRACKING
                }
                context.stopService(stopIntent)

                Toast.makeText(
                    context,
                    "Worker disconnected. Worker can reconnect with fixed code 1221.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    fun unpairChild(childId: String) {
        val parentId = getOwnerId()
        if (parentId.isEmpty() || childId.isEmpty()) return
        val db = repository.firebaseDatabase
        if (repository.isFirebaseInitialized() && db != null) {
            db.getReference("worker_disconnect").child(childId).setValue(System.currentTimeMillis())
            db.getReference("devices").child(childId).child("disconnected").setValue(true)
            db.getReference("locations").child(parentId).child(childId).setValue(null)
            db.getReference("child_access").child(parentId).child(childId).setValue(null)
            db.getReference("sync_requests").child(parentId).child(childId).setValue(null)
        }
        val map = _childrenLocations.value.toMutableMap()
        map.remove(childId)
        _childrenLocations.value = map
    }

    private var workerDisconnectListener: ValueEventListener? = null

    fun startObservingWorkerDisconnect() {
        val childUid = repository.getCurrentUserId()
        val db = repository.firebaseDatabase
        if (childUid == null || db == null || !repository.isFirebaseInitialized()) return

        val ref = db.getReference("worker_disconnect").child(childUid)
        workerDisconnectListener?.let { ref.removeEventListener(it) }

        workerDisconnectListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val ts = snapshot.getValue(Long::class.java) ?: 0L
                    if (ts > 0) {
                        Log.d("LocationViewModel", "Worker unpair signal from owner received.")
                        repository.setPairedParentId(null)
                        _pairedParentId.value = null
                        _devicePairingState.value = UiState.Idle
                        try {
                            snapshot.ref.setValue(null)
                        } catch (e: Exception) {}
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        ref.addValueEventListener(workerDisconnectListener!!)
    }

    fun verifyWorkerStillPaired() {
        val childUid = repository.getCurrentUserId()
        val pairedParent = repository.getPairedParentId()
        val db = repository.firebaseDatabase
        if (childUid == null || pairedParent == null || db == null || !repository.isFirebaseInitialized()) return

        db.getReference("devices").child(childUid).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val isDisconnected = snapshot.child("disconnected").getValue(Boolean::class.java) ?: false
                    val parentId = snapshot.child("pairedParentId").getValue(String::class.java)
                    if (isDisconnected || parentId != pairedParent) {
                        Log.d("LocationViewModel", "Device is marked disconnected or parent unlinked. Resetting pairing.")
                        repository.setPairedParentId(null)
                        _pairedParentId.value = null
                        _devicePairingState.value = UiState.Idle
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    fun clearOtherLocationsExcept(activeChildId: String) {
        val parentId = repository.getPairedParentId() ?: repository.getCurrentUserId() ?: ""
        if (parentId.isEmpty()) return
        val db = repository.firebaseDatabase
        if (repository.isFirebaseInitialized() && db != null) {
            val map = _childrenLocations.value
            map.keys.forEach { cid ->
                if (cid != activeChildId) {
                    db.getReference("locations").child(parentId).child(cid).setValue(null)
                    db.getReference("child_access").child(parentId).child(cid).setValue(null)
                    db.getReference("sync_requests").child(parentId).child(cid).setValue(null)
                    db.getReference("devices").child(cid).setValue(null)
                }
            }
        } else {
            val map = _childrenLocations.value
            _childrenLocations.value = map.filterKeys { it == activeChildId }
        }
    }

    // --- Location Monitoring Subscription ---

    fun startObservingLocations(parentId: String) {
        locationsObserverJob?.cancel()
        
        if (!repository.isFirebaseInitialized() || repository.firebaseDatabase == null) {
            // Offline/Sandbox mode: do NOT show fake locations. Keep childrenLocations empty unless locally paired.
            return
        }

        locationsObserverJob = viewModelScope.launch {
            repository.observeChildrenLocations(parentId)
                .catch { e ->
                    Log.e("LocationViewModel", "Failure in locations flow stream: ${e.message}", e)
                }
                .collect { locationMap ->
                    // Only show paired children locations. If empty, map is empty (no fake data).
                    _childrenLocations.value = locationMap
                }
        }
    }

    fun stopObservingLocations() {
        locationsObserverJob?.cancel()
        locationsObserverJob = null
    }

    // --- Dynamic Parent-Init Pull Sync Request to Worker ---

    fun triggerLocationPull(parentId: String, childId: String, context: android.content.Context) {
        val database = repository.firebaseDatabase
        if (repository.isFirebaseInitialized() && database != null) {
            val timestamp = System.currentTimeMillis()
            viewModelScope.launch {
                try {
                    database.getReference("sync_requests")
                        .child(parentId)
                        .child(childId)
                        .setValue(timestamp)
                        .addOnSuccessListener {
                            Log.d("LocationViewModel", "Direct-pull manual sync request dispatched successfully: $timestamp")
                            android.widget.Toast.makeText(context, "Satellite GPS pull request dispatched successfully to child device!", android.widget.Toast.LENGTH_SHORT).show()
                        }
                        .addOnFailureListener { e ->
                            Log.w("LocationViewModel", "Direct-pull manual sync database write failed", e)
                            android.widget.Toast.makeText(context, "Tracking signal failed to register: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                        }
                } catch (e: Exception) {
                    Log.e("LocationViewModel", "Failed to dispatch tracking pull packet", e)
                }
            }
        } else {
            viewModelScope.launch {
                val map = _childrenLocations.value
                val item = map[childId]
                if (item != null) {
                    android.widget.Toast.makeText(context, "Local GPS Sync Refresh Dispatched", android.widget.Toast.LENGTH_SHORT).show()
                    val refreshed = item.copy(
                        updatedAt = System.currentTimeMillis(),
                        status = "Active (GPS Pulled)"
                    )
                    _childrenLocations.value = map.toMutableMap().apply {
                        put(childId, refreshed)
                    }
                } else {
                    android.widget.Toast.makeText(context, "No device paired to pull GPS from.", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }





    fun startObservingChildAccessSelf() {
        val parentId = repository.getPairedParentId()
        val childUid = repository.getCurrentUserId()
        if (parentId == null || childUid == null || !repository.isFirebaseInitialized()) return
        val db = repository.firebaseDatabase ?: return
        
        db.getReference("child_access").child(parentId).child(childUid)
            .addValueEventListener(object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                    val isAllowed = snapshot.getValue(Boolean::class.java) ?: false
                    repository.setChildAccessAllowed(isAllowed)
                    _isChildAccessAllowed.value = isAllowed
                }

                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
            })
    }

    fun startObservingChildAccess(parentId: String) {
        if (!repository.isFirebaseInitialized()) return
        val db = repository.firebaseDatabase ?: return
        
        db.getReference("child_access").child(parentId)
            .addValueEventListener(object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                    val map = mutableMapOf<String, Boolean>()
                    for (childSnap in snapshot.children) {
                        val key = childSnap.key ?: continue
                        val isAllowed = childSnap.getValue(Boolean::class.java) ?: false
                        map[key] = isAllowed
                    }
                    _childAccessStates.value = map
                }

                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                    Log.w("LocationViewModel", "Child access listener cancelled: ${error.message}")
                }
            })
    }

    fun setChildAccessPermission(childGuid: String, allowed: Boolean, context: Context) {
        val parentId = repository.getPairedParentId() ?: repository.getCurrentUserId() ?: ""
        val db = repository.firebaseDatabase
        if (repository.isFirebaseInitialized() && db != null && parentId.isNotEmpty() && childGuid.isNotEmpty()) {
            viewModelScope.launch {
                db.getReference("child_access")
                    .child(parentId)
                    .child(childGuid)
                    .setValue(allowed)
                    .addOnSuccessListener {
                        val txt = if (allowed) "Child dashboard visibility unlocked!" else "Child dashboard visibility locked!"
                        android.widget.Toast.makeText(context, txt, android.widget.Toast.LENGTH_SHORT).show()
                    }
            }
        } else {
            // Sandbox Mode direct toggle to allow full testing in emulator preview
            repository.setChildAccessAllowed(allowed)
            _isChildAccessAllowed.value = allowed
            _childAccessStates.value = _childAccessStates.value.toMutableMap().apply {
                put(childGuid, allowed)
            }
            val txt = if (allowed) "Sandbox: Unlocked child screen!" else "Sandbox: Cloaked child screen!"
            android.widget.Toast.makeText(context, txt, android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}

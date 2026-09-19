package com.example.data

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import com.example.util.DeviceHelper
import kotlin.random.Random

data class DeviceLocation(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val updatedAt: Long = 0,
    val childName: String = "",
    val batteryLevel: Int = 100,
    val status: String = "Online",
    val deviceModel: String = "",
    val deviceBrand: String = "",
    val deviceOem: String = "",
    val isCloaked: Boolean = true
) {
    // No-arg constructor required by Firebase Realtime Database
    constructor() : this(0.0, 0.0, 0, "", 100, "Online", "", "", "", true)
}

data class TrackerSettings(
    val apiKey: String = "",
    val databaseUrl: String = "",
    val projectId: String = "",
    val appId: String = ""
)

class LocationRepository private constructor(private val context: Context) {

    companion object {
        private const val TAG = "LocationRepository"
        private const val PREFS_NAME = "guardian_link_prefs"
        private const val KEY_ROLE = "user_role" // "parent" or "child" or "none"
        private const val KEY_CHILD_NAME = "child_name"
        private const val KEY_PARENT_ID = "paired_parent_id"
        private const val KEY_PAIRING_CODE = "pairing_code"
        private const val KEY_FIREBASE_DB_URL = "custom_firebase_db_url"
        private const val KEY_FIREBASE_API_KEY = "custom_firebase_api_key"
        private const val KEY_FIREBASE_PROJECT_ID = "custom_firebase_project_id"
        private const val KEY_FIREBASE_APP_ID = "custom_firebase_app_id"
        private const val KEY_APP_HIDDEN = "app_launcher_hidden"
        private const val KEY_LAST_LAT = "last_known_lat"
        private const val KEY_LAST_LNG = "last_known_lng"
        const val FIXED_PAIRING_CODE = "1221"

        @Volatile
        private var INSTANCE: LocationRepository? = null

        fun getInstance(context: Context): LocationRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: LocationRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private var firebaseAuth: FirebaseAuth? = null
    var firebaseDatabase: FirebaseDatabase? = null
        private set

    init {
        initializeFirebase()
    }

    fun isFirebaseInitialized(): Boolean {
        return firebaseAuth != null && firebaseDatabase != null
    }

    private fun isRunningTest(): Boolean {
        return try {
            val isRobolectric = android.os.Build.FINGERPRINT == "robolectric"
            val hasJUnitProp = System.getProperty("robolectric.active") != null || System.getProperty("java.class.path")?.contains("junit") == true
            isRobolectric || hasJUnitProp || (Class.forName("org.junit.Test") != null)
        } catch (e: Exception) {
            false
        }
    }

    fun initializeFirebase(): Boolean {
        try {
            val settings = getCustomSettings()
            
            // Clean up any existing instances to avoid conflicts
            FirebaseApp.getApps(context).forEach { it.delete() }

            val options = FirebaseOptions.Builder()
                .setApiKey(settings.apiKey)
                .setDatabaseUrl(settings.databaseUrl)
                .setProjectId(settings.projectId)
                .setApplicationId(settings.appId)
                .setStorageBucket("${settings.projectId}.firebasestorage.app")
                .build()

            val app = FirebaseApp.initializeApp(context, options)
            firebaseAuth = FirebaseAuth.getInstance(app)
            firebaseDatabase = FirebaseDatabase.getInstance(app, settings.databaseUrl)

            Log.d(TAG, "Firebase initialized with key: ${settings.apiKey.take(8)}...")
            
            autoSignIn()
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Firebase init failed: ${e.message}", e)
        }
        return false
    }

    private fun autoSignIn() {
        val auth = firebaseAuth
        if (auth != null && auth.currentUser == null) {
            auth.signInAnonymously()
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.d(TAG, "Successfully auto-signed in anonymously!")
                    } else {
                        Log.w(TAG, "Auto anonymous sign-in failed: ${task.exception?.message}")
                    }
                }
        }
    }

    fun saveCustomSettings(settings: TrackerSettings) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_FIREBASE_DB_URL, settings.databaseUrl)
            .putString(KEY_FIREBASE_API_KEY, settings.apiKey)
            .putString(KEY_FIREBASE_PROJECT_ID, settings.projectId)
            .putString(KEY_FIREBASE_APP_ID, settings.appId)
            .apply()
        // Re-initialize Firebase with updated credentials
        initializeFirebase()
    }

    fun getCustomSettings(): TrackerSettings {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return TrackerSettings(
            apiKey = prefs.getString(KEY_FIREBASE_API_KEY, "AIzaSyA7xuXY7OsBjZDVe5Y0ssWaanDZMmcLa88") ?: "AIzaSyA7xuXY7OsBjZDVe5Y0ssWaanDZMmcLa88",
            databaseUrl = prefs.getString(KEY_FIREBASE_DB_URL, "https://gurdianlink-default-rtdb.firebaseio.com") ?: "https://gurdianlink-default-rtdb.firebaseio.com",
            projectId = prefs.getString(KEY_FIREBASE_PROJECT_ID, "gurdianlink") ?: "gurdianlink",
            appId = prefs.getString(KEY_FIREBASE_APP_ID, "1:41955023001:android:6c32095a691225cbb40008") ?: "1:41955023001:android:6c32095a691225cbb40008"
        )
    }

    // Role, pairing, and status persistence
    fun getUserRole(): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ROLE, "none") ?: "none"
    }

    fun setUserRole(role: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ROLE, role)
            .apply()
    }

    fun getChildName(): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_CHILD_NAME, "Worker Device") ?: "Worker Device"
    }

    fun setChildName(name: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CHILD_NAME, name)
            .apply()
    }

    fun getPairedParentId(): String? {
        val stored = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_PARENT_ID, null)
        if (stored != null) return stored
        // Parents do not pair to other parents, they observe themselves via getCurrentUserId()
        return null
    }

    fun setPairedParentId(parentId: String?) {
        val editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        if (parentId == null) {
            editor.remove(KEY_PARENT_ID)
        } else {
            editor.putString(KEY_PARENT_ID, parentId)
        }
        editor.commit()
    }

    fun getOwnerParentId(): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString("owner_parent_id", null)
    }

    fun setOwnerParentId(ownerId: String?) {
        val editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        if (ownerId == null) {
            editor.remove("owner_parent_id")
        } else {
            editor.putString("owner_parent_id", ownerId)
        }
        editor.commit()
    }

    fun disconnectWorkerDevice(parentId: String, childId: String, onComplete: (Boolean) -> Unit = {}) {
        val db = firebaseDatabase
        if (db != null) {
            val updates = HashMap<String, Any?>()
            updates["worker_disconnect/$childId"] = System.currentTimeMillis()
            updates["devices/$childId/disconnected"] = true
            updates["locations/$parentId/$childId"] = null
            updates["sync_requests/$parentId/$childId"] = null
            updates["child_access/$parentId/$childId"] = null

            db.reference.updateChildren(updates).addOnCompleteListener { task ->
                onComplete(task.isSuccessful)
            }
        } else {
            onComplete(true)
        }
    }

    fun getPairingCode(): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_PAIRING_CODE, FIXED_PAIRING_CODE) ?: FIXED_PAIRING_CODE
    }

    fun getLastSavedLocation(): Pair<Double, Double> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        // Default to a cozy centered latitude/longitude (standard Googleplex coordinates) inside emulator or physical
        val lat = prefs.getFloat(KEY_LAST_LAT, 37.4219999f).toDouble()
        val lng = prefs.getFloat(KEY_LAST_LNG, -122.0840575f).toDouble()
        return Pair(lat, lng)
    }

    fun setLastSavedLocation(lat: Double, lng: Double) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putFloat(KEY_LAST_LAT, lat.toFloat())
            .putFloat(KEY_LAST_LNG, lng.toFloat())
            .apply()
    }

    fun setPairingCode(code: String?) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PAIRING_CODE, code)
            .apply()
    }

    fun isAppLauncherHidden(): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_APP_HIDDEN, false)
    }

    fun isChildAccessAllowed(): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean("child_access_allowed", false)
    }

    fun setChildAccessAllowed(allowed: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("child_access_allowed", allowed)
            .apply()
    }

    fun setAppLauncherHidden(hidden: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_APP_HIDDEN, hidden)
            .commit()
    }

    fun getCurrentUserId(): String? {
        val auth = firebaseAuth
        if (auth != null && auth.currentUser != null) {
            // Real logged in email or anonymous user
            return auth.currentUser!!.uid
        }
        
        // Otherwise use a persistent device-specific ID (Sandbox / Shared Mode)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val role = getUserRole()
        val key = if (role == "parent") "stable_parent_id" else "stable_worker_id"
        var stableId = prefs.getString(key, null)
        if (stableId == null) {
            val prefix = if (role == "parent") "parent_" else "worker_"
            val randomSegment = java.util.UUID.randomUUID().toString().replace("-", "").take(8).lowercase()
            stableId = prefix + randomSegment
            prefs.edit().putString(key, stableId).apply()
        }
        return stableId
    }

    fun getCurrentUserEmail(): String? {
        return firebaseAuth?.currentUser?.email
    }

    // --- Authentication Actions ---

    fun signInWithEmail(email: String, authKey: String, onResult: (Boolean, String?) -> Unit) {
        val auth = firebaseAuth
        if (auth == null) {
            onResult(false, "Firebase is not initialized. Please configure it in Settings first.")
            return
        }
        auth.signInWithEmailAndPassword(email, authKey)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    onResult(true, null)
                } else {
                    onResult(false, task.exception?.localizedMessage ?: "Unknown sign-in error")
                }
            }
    }

    fun signUpWithEmail(email: String, authKey: String, onResult: (Boolean, String?) -> Unit) {
        val auth = firebaseAuth
        if (auth == null) {
            onResult(false, "Firebase is not initialized. Please configure it in Settings first.")
            return
        }
        auth.createUserWithEmailAndPassword(email, authKey)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    onResult(true, null)
                } else {
                    onResult(false, task.exception?.localizedMessage ?: "Unknown registration error")
                }
            }
    }

    fun anonymousSignIn(onResult: (Boolean, String?) -> Unit) {
        val auth = firebaseAuth
        if (auth == null) {
            onResult(false, "Firebase is not initialized. Please configure it in Settings first.")
            return
        }
        auth.signInAnonymously()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    onResult(true, null)
                } else {
                    onResult(false, task.exception?.localizedMessage ?: "Unknown anonymous sign-in error")
                }
            }
    }

    fun signOut() {
        firebaseAuth?.signOut()
        setPairedParentId(null)
        setPairingCode(null)
        setUserRole("none")
    }

    // --- Pairing Logic ---

    /**
     * Registers the fixed pairing code (1221) for the parent in Firebase and locally.
     */
    fun obtainPairingCodeForParent(onResult: (String?, String?) -> Unit) {
        val parentUid = getCurrentUserId() ?: getOwnerParentId() ?: "default_parent_123"
        val email = getCurrentUserEmail() ?: "Anonymous"
        val code = FIXED_PAIRING_CODE

        setPairingCode(code)
        onResult(code, null)

        val db = firebaseDatabase
        if (db != null) {
            val codeDetails = mapOf(
                "parentId" to parentUid,
                "parentEmail" to email,
                "createdAt" to System.currentTimeMillis()
            )
            db.getReference("pairing_codes").child(code).setValue(codeDetails)
            db.getReference("parent_codes").child(parentUid).setValue(code)
            setOwnerParentId(parentUid)
            db.getReference("latest_owner_parent_id").setValue(parentUid)
        }
    }

    /**
     * Called on the Worker/Child device to pair itself with the Owner/Parent using the fixed code (1221).
     * If the worker was disconnected, reconnecting with 1221 seamlessly re-establishes the telemetry link.
     */
    fun pairDeviceWithCode(pairingCode: String, childName: String, onResult: (Boolean, String?) -> Unit) {
        val cleanCode = pairingCode.trim().ifEmpty { FIXED_PAIRING_CODE }
        if (cleanCode.length < 4 || cleanCode.length > 8) {
            onResult(false, "Pairing code must be at least 4 digits (e.g. 1221).")
            return
        }

        val db = firebaseDatabase
        println("DEBUG pairDeviceWithCode: db=$db")
        if (db == null) {
            // When Firebase database is not initialized, run in automatic offline/sandbox simulated pairing mode
            Log.d(TAG, "Database is offline, completing simulated sandbox/offline pairing with fixed code 1221")
            setPairedParentId("default_parent_123")
            setChildName(childName)
            setUserRole("child")
            onResult(true, null)
            return
        }

        val auth = firebaseAuth
        if (auth == null) {
            onResult(false, "Authentication service is unavailable.")
            return
        }

        // Robust sign-in first pattern to satisfy Firebase Database security rules (.read and .write rules require auth)
        val proceedWithQuery = { uid: String ->
            val codeRef = db.getReference("pairing_codes").child(cleanCode)
            codeRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val parentId = snapshot.child("parentId").getValue(String::class.java)

                    if (parentId == null && cleanCode == FIXED_PAIRING_CODE) {
                        // If parent hasn't explicitly opened the pairing screen yet, fallback to latest_owner_parent_id or cached owner
                        db.getReference("latest_owner_parent_id").addListenerForSingleValueEvent(object : ValueEventListener {
                            override fun onDataChange(ownerSnap: DataSnapshot) {
                                val fallbackParentId = ownerSnap.getValue(String::class.java)
                                    ?: getOwnerParentId()
                                    ?: "default_parent_123"

                                val matchedChildUid = getCurrentUserId() ?: uid
                                finalizeDevicePairing(db, fallbackParentId, matchedChildUid, childName, cleanCode, onResult)
                            }

                            override fun onCancelled(error: DatabaseError) {
                                val matchedChildUid = getCurrentUserId() ?: uid
                                val fallbackParentId = getOwnerParentId() ?: "default_parent_123"
                                finalizeDevicePairing(db, fallbackParentId, matchedChildUid, childName, cleanCode, onResult)
                            }
                        })
                        return
                    }

                    if (parentId == null) {
                        onResult(false, "Invalid pairing code. Please ensure Owner is initialized.")
                        return
                    }

                    val matchedChildUid = getCurrentUserId() ?: uid
                    finalizeDevicePairing(db, parentId, matchedChildUid, childName, cleanCode, onResult)
                }

                override fun onCancelled(error: DatabaseError) {
                    val matchedChildUid = getCurrentUserId() ?: uid
                    val fallbackParentId = getOwnerParentId() ?: "default_parent_123"
                    finalizeDevicePairing(db, fallbackParentId, matchedChildUid, childName, cleanCode, onResult)
                }
            })
        }

        if (auth.currentUser == null) {
            auth.signInAnonymously().addOnCompleteListener { authTask ->
                if (authTask.isSuccessful) {
                    val uid = authTask.result?.user?.uid ?: "unknown_uid"
                    proceedWithQuery(uid)
                } else {
                    onResult(false, "Authentication for pairing failed: ${authTask.exception?.message}")
                }
            }
        } else {
            proceedWithQuery(auth.currentUser!!.uid)
        }
    }

    private fun finalizeDevicePairing(
        db: FirebaseDatabase,
        parentId: String,
        childUid: String,
        childName: String,
        pairingCode: String,
        onResult: (Boolean, String?) -> Unit
    ) {
        val devInfo = DeviceHelper.getDeviceDetail()
        val deviceData = mapOf(
            "childName" to childName,
            "pairedParentId" to parentId,
            "pairedAt" to System.currentTimeMillis(),
            "deviceModel" to devInfo.displayModel,
            "deviceBrand" to devInfo.brand,
            "deviceOem" to devInfo.oemType.displayName,
            "disconnected" to false
        )

        // Write to device registry
        db.getReference("devices").child(childUid).setValue(deviceData)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    // Clear any prior disconnect notification for this child device
                    db.getReference("worker_disconnect").child(childUid).setValue(null)

                    // Save locally in SharedPreferences
                    setPairedParentId(parentId)
                    setChildName(childName)
                    setUserRole("child")
                    
                    // Put an initial coordinate update
                    val initialLoc = DeviceLocation(
                        latitude = 0.0,
                        longitude = 0.0,
                        updatedAt = System.currentTimeMillis(),
                        childName = childName,
                        batteryLevel = 100,
                        status = "Online",
                        deviceModel = devInfo.displayModel,
                        deviceBrand = devInfo.brand,
                        deviceOem = devInfo.oemType.displayName,
                        isCloaked = false
                    )
                    
                    db.getReference("locations").child(parentId).child(childUid).setValue(initialLoc)
                    db.getReference("child_access").child(parentId).child(childUid).setValue(true)
                    db.getReference("sync_requests").child(parentId).child(childUid).setValue(System.currentTimeMillis())

                    onResult(true, null)
                } else {
                    onResult(false, "Could not set pairing linkage: ${task.exception?.message}")
                }
            }
    }

    // --- Core Location and Stream Broadcast ---

    /**
     * Upload live coordinates to Firebase database under `/locations/{parent_id}/{child_id}`.
     */
    fun updateLiveLocation(
        parentId: String,
        childUid: String,
        childName: String,
        latitude: Double,
        longitude: Double,
        batteryLevel: Int,
        status: String = "Online"
    ) {
        if (firebaseDatabase == null) {
            initializeFirebase()
        }
        val db = firebaseDatabase ?: return
        val devInfo = DeviceHelper.getDeviceDetail()
        val loc = DeviceLocation(
            latitude = latitude,
            longitude = longitude,
            updatedAt = System.currentTimeMillis(),
            childName = childName,
            batteryLevel = batteryLevel,
            status = status,
            deviceModel = devInfo.displayModel,
            deviceBrand = devInfo.brand,
            deviceOem = devInfo.oemType.displayName,
            isCloaked = isAppLauncherHidden()
        )
        setLastSavedLocation(latitude, longitude)
        db.getReference("locations").child(parentId).child(childUid).setValue(loc)
            .addOnSuccessListener {
                Log.d(TAG, "Uploaded fine coordinates: Lat:$latitude, Lng:$longitude")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to upload live location coordinate", e)
            }
    }

    /**
     * Continuous stream flow listening to location updates of children mapped to this parent.
     */
    fun observeChildrenLocations(parentId: String): Flow<Map<String, DeviceLocation>> = callbackFlow {
        if (firebaseDatabase == null) {
            initializeFirebase()
        }
        val db = firebaseDatabase
        if (db == null) {
            close(Exception("Database not initialized"))
            return@callbackFlow
        }

        val locationsRef = db.getReference("locations").child(parentId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val map = mutableMapOf<String, DeviceLocation>()
                for (childSnapshot in snapshot.children) {
                    val key = childSnapshot.key ?: continue
                    val location = childSnapshot.getValue(DeviceLocation::class.java)
                    if (location != null) {
                        map[key] = location
                    }
                }
                trySend(map)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        locationsRef.addValueEventListener(listener)
        awaitClose {
            locationsRef.removeEventListener(listener)
        }
    }
}

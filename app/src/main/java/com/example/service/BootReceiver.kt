package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.data.LocationRepository

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == "com.htc.intent.action.QUICKBOOT_POWERON" ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            Log.d(TAG, "Device booted or app updated ($action)! Checking auto-start configurations...")
            val repo = LocationRepository.getInstance(context)
            
            // Auto restart tracking if configured as a child and successfully paired
            if (repo.getUserRole() == "child" && repo.getPairedParentId() != null) {
                Log.d(TAG, "Device is paired as child. Initializing tracking service...")
                val serviceIntent = Intent(context, LocationTrackingService::class.java).apply {
                    this.action = LocationTrackingService.ACTION_START_TRACKING
                }
                
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                    Log.d(TAG, "LocationTrackingService started successfully on boot.")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start tracking service on device boot: ${e.message}", e)
                }
            } else {
                Log.d(TAG, "Device is not configured or paired for background tracking. Skipping auto-start.")
            }
        } else if (action == "android.provider.Telephony.SECRET_CODE") {
            Log.d(TAG, "Secret dialer code *#*#1221#*#* dialed! Unlocking worker app interface for owner...")
            val prefs = context.getSharedPreferences("guardian_link_prefs", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("child_access_allowed", true).apply()
            
            val launchIntent = Intent(context, com.example.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            context.startActivity(launchIntent)
        }
    }
}

package com.example

import android.app.Application
import android.util.Log
import com.google.android.gms.maps.MapsInitializer

class GuardianApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        initializeGoogleMapsRenderer()
    }

    private fun initializeGoogleMapsRenderer() {
        try {
            MapsInitializer.initialize(applicationContext, MapsInitializer.Renderer.LATEST) { renderer ->
                when (renderer) {
                    MapsInitializer.Renderer.LATEST -> Log.i(TAG, "Google Maps initialized with LATEST modern renderer")
                    MapsInitializer.Renderer.LEGACY -> Log.w(TAG, "Google Maps initialized with LEGACY renderer")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "MapsInitializer error: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "GuardianApplication"
    }
}

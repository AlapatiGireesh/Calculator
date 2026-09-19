package com.example

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log

class LauncherActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val targetIntent = Intent(this, MainActivity::class.java).apply {
            action = intent?.action
            data = intent?.data
            type = intent?.type
            intent?.extras?.let { putExtras(it) }
        }
        startActivity(targetIntent)
        finish()
    }
}

package com.rendy.classnote.notification

import android.app.Activity
import android.content.Intent
import android.os.Bundle

class DismissShadeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startForegroundService(Intent(this, FloatingQuickAddService::class.java).apply {
            action = FloatingQuickAddService.ACTION_SHOW
        })
        finish()
    }
}

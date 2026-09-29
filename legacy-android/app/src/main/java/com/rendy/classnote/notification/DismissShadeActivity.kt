package com.rendy.classnote.notification

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings

class DismissShadeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Settings.canDrawOverlays(this)) {
            startForegroundService(Intent(this, FloatingQuickAddService::class.java).apply {
                action = FloatingQuickAddService.ACTION_SHOW
            })
        } else {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")))
        }
        finish()
    }
}

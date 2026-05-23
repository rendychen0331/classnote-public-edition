package com.rendy.classnote.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.rendy.classnote.data.UpdateChecker

class InstallApkReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_INSTALL = "com.rendy.classnote.action.INSTALL_APK"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_INSTALL) return
        val apkFile = UpdateChecker.getDownloadedApkFile(context) ?: return
        UpdateChecker.triggerInstallFromFile(context, apkFile)
    }
}

package com.rendy.classnote.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.rendy.classnote.R
import com.rendy.classnote.ui.MainActivity

class AutoUpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val info = UpdateChecker.checkForUpdate(applicationContext, force = true)
            ?: return Result.success()
        if (!info.isNewer) return Result.success()
        showUpdateNotification(applicationContext, info.tagName)
        return Result.success()
    }

    private fun showUpdateNotification(context: Context, tagName: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID, "應用程式更新", NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "ClassNote 有新版本時通知"
            enableVibration(false)
        }
        manager.createNotificationChannel(channel)

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "settings")
        }
        val tap = PendingIntent.getActivity(
            context, NOTIF_ID, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("ClassNote 有新版本")
            .setContentText("$tagName 已發布，點擊前往設定手動更新")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(tap)
            .build()
        manager.notify(NOTIF_ID, n)
    }

    companion object {
        const val WORK_NAME = "classnote_auto_update"
        private const val CHANNEL_ID = "classnote_updates"
        private const val NOTIF_ID = 9_003
    }
}

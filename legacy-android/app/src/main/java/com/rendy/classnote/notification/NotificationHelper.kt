package com.rendy.classnote.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.rendy.classnote.ClassNoteApplication
import com.rendy.classnote.R
import com.rendy.classnote.ui.MainActivity
import com.rendy.classnote.ui.ReminderAlarmActivity

object NotificationHelper {

    const val CHANNEL_ID = "classnote_reminders"
    const val CHANNEL_NAME = "提醒事項通知"

    private const val CHANNEL_AI_ID = "classnote_ai_status"
    private const val AI_STATUS_NOTIF_ID = 9_001
    private const val PENDING_CONFIRM_NOTIF_ID = 9_002

    fun createAiStatusChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_AI_ID,
            "AI 辨識狀態",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "ClassNote AI 通知辨識進度"
            enableVibration(false)
            setSound(null, null)
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    fun showAiProcessing(context: Context, count: Int) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val n = NotificationCompat.Builder(context, CHANNEL_AI_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("AI 辨識中…")
            .setContentText("已偵測到 $count 則通知，正在分析")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setProgress(0, 0, true)
            .build()
        manager.notify(AI_STATUS_NOTIF_ID, n)
    }

    fun showAiResult(context: Context, added: Int, titles: List<String>) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val tapIntent = Intent(context, com.rendy.classnote.ui.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "reminders")
        }
        val tap = PendingIntent.getActivity(
            context, AI_STATUS_NOTIF_ID, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val body = titles.take(3).joinToString("、").let {
            if (titles.size > 3) "$it 等" else it
        }
        val n = NotificationCompat.Builder(context, CHANNEL_AI_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("已新增 $added 個提醒")
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(tap)
            .build()
        manager.notify(AI_STATUS_NOTIF_ID, n)
    }

    fun showAiNoResult(context: Context, recognized: List<String>) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val body = if (recognized.isEmpty()) {
            "未偵測到提醒事項"
        } else {
            recognized.take(3).joinToString("、").let {
                if (recognized.size > 3) "$it 等（已存在）" else "$it（已存在）"
            }
        }
        val n = NotificationCompat.Builder(context, CHANNEL_AI_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("AI 辨識完成")
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()
        manager.notify(AI_STATUS_NOTIF_ID, n)
    }

    fun cancelAiStatus(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(AI_STATUS_NOTIF_ID)
    }

    fun showAiPendingConfirmation(context: Context, count: Int, titles: List<String>) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val confirmIntent = Intent(context, AiConfirmReceiver::class.java).apply {
            action = AiConfirmReceiver.ACTION_CONFIRM
        }
        val confirmPi = PendingIntent.getBroadcast(
            context, PENDING_CONFIRM_NOTIF_ID, confirmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val cancelIntent = Intent(context, AiConfirmReceiver::class.java).apply {
            action = AiConfirmReceiver.ACTION_CANCEL
        }
        val cancelPi = PendingIntent.getBroadcast(
            context, PENDING_CONFIRM_NOTIF_ID + 1, cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val body = titles.take(3).joinToString("、").let {
            if (titles.size > 3) "$it 等" else it
        }
        val n = NotificationCompat.Builder(context, CHANNEL_AI_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("偵測到 $count 個提醒事項")
            .setContentText("$body，是否全部加入？")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("$body\n\n共 $count 個事件，點「全部加入」確認新增。"))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(false)
            .addAction(0, "全部加入", confirmPi)
            .addAction(0, "取消", cancelPi)
            .build()
        manager.notify(PENDING_CONFIRM_NOTIF_ID, n)
    }

    fun cancelPendingConfirmation(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(PENDING_CONFIRM_NOTIF_ID)
    }

    fun createNotificationChannel(context: Context, bypassDnd: Boolean = false) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "ClassNote 提醒事項推播通知"
            enableVibration(true)
            setBypassDnd(bypassDnd)
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    fun showReminderNotification(
        context: Context,
        notificationId: Int,
        title: String,
        body: String,
        category: String? = null,
        reminderId: Long = -1L,
        fullScreenAlarm: Boolean = true
    ) {
        // 點擊通知 → 跳到提醒列表
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "reminders")
        }
        val tapPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Full-screen intent → 鎖屏/使用中時顯示全螢幕鬧鐘介面
        val alarmIntent = Intent(context, ReminderAlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(ReminderAlarmActivity.EXTRA_TITLE, title)
            putExtra(ReminderAlarmActivity.EXTRA_NOTE, body)
            putExtra(ReminderAlarmActivity.EXTRA_CATEGORY, category)
            putExtra(ReminderAlarmActivity.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            notificationId + 10_000,   // 避免與 tap intent 衝突
            alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body.ifBlank { "點擊查看提醒詳情" })
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(tapPendingIntent)

        if (fullScreenAlarm) {
            builder.setFullScreenIntent(fullScreenPendingIntent, true)
        }

        // ── 快速動作按鈕（延後 / 完成）────────────────────────────────────────
        // 延後
        val snoozeIntent = Intent(context, ReminderActionReceiver::class.java).apply {
            action = ReminderActionReceiver.ACTION_SNOOZE
            putExtra(ReminderActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
            putExtra(ReminderActionReceiver.EXTRA_TITLE, title)
            putExtra(ReminderActionReceiver.EXTRA_NOTE, body)
            putExtra(ReminderActionReceiver.EXTRA_CATEGORY, category)
            putExtra(ReminderActionReceiver.EXTRA_FULL_SCREEN_ALARM, fullScreenAlarm)
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 20_000,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 完成（只有在有 reminderId 時才顯示）
        if (reminderId >= 0) {
            val completeIntent = Intent(context, ReminderActionReceiver::class.java).apply {
                action = ReminderActionReceiver.ACTION_COMPLETE
                putExtra(ReminderActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
                putExtra(ReminderActionReceiver.EXTRA_REMINDER_ID, reminderId)
            }
            val completePendingIntent = PendingIntent.getBroadcast(
                context,
                notificationId + 30_000,
                completeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(0, "完成", completePendingIntent)
        }

        builder.addAction(0, "延後", snoozePendingIntent)

        val notification = builder.build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notificationId, notification)
    }
}

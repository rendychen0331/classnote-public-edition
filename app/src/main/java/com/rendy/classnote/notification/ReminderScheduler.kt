package com.rendy.classnote.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.rendy.classnote.data.AppPreferences
import com.rendy.classnote.data.local.entity.ReminderNotificationEntity
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

object ReminderScheduler {

    private const val ACTION_REMINDER = "com.rendy.classnote.REMINDER_ALARM"

    fun scheduleNotification(context: Context, notification: ReminderNotificationEntity) {
        if (notification.triggerAt <= System.currentTimeMillis()) return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = buildIntent(context, notification)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            (notification.id and 0x7FFFFFFF).toInt(), // H-1 fix: 避免 Long→Int 溢位導致負值/衝突
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                notification.triggerAt,
                pendingIntent
            )
        } catch (e: SecurityException) {
            // SCHEDULE_EXACT_ALARM 未授權，略過排程（資料已存入 DB）
        }
    }

    fun cancelNotification(context: Context, notificationId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_REMINDER
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            (notificationId and 0x7FFFFFFF).toInt(), // H-1 fix: 與 scheduleNotification 一致
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return
        alarmManager.cancel(pendingIntent)
    }

    fun rescheduleAll(context: Context, notifications: List<ReminderNotificationEntity>) {
        val now = System.currentTimeMillis()
        notifications.filter { !it.isFired && it.triggerAt > now }
            .forEach { scheduleNotification(context, it) }
    }

    /**
     * 安靜時段夾取。
     * @param startHour 安靜時段開始（整點，預設 23）
     * @param endHour   安靜時段結束（整點，預設 6）
     * @param policyBefore true = 提前到 startHour:00 前 1 分鐘；false = 延後到 endHour:00（預設）
     */
    fun clampToQuietHours(
        millis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        startHour: Int = 23,
        endHour: Int = 6,
        policyBefore: Boolean = false
    ): Long {
        val zdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(millis), zone)
        val hour = zdt.hour
        val inQuiet = hour >= startHour || hour < endHour
        if (!inQuiet) return millis

        return if (policyBefore && hour >= startHour) {
            // 提前：推到安靜時段開始前 1 分鐘（例如 22:59）
            zdt.with(LocalTime.of(startHour, 0)).minusMinutes(1).toInstant().toEpochMilli()
        } else {
            // 延後（預設），或提前但已過午夜無法回頭 → 推到結束時間
            val candidate = zdt.with(LocalTime.of(endHour, 0))
            if (candidate.toInstant().toEpochMilli() <= millis)
                candidate.plusDays(1).toInstant().toEpochMilli()
            else
                candidate.toInstant().toEpochMilli()
        }
    }

    /** 從 AppPreferences 讀取設定後呼叫 [clampToQuietHours]。 */
    fun clampToQuietHours(millis: Long, prefs: AppPreferences, zone: ZoneId = ZoneId.systemDefault()): Long =
        clampToQuietHours(millis, zone, prefs.quietHoursStart, prefs.quietHoursEnd, prefs.quietHoursPolicyBefore)

    private fun buildIntent(context: Context, notification: ReminderNotificationEntity): Intent =
        Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_REMINDER
            putExtra(ReminderReceiver.EXTRA_NOTIFICATION_ID, notification.id)
            putExtra(ReminderReceiver.EXTRA_REMINDER_ID, notification.reminderId)
            putExtra(ReminderReceiver.EXTRA_TRIGGER_AT, notification.triggerAt)
        }
}

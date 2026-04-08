package com.rendy.classnote.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.rendy.classnote.data.model.ReminderCategory
import com.rendy.classnote.databinding.ActivityReminderAlarmBinding
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * 全螢幕鬧鐘介面。
 * 透過 NotificationHelper.setFullScreenIntent 觸發，
 * 或在鎖屏狀態下由通知直接喚醒螢幕顯示。
 */
class ReminderAlarmActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TITLE = "alarm_title"
        const val EXTRA_NOTE = "alarm_note"
        const val EXTRA_CATEGORY = "alarm_category"
        const val EXTRA_NOTIFICATION_ID = "alarm_notification_id"

        const val SNOOZE_MINUTES = 10L
    }

    private lateinit var binding: ActivityReminderAlarmBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 讓 Activity 顯示在鎖屏上方並喚醒螢幕
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        binding = ActivityReminderAlarmBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val title = intent.getStringExtra(EXTRA_TITLE) ?: return finish()
        val note = intent.getStringExtra(EXTRA_NOTE) ?: ""
        val category = intent.getStringExtra(EXTRA_CATEGORY)
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)

        // 顯示當前時間
        binding.tvAlarmTime.text =
            LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))

        // 分類色條 + chip
        val cat = ReminderCategory.fromString(category)
        val accentColor = Color.parseColor(ReminderCategory.colorFor(category))
        binding.viewAccentBar.setBackgroundColor(accentColor)

        if (cat != null) {
            binding.tvCategory.visibility = View.VISIBLE
            binding.tvCategory.text = cat.label
            val bg = binding.tvCategory.background.mutate() as? GradientDrawable
            bg?.setColor(accentColor)
        }

        // 標題 + 備註
        binding.tvTitle.text = title
        if (note.isNotBlank()) {
            binding.tvNote.visibility = View.VISIBLE
            binding.tvNote.text = note
        }

        // 關閉按鈕
        binding.btnDismiss.setOnClickListener {
            finish()
        }

        // 延後 10 分鐘（Snooze）
        binding.btnSnooze.setOnClickListener {
            scheduleSnooze(notificationId, title, note, category)
            finish()
        }
    }

    private fun scheduleSnooze(
        notificationId: Int,
        title: String,
        note: String,
        category: String?
    ) {
        if (notificationId < 0) return
        // 重新用 AlarmManager 在 10 分鐘後觸發同一個 notification ID
        val triggerAt = System.currentTimeMillis() + SNOOZE_MINUTES * 60 * 1000
        val alarmManager =
            getSystemService(android.app.AlarmManager::class.java) ?: return
        val intent = android.content.Intent(this,
            com.rendy.classnote.notification.ReminderReceiver::class.java).apply {
            action = "com.rendy.classnote.REMINDER_ALARM"
            putExtra(com.rendy.classnote.notification.ReminderReceiver.EXTRA_NOTIFICATION_ID,
                notificationId.toLong())
            // 把 title/note/category 放在 intent 供 receiver 直接用（snooze 情境）
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_NOTE, note)
            putExtra(EXTRA_CATEGORY, category)
            putExtra("is_snooze", true)
        }
        val pendingIntent = android.app.PendingIntent.getBroadcast(
            this,
            notificationId + 100_000,   // 避免與原本 PendingIntent 衝突
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                    android.app.PendingIntent.FLAG_IMMUTABLE
        )
        try {
            alarmManager.setExactAndAllowWhileIdle(
                android.app.AlarmManager.RTC_WAKEUP,
                triggerAt,
                pendingIntent
            )
        } catch (_: SecurityException) {}
    }
}

package com.rendy.classnote.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 提醒事項的通知時間（一個提醒可有多個通知）
 * triggerAt：Unix timestamp（毫秒）
 * isFired：是否已觸發（用於避免重複）
 */
@Entity(
    tableName = "reminder_notifications",
    foreignKeys = [
        ForeignKey(
            entity = ReminderEntity::class,
            parentColumns = ["id"],
            childColumns = ["reminderId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("reminderId")]
)
data class ReminderNotificationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val reminderId: Long,
    val triggerAt: Long,
    val isFired: Boolean = false
)

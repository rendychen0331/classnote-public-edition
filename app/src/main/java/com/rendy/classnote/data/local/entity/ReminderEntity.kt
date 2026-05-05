package com.rendy.classnote.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 提醒事項
 * courseId 為 null 表示獨立提醒；不為 null 則關聯特定課程
 * isCompleted = true 時前端不顯示，背景定期清理
 */
@Entity(
    tableName = "reminders",
    foreignKeys = [
        ForeignKey(
            entity = CourseEntity::class,
            parentColumns = ["id"],
            childColumns = ["courseId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("courseId")]
)
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val note: String = "",
    val courseId: Long? = null,    // null = 獨立提醒
    val dueDate: String? = null,   // "yyyy-MM-dd" 結束日
    val dueTime: String? = null,   // "HH:mm" 截止時間，null 表示不指定
    val isCompleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val category: String? = null,  // WORK / HOMEWORK / EXAM / REMINDER
    val colorHex: String? = null,  // 自訂顏色，null 時依 category 套用
    val startDate: String? = null, // "yyyy-MM-dd" 多天事件開始日，null 視為單日
    val fullScreenAlarm: Boolean = true,  // 是否使用全螢幕提醒（個別設定）
    val externalId: String? = null,  // 外部來源唯一識別碼，如 "gmail:MSG_ID"
    val syncSource: String? = null,  // 同步來源："gmail" | "classroom" | "notify"
    val sourceName: String? = null,  // 來源顯示名稱，如課程名稱或寄件者
    val repeatType: String = "NONE",  // NONE / DAILY / WEEKLY / MONTHLY
    val rawNotification: String? = null  // 原始通知完整文字（通知監聽來源）
)

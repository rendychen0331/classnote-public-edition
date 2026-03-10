package com.rendy.classnote.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 節次時間設定（使用者可調整）
 * startMinute / endMinute：距離 00:00 的分鐘數
 * 例：第 1 節 08:10 = 490, 09:00 = 540
 */
@Entity(tableName = "period_times")
data class PeriodTimeEntity(
    @PrimaryKey
    val period: Int,        // 1～7
    val startMinute: Int,
    val endMinute: Int
)

package com.rendy.classnote.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 固定週課表的課程（週一～週五 × 第 1～7 節）
 * semesterId：學期識別，例如 "2024-1"，切換學期時覆蓋舊資料
 */
@Entity(tableName = "courses")
data class CourseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val semesterId: String,
    val dayOfWeek: Int,       // 1=週一, 2=週二, ..., 5=週五
    val period: Int,          // 1～7
    val name: String,
    val teacher: String = "",
    val room: String = "",
    val colorHex: String = "#4CAF50"
)

package com.rendy.classnote.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "class_session_summaries")
data class ClassSessionSummaryEntity(
    @PrimaryKey val sessionLabel: String,
    val summary: String,
    val generatedAt: Long = System.currentTimeMillis()
)

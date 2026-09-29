package com.rendy.classnote.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 數學公式
 * latex: LaTeX 語法字串，如 "E=mc^2"
 * subject: 科目標籤，如 "物理" "數學"，空字串表示未分類
 */
@Entity(tableName = "formulas")
data class FormulaEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val latex: String,
    val explanation: String = "",
    val subject: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

package com.rendy.classnote.data.local.dao

import androidx.room.*
import com.rendy.classnote.data.local.entity.ClassSessionSummaryEntity

@Dao
interface ClassSessionSummaryDao {
    @Query("SELECT * FROM class_session_summaries WHERE sessionLabel = :sessionLabel")
    suspend fun get(sessionLabel: String): ClassSessionSummaryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ClassSessionSummaryEntity)
}

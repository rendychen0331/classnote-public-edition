package com.rendy.classnote.data.local.dao

import androidx.room.*
import com.rendy.classnote.data.local.entity.ErrorLogEntity

@Dao
interface ErrorLogDao {
    @Query("SELECT * FROM error_logs ORDER BY timestamp DESC LIMIT 200")
    suspend fun getRecentLogs(): List<ErrorLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: ErrorLogEntity)

    @Query("DELETE FROM error_logs WHERE id NOT IN (SELECT id FROM error_logs ORDER BY timestamp DESC LIMIT 200)")
    suspend fun pruneOldLogs()

    @Query("DELETE FROM error_logs")
    suspend fun clearAll()
}

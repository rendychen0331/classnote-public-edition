package com.rendy.classnote.data.local.dao

import androidx.room.*
import com.rendy.classnote.data.local.entity.FormulaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FormulaDao {

    @Query("SELECT * FROM formulas ORDER BY subject ASC, title ASC")
    fun getAllFormulas(): Flow<List<FormulaEntity>>

    @Query("""
        SELECT * FROM formulas
        WHERE title LIKE '%' || :query || '%'
           OR explanation LIKE '%' || :query || '%'
           OR subject LIKE '%' || :query || '%'
           OR latex LIKE '%' || :query || '%'
        ORDER BY subject ASC, title ASC
    """)
    fun search(query: String): Flow<List<FormulaEntity>>

    @Query("SELECT * FROM formulas WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): FormulaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(formula: FormulaEntity): Long

    @Update
    suspend fun update(formula: FormulaEntity)

    @Delete
    suspend fun delete(formula: FormulaEntity)
}

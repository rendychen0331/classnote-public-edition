package com.rendy.classnote.data.repository

import com.rendy.classnote.data.local.dao.FormulaDao
import com.rendy.classnote.data.local.entity.FormulaEntity
import kotlinx.coroutines.flow.Flow

class FormulaRepository(private val dao: FormulaDao) {

    fun getAllFormulas(): Flow<List<FormulaEntity>> = dao.getAllFormulas()

    fun search(query: String): Flow<List<FormulaEntity>> = dao.search(query)

    suspend fun getById(id: Long): FormulaEntity? = dao.getById(id)

    suspend fun insert(formula: FormulaEntity): Long = dao.insert(formula)

    suspend fun update(formula: FormulaEntity) = dao.update(formula)

    suspend fun delete(formula: FormulaEntity) = dao.delete(formula)
}

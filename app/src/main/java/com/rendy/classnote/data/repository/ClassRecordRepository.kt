package com.rendy.classnote.data.repository

import com.rendy.classnote.data.local.dao.ClassRecordDao
import com.rendy.classnote.data.local.dao.ClassRecordMediaDao
import com.rendy.classnote.data.local.dao.ClassSessionSummaryDao
import com.rendy.classnote.data.local.entity.ClassRecordEntity
import com.rendy.classnote.data.local.entity.ClassRecordMediaEntity
import com.rendy.classnote.data.local.entity.ClassSessionSummaryEntity
import kotlinx.coroutines.flow.Flow

class ClassRecordRepository(
    private val recordDao: ClassRecordDao,
    private val mediaDao: ClassRecordMediaDao,
    private val sessionSummaryDao: ClassSessionSummaryDao
) {
    fun getAllRecords(): Flow<List<ClassRecordEntity>> = recordDao.getAllRecords()

    suspend fun getById(id: Long): ClassRecordEntity? = recordDao.getById(id)

    suspend fun insert(record: ClassRecordEntity): Long = recordDao.insert(record)

    suspend fun update(record: ClassRecordEntity) = recordDao.update(record)

    suspend fun deleteById(id: Long) = recordDao.deleteById(id)

    fun getMediaForRecord(recordId: Long): Flow<List<ClassRecordMediaEntity>> =
        mediaDao.getMediaForRecord(recordId)

    suspend fun getMediaForRecordOnce(recordId: Long): List<ClassRecordMediaEntity> =
        mediaDao.getMediaForRecordOnce(recordId)

    suspend fun insertMedia(media: ClassRecordMediaEntity): Long = mediaDao.insert(media)

    suspend fun deleteMedia(media: ClassRecordMediaEntity) = mediaDao.delete(media)

    suspend fun deleteAllMediaForRecord(recordId: Long) = mediaDao.deleteAllForRecord(recordId)

    suspend fun updateMediaAiSummary(id: Long, summary: String) = mediaDao.updateAiSummary(id, summary)

    suspend fun getFirstPhotoPathsForRecords(recordIds: List<Long>): Map<Long, String> =
        mediaDao.getPhotosForRecords(recordIds)
            .groupBy { it.recordId }
            .mapValues { (_, list) -> list.first().filePath }

    suspend fun getSessionSummary(sessionLabel: String): String? =
        sessionSummaryDao.get(sessionLabel)?.summary

    suspend fun saveSessionSummary(sessionLabel: String, summary: String) =
        sessionSummaryDao.upsert(ClassSessionSummaryEntity(sessionLabel, summary))
}

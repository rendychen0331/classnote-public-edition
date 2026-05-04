package com.rendy.classnote.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.rendy.classnote.data.local.ClassNoteDatabase

class LocalCalendarSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "local_calendar_auto_sync"
    }

    override suspend fun doWork(): Result {
        val prefs = AppPreferences(applicationContext)
        if (!prefs.localCalendarSyncEnabled) return Result.success()

        val db = ClassNoteDatabase.getDatabase(applicationContext)
        return when (val result = LocalCalendarSyncManager.sync(
            applicationContext,
            db.reminderDao(),
            db.reminderNotificationDao()
        )) {
            is LocalCalendarSyncManager.SyncResult.Success -> {
                prefs.lastLocalCalendarSyncSummary = "已自動匯入 ${result.imported} 筆，略過 ${result.skipped} 筆"
                Result.success()
            }
            is LocalCalendarSyncManager.SyncResult.Error -> {
                prefs.lastLocalCalendarSyncSummary = "同步失敗：${result.message}"
                Result.retry()
            }
            LocalCalendarSyncManager.SyncResult.NoPermission -> {
                prefs.lastLocalCalendarSyncSummary = "缺少日曆讀取權限"
                Result.failure()
            }
        }
    }
}

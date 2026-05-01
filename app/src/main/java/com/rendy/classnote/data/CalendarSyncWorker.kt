package com.rendy.classnote.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.rendy.classnote.data.local.ClassNoteDatabase

class CalendarSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val email = GoogleAuthManager.getCalendarAccountEmail(applicationContext)
            ?: return Result.failure()

        val prefs = AppPreferences(applicationContext)
        val db = ClassNoteDatabase.getDatabase(applicationContext)
        val result = CalendarSyncManager.sync(
            applicationContext,
            email,
            db.reminderDao(),
            db.reminderNotificationDao()
        )

        return when (result) {
            is CalendarSyncManager.SyncResult.Success -> {
                prefs.lastCalendarSyncSummary = "已自動匯入 ${result.imported} 筆，略過 ${result.skipped} 筆"
                Result.success()
            }
            is CalendarSyncManager.SyncResult.Error -> Result.retry()
            CalendarSyncManager.SyncResult.NoPermission -> Result.failure()
        }
    }
}

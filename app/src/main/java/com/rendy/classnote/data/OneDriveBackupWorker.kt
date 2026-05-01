package com.rendy.classnote.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class OneDriveBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val token = OneDriveAuthManager.acquireTokenSilent(applicationContext)
            ?: return Result.failure()

        val prefs = AppPreferences(applicationContext)
        return when (val result = OneDriveBackupManager.backup(applicationContext, token)) {
            is OneDriveBackupManager.Result.Success -> {
                prefs.lastOneDriveSyncSummary = "自動備份成功"
                Result.success()
            }
            is OneDriveBackupManager.Result.Error -> Result.retry()
        }
    }
}

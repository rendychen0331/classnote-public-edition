package com.rendy.classnote.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.android.gms.auth.api.signin.GoogleSignIn

class DriveBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val account = GoogleSignIn.getLastSignedInAccount(applicationContext)
            ?: return Result.failure()
        val prefs = AppPreferences(applicationContext)
        return when (DriveBackupManager.backup(applicationContext, account, prefs.backupNetworkType)) {
            is DriveBackupManager.Result.Success -> Result.success()
            is DriveBackupManager.Result.AuthRequired -> Result.failure()
            is DriveBackupManager.Result.Error -> Result.retry()
        }
    }
}

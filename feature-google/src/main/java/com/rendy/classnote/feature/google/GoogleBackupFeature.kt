package com.rendy.classnote.feature.google

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.FileContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.rendy.classnote.feature.BackupFeature
import com.rendy.classnote.feature.BackupOutcome
import com.rendy.classnote.feature.SyncBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileOutputStream

class GoogleBackupFeature : BackupFeature {

    private val TAG = "GoogleBackupFeature"
    private val BACKUP_FILENAME = "classnote_backup.db"
    private val DB_NAME = "classnote_database"

    override suspend fun backup(bridge: SyncBridge): BackupOutcome = withContext(Dispatchers.IO) {
        val ctx = bridge.context
        if (!isNetworkAllowed(bridge)) return@withContext BackupOutcome.Error("網路條件不允許備份")
        val email = bridge.googleSignedInAccountEmail()
            ?: return@withContext BackupOutcome.AuthRequired(null)
        try {
            val drive = buildDriveService(bridge, email)
            val dbFile = ctx.getDatabasePath(DB_NAME)
            if (!dbFile.exists()) return@withContext BackupOutcome.Error("資料庫不存在")
            val existing = drive.files().list()
                .setSpaces("appDataFolder")
                .setQ("name='$BACKUP_FILENAME'")
                .execute().files
            val meta = com.google.api.services.drive.model.File().apply {
                name = BACKUP_FILENAME
                parents = listOf("appDataFolder")
            }
            val content = FileContent("application/octet-stream", dbFile)
            if (existing.isNullOrEmpty()) {
                drive.files().create(meta, content).execute()
            } else {
                drive.files().update(existing[0].id, com.google.api.services.drive.model.File(), content).execute()
            }
            bridge.logSync("DriveBackup", "backup", "成功", true)
            BackupOutcome.Success
        } catch (e: UserRecoverableAuthIOException) {
            BackupOutcome.AuthRequired(e.intent)
        } catch (e: GoogleJsonResponseException) {
            if (e.statusCode == 401 || e.statusCode == 403) BackupOutcome.AuthRequired(null)
            else BackupOutcome.Error(e.message ?: "Drive 錯誤")
        } catch (e: Exception) {
            Log.e(TAG, "Backup failed", e)
            bridge.logSync("DriveBackup", "backup", e.message ?: "失敗", false)
            BackupOutcome.Error(e.message ?: "備份失敗")
        }
    }

    override suspend fun restore(bridge: SyncBridge): BackupOutcome = withContext(Dispatchers.IO) {
        val ctx = bridge.context
        if (!isNetworkAllowed(bridge)) return@withContext BackupOutcome.Error("網路條件不允許還原")
        val email = bridge.googleSignedInAccountEmail()
            ?: return@withContext BackupOutcome.AuthRequired(null)
        try {
            val drive = buildDriveService(bridge, email)
            val files = drive.files().list()
                .setSpaces("appDataFolder")
                .setQ("name='$BACKUP_FILENAME'")
                .execute().files
            if (files.isNullOrEmpty()) return@withContext BackupOutcome.Error("Drive 上沒有備份檔")
            val dbFile = ctx.getDatabasePath(DB_NAME)
            drive.files().get(files[0].id).executeMediaAndDownloadTo(FileOutputStream(dbFile))
            bridge.logSync("DriveBackup", "restore", "成功", true)
            BackupOutcome.Success
        } catch (e: UserRecoverableAuthIOException) {
            BackupOutcome.AuthRequired(e.intent)
        } catch (e: Exception) {
            Log.e(TAG, "Restore failed", e)
            BackupOutcome.Error(e.message ?: "還原失敗")
        }
    }

    private fun buildDriveService(bridge: SyncBridge, email: String): Drive {
        val credential = GoogleAccountCredential.usingOAuth2(
            bridge.context, listOf(DriveScopes.DRIVE_APPDATA)
        ).apply { selectedAccountName = email }
        return Drive.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), credential)
            .setApplicationName("ClassNote")
            .build()
    }

    private fun isNetworkAllowed(bridge: SyncBridge): Boolean {
        val ctx = bridge.context
        val networkType = bridge.backupNetworkType()
        val cm = ctx.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return false) ?: return false
        return when (networkType) {
            1 -> caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            else -> true
        }
    }
}

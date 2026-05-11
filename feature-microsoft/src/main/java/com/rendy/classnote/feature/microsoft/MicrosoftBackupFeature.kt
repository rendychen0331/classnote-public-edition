package com.rendy.classnote.feature.microsoft

import android.util.Log
import com.rendy.classnote.feature.BackupFeature
import com.rendy.classnote.feature.BackupOutcome
import com.rendy.classnote.feature.SyncBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val GRAPH_BASE = "https://graph.microsoft.com/v1.0"
private const val BACKUP_FILENAME = "classnote_backup.db"
private const val DB_NAME = "classnote_database"
private const val TAG = "MicrosoftBackupFeature"

class MicrosoftBackupFeature : BackupFeature {

    override suspend fun backup(bridge: SyncBridge): BackupOutcome = withContext(Dispatchers.IO) {
        val token = MsAuthHelper.acquireTokenSilent(bridge.context)
            ?: return@withContext BackupOutcome.AuthRequired(null)
        try {
            val dbFile = bridge.context.getDatabasePath(DB_NAME)
            if (!dbFile.exists()) return@withContext BackupOutcome.Error("資料庫不存在")
            val bytes = dbFile.readBytes()
            val url = URL("$GRAPH_BASE/me/drive/special/approot:/$BACKUP_FILENAME:/content")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "PUT"
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Content-Type", "application/octet-stream")
            conn.doOutput = true
            conn.outputStream.write(bytes)
            val code = conn.responseCode; conn.disconnect()
            if (code in 200..299) {
                bridge.logSync("OneDriveBackup", "backup", "成功", true)
                BackupOutcome.Success
            } else {
                BackupOutcome.Error("HTTP $code")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Backup failed", e)
            bridge.logSync("OneDriveBackup", "backup", e.message ?: "失敗", false)
            BackupOutcome.Error(e.message ?: "備份失敗")
        }
    }

    override suspend fun restore(bridge: SyncBridge): BackupOutcome = withContext(Dispatchers.IO) {
        val token = MsAuthHelper.acquireTokenSilent(bridge.context)
            ?: return@withContext BackupOutcome.AuthRequired(null)
        try {
            val url = URL("$GRAPH_BASE/me/drive/special/approot:/$BACKUP_FILENAME:/content")
            val conn = url.openConnection() as HttpURLConnection
            conn.setRequestProperty("Authorization", "Bearer $token")
            val code = conn.responseCode
            if (code == 404) return@withContext BackupOutcome.Error("OneDrive 上沒有備份檔")
            val bytes = conn.inputStream.readBytes(); conn.disconnect()
            bridge.context.getDatabasePath(DB_NAME).writeBytes(bytes)
            bridge.logSync("OneDriveBackup", "restore", "成功", true)
            BackupOutcome.Success
        } catch (e: Exception) {
            Log.e(TAG, "Restore failed", e)
            BackupOutcome.Error(e.message ?: "還原失敗")
        }
    }
}

package com.rendy.classnote.data

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.FileContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import com.rendy.classnote.data.local.ClassNoteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DriveBackupManager {

    private const val TAG = "DriveBackupManager"
    private const val BACKUP_FILENAME = "classnote_backup.db"
    private const val DB_NAME = "classnote_database"

    sealed class Result {
        object Success : Result()
        data class Error(val message: String) : Result()
        data class AuthRequired(val intent: Intent) : Result()
    }

    /**
     * 依使用者設定檢查目前網路是否允許備份/還原。
     * @param networkType AppPreferences.NETWORK_WIFI / NETWORK_MOBILE / NETWORK_ANY
     */
    fun isNetworkAllowed(context: Context, networkType: String): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        val hasWifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        val hasMobile = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        return when (networkType) {
            AppPreferences.NETWORK_WIFI -> hasWifi
            AppPreferences.NETWORK_MOBILE -> hasMobile
            else -> hasWifi || hasMobile
        }
    }

    private fun buildDriveService(context: Context, account: GoogleSignInAccount): Drive {
        val credential = GoogleAccountCredential.usingOAuth2(
            context, listOf(DriveScopes.DRIVE_APPDATA)
        ).apply { selectedAccount = account.account }
        return Drive.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), credential)
            .setApplicationName("ClassNote")
            .build()
    }

    /**
     * 備份 Room DB 到 Google Drive AppData 資料夾。
     * 備份前先做 WAL checkpoint 確保 .db 檔案完整。
     */
    suspend fun backup(context: Context, account: GoogleSignInAccount,
                       networkType: String = AppPreferences.NETWORK_ANY): Result =
        withContext(Dispatchers.IO) {
            if (!isNetworkAllowed(context, networkType)) return@withContext Result.Error("網路不符合備份設定")
            try {
                // WAL checkpoint（wal_checkpoint 回傳結果列，需用 query 而非 execSQL）
                val db = ClassNoteDatabase.getDatabase(context)
                db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").close()

                val dbFile = context.getDatabasePath(DB_NAME)
                if (!dbFile.exists()) return@withContext Result.Error("找不到資料庫檔案")

                val drive = buildDriveService(context, account)

                // 刪除舊備份（AppData 只保留一份）
                val existing = drive.files().list()
                    .setSpaces("appDataFolder")
                    .setQ("name = '$BACKUP_FILENAME'")
                    .setFields("files(id)")
                    .execute()
                    .files
                existing?.forEach { drive.files().delete(it.id).execute() }

                // 上傳新備份
                val metadata = File().apply {
                    name = BACKUP_FILENAME
                    parents = listOf("appDataFolder")
                }
                val mediaContent = FileContent("application/octet-stream", dbFile)
                drive.files().create(metadata, mediaContent)
                    .setFields("id")
                    .execute()

                Log.d(TAG, "Backup successful")
                Result.Success
            } catch (e: UserRecoverableAuthIOException) {
                Log.w(TAG, "Backup auth required", e)
                Result.AuthRequired(e.intent)
            } catch (e: GoogleJsonResponseException) {
                val reason = e.details?.errors?.firstOrNull()?.reason ?: "unknown"
                Log.e(TAG, "Backup Drive API error: ${e.statusCode} reason=$reason", e)
                Result.Error("備份失敗 (${e.statusCode} $reason)")
            } catch (e: Exception) {
                Log.e(TAG, "Backup failed: ${e.javaClass.simpleName}", e)
                Result.Error(e.message ?: "備份失敗")
            }
        }

    /**
     * 從 Google Drive AppData 還原 DB。
     * 還原後需重啟 App 才能讓 Room 重新載入。
     */
    suspend fun restore(context: Context, account: GoogleSignInAccount,
                        networkType: String = AppPreferences.NETWORK_ANY): Result =
        withContext(Dispatchers.IO) {
            if (!isNetworkAllowed(context, networkType)) return@withContext Result.Error("網路不符合備份設定")
            try {
                val drive = buildDriveService(context, account)

                val files = drive.files().list()
                    .setSpaces("appDataFolder")
                    .setQ("name = '$BACKUP_FILENAME'")
                    .setFields("files(id, modifiedTime)")
                    .execute()
                    .files

                if (files.isNullOrEmpty()) return@withContext Result.Error("找不到備份檔案")

                val fileId = files.first().id
                val dbFile = context.getDatabasePath(DB_NAME)

                // 下載到暫存檔，成功後再覆蓋
                val tempFile = java.io.File(context.cacheDir, "classnote_restore.tmp")
                // C-1 fix: 用 use{} 確保 stream 一定關閉
                FileOutputStream(tempFile).use { out ->
                    drive.files().get(fileId).executeMediaAndDownloadTo(out)
                }

                // C-2 fix: 下載完整後才關閉 DB，並用 finally 確保 tempFile 刪除
                ClassNoteDatabase.closeDatabase()

                // 覆蓋 DB 檔案（同時刪除 WAL / SHM）
                try {
                    tempFile.copyTo(dbFile, overwrite = true)
                    java.io.File(dbFile.path + "-wal").delete()
                    java.io.File(dbFile.path + "-shm").delete()
                } finally {
                    tempFile.delete()
                }

                Log.d(TAG, "Restore successful")
                Result.Success
            } catch (e: UserRecoverableAuthIOException) {
                Log.w(TAG, "Restore auth required", e)
                Result.AuthRequired(e.intent)
            } catch (e: Exception) {
                Log.e(TAG, "Restore failed", e)
                Result.Error(e.message ?: "還原失敗")
            }
        }

    /**
     * 取得最後備份時間（null 表示沒有備份）
     */
    suspend fun getLastBackupTime(context: Context, account: GoogleSignInAccount): String? =
        withContext(Dispatchers.IO) {
            try {
                val drive = buildDriveService(context, account)
                val files = drive.files().list()
                    .setSpaces("appDataFolder")
                    .setQ("name = '$BACKUP_FILENAME'")
                    .setFields("files(modifiedTime)")
                    .execute()
                    .files
                if (files.isNullOrEmpty()) return@withContext null
                val modifiedTime = files.first().modifiedTime?.value ?: return@withContext null
                val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
                sdf.format(Date(modifiedTime))
            } catch (_: Exception) {
                null
            }
        }
}

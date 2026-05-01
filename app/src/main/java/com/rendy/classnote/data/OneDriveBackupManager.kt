package com.rendy.classnote.data

import android.content.Context
import android.util.Log
import com.rendy.classnote.data.local.ClassNoteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object OneDriveBackupManager {

    private const val TAG = "OneDriveBackupManager"
    private const val GRAPH_BASE = "https://graph.microsoft.com/v1.0/me/drive/special/approot"
    private const val BACKUP_FILENAME = "classnote_backup.db"
    private const val DB_NAME = "classnote_database"

    sealed class Result {
        data class Success(val info: String = "") : Result()
        data class Error(val message: String) : Result()
    }

    suspend fun backup(context: Context, token: String): Result = withContext(Dispatchers.IO) {
        try {
            val db = ClassNoteDatabase.getDatabase(context)
            db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").close()

            val dbFile = context.getDatabasePath(DB_NAME)
            if (!dbFile.exists()) return@withContext Result.Error("資料庫不存在")

            val url = URL("$GRAPH_BASE:/$BACKUP_FILENAME:/content")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "PUT"
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Content-Type", "application/octet-stream")
                doOutput = true
            }
            val bytes = dbFile.readBytes()
            conn.setFixedLengthStreamingMode(bytes.size)
            conn.outputStream.use { it.write(bytes) }

            val code = conn.responseCode
            conn.disconnect()
            if (code in 200..299) Result.Success()
            else Result.Error("備份失敗：HTTP $code")
        } catch (e: Exception) {
            Log.e(TAG, "backup error", e)
            Result.Error(e.message ?: "備份失敗")
        }
    }

    suspend fun restore(context: Context, token: String): Result = withContext(Dispatchers.IO) {
        try {
            val url = URL("$GRAPH_BASE:/$BACKUP_FILENAME:/content")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $token")
                instanceFollowRedirects = true
            }

            val code = conn.responseCode
            if (code !in 200..299) {
                conn.disconnect()
                return@withContext Result.Error("還原失敗：HTTP $code")
            }

            val tempFile = File(context.cacheDir, "onedrive_restore_temp.db")
            conn.inputStream.use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            }
            conn.disconnect()

            ClassNoteDatabase.closeDatabase()
            val dbFile = context.getDatabasePath(DB_NAME)
            tempFile.copyTo(dbFile, overwrite = true)
            tempFile.delete()

            Result.Success()
        } catch (e: Exception) {
            Log.e(TAG, "restore error", e)
            Result.Error(e.message ?: "還原失敗")
        }
    }

    suspend fun getLastBackupTime(token: String): String? = withContext(Dispatchers.IO) {
        try {
            val url = URL("$GRAPH_BASE:/$BACKUP_FILENAME")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $token")
            }
            val code = conn.responseCode
            if (code !in 200..299) { conn.disconnect(); return@withContext null }
            val body = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            val json = JSONObject(body)
            val iso = json.optString("lastModifiedDateTime").takeIf { it.isNotEmpty() }
                ?: return@withContext null

            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            val date = sdf.parse(iso) ?: return@withContext iso
            SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(date)
        } catch (e: Exception) { null }
    }
}

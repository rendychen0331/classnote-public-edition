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
    private const val PREFS_BACKUP_FILENAME = "classnote_prefs_backup.json"
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

            putFile(token, BACKUP_FILENAME, "application/octet-stream", dbFile.readBytes())
                .let { if (it !in 200..299) return@withContext Result.Error("備份失敗：HTTP $it") }

            val prefsJson = buildPrefsJson(context)
            putFile(token, PREFS_BACKUP_FILENAME, "application/json", prefsJson.toByteArray())

            Result.Success()
        } catch (e: Exception) {
            Log.e(TAG, "backup error", e)
            Result.Error(e.message ?: "備份失敗")
        }
    }

    suspend fun restore(context: Context, token: String): Result = withContext(Dispatchers.IO) {
        try {
            val dbBytes = getFile(token, BACKUP_FILENAME)
                ?: return@withContext Result.Error("找不到備份檔案")

            ClassNoteDatabase.closeDatabase()
            val tempFile = File(context.cacheDir, "onedrive_restore_temp.db")
            tempFile.writeBytes(dbBytes)
            val dbFile = context.getDatabasePath(DB_NAME)
            tempFile.copyTo(dbFile, overwrite = true)
            tempFile.delete()

            val prefsBytes = getFile(token, PREFS_BACKUP_FILENAME)
            if (prefsBytes != null) {
                restorePrefsJson(context, String(prefsBytes))
            }

            Result.Success()
        } catch (e: Exception) {
            Log.e(TAG, "restore error", e)
            Result.Error(e.message ?: "還原失敗")
        }
    }

    private fun putFile(token: String, filename: String, contentType: String, bytes: ByteArray): Int {
        val url = URL("$GRAPH_BASE:/$filename:/content")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "PUT"
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", contentType)
            doOutput = true
        }
        conn.setFixedLengthStreamingMode(bytes.size)
        conn.outputStream.use { it.write(bytes) }
        val code = conn.responseCode
        conn.disconnect()
        return code
    }

    private fun getFile(token: String, filename: String): ByteArray? {
        val url = URL("$GRAPH_BASE:/$filename:/content")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bearer $token")
            instanceFollowRedirects = true
        }
        val code = conn.responseCode
        if (code !in 200..299) { conn.disconnect(); return null }
        val bytes = conn.inputStream.use { it.readBytes() }
        conn.disconnect()
        return bytes
    }

    private fun buildPrefsJson(context: Context): String {
        val appPrefs = AppPreferences(context)
        val weatherPrefs = WeatherPreferences(context)
        return JSONObject().apply {
            put("gemini_api_key", appPrefs.geminiApiKey)
            put("cwa_api_key", appPrefs.cwaApiKey)
            put("mimo_api_key", appPrefs.mimoApiKey)
            put("claude_api_key", appPrefs.claudeApiKey)
            put("openai_api_key", appPrefs.openaiApiKey)
            put("groq_api_key", appPrefs.groqApiKey)
            put("preferred_chat_provider", appPrefs.preferredChatProvider)
            put("preferred_notif_provider", appPrefs.preferredNotifProvider)
            put("weather_notif_location", weatherPrefs.weatherNotifLocation)
            put("weather_saved_locations", org.json.JSONArray(weatherPrefs.savedLocations))
        }.toString()
    }

    private fun restorePrefsJson(context: Context, json: String) {
        val obj = JSONObject(json)
        val appPrefs = AppPreferences(context)
        val weatherPrefs = WeatherPreferences(context)
        obj.optString("gemini_api_key").takeIf { it.isNotEmpty() }?.let { appPrefs.geminiApiKey = it }
        obj.optString("cwa_api_key").takeIf { it.isNotEmpty() }?.let { appPrefs.cwaApiKey = it }
        obj.optString("mimo_api_key").takeIf { it.isNotEmpty() }?.let { appPrefs.mimoApiKey = it }
        obj.optString("claude_api_key").takeIf { it.isNotEmpty() }?.let { appPrefs.claudeApiKey = it }
        obj.optString("openai_api_key").takeIf { it.isNotEmpty() }?.let { appPrefs.openaiApiKey = it }
        obj.optString("groq_api_key").takeIf { it.isNotEmpty() }?.let { appPrefs.groqApiKey = it }
        obj.optString("preferred_chat_provider").takeIf { it.isNotEmpty() }?.let { appPrefs.preferredChatProvider = it }
        obj.optString("preferred_notif_provider").takeIf { it.isNotEmpty() }?.let { appPrefs.preferredNotifProvider = it }
        obj.optString("weather_notif_location").takeIf { it.isNotEmpty() }?.let { weatherPrefs.weatherNotifLocation = it }
        obj.optJSONArray("weather_saved_locations")?.let { arr ->
            weatherPrefs.savedLocations = (0 until arr.length()).map { arr.getString(it) }
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

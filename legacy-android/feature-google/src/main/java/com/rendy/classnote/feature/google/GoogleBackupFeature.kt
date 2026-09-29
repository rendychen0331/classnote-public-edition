package com.rendy.classnote.feature.google

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.FileContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.rendy.classnote.feature.BackupFeature
import com.rendy.classnote.feature.BackupMeta
import com.rendy.classnote.feature.BackupOutcome
import com.rendy.classnote.feature.RestoreOptions
import com.rendy.classnote.feature.SyncBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import android.database.sqlite.SQLiteDatabase
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GoogleBackupFeature : BackupFeature {

    private val TAG = "GoogleBackupFeature"
    private val DB_NAME = "classnote_database"
    private val MAX_BACKUPS = 3

    private val tsFormat = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US)
    private fun dbFileName(id: String) = "classnote_backup_$id.db"
    private fun metaFileName(id: String) = "classnote_meta_$id.json"

    override suspend fun backup(bridge: SyncBridge): BackupOutcome = withContext(Dispatchers.IO) {
        val ctx = bridge.context
        if (!isNetworkAllowed(bridge)) return@withContext BackupOutcome.Error("網路條件不允許備份")
        val email = bridge.googleSignedInAccountEmail()
            ?: return@withContext BackupOutcome.AuthRequired(null)
        try {
            val drive = buildDriveService(bridge, email)
            val backupId = tsFormat.format(Date())

            val dbFile = ctx.getDatabasePath(DB_NAME)
            if (!dbFile.exists()) return@withContext BackupOutcome.Error("資料庫不存在")
            try {
                SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
                    db.execSQL("PRAGMA wal_checkpoint(FULL)")
                }
            } catch (e: Exception) {
                Log.w(TAG, "wal_checkpoint failed, proceeding anyway", e)
            }

            // upload DB
            val dbMeta = com.google.api.services.drive.model.File().apply {
                name = dbFileName(backupId)
                parents = listOf("appDataFolder")
            }
            drive.files().create(dbMeta, FileContent("application/octet-stream", dbFile)).execute()

            // upload meta JSON
            val metaJson = buildMetaJson(bridge, backupId)
            val metaMeta = com.google.api.services.drive.model.File().apply {
                name = metaFileName(backupId)
                parents = listOf("appDataFolder")
            }
            drive.files().create(metaMeta, ByteArrayContent("application/json", metaJson.toByteArray(Charsets.UTF_8))).execute()

            // prune old backups — keep newest MAX_BACKUPS
            pruneOldFiles(drive, "classnote_backup_", ".db")
            pruneOldFiles(drive, "classnote_meta_", ".json")

            bridge.logSync("DriveBackup", "backup", "成功 ($backupId)", true)
            BackupOutcome.Success()
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

    private fun pruneOldFiles(drive: Drive, prefix: String, suffix: String) {
        val files = drive.files().list()
            .setSpaces("appDataFolder")
            .setQ("name contains '$prefix'")
            .setFields("files(id, name)")
            .execute().files ?: return
        val sorted = files.filter { it.name.startsWith(prefix) && it.name.endsWith(suffix) }
            .sortedBy { it.name } // yyyyMMdd_HHmm sorts lexicographically = chronologically
        if (sorted.size > MAX_BACKUPS) {
            sorted.take(sorted.size - MAX_BACKUPS).forEach { f ->
                try { drive.files().delete(f.id).execute() } catch (e: Exception) {
                    Log.w(TAG, "Failed to delete old backup: ${f.name}", e)
                }
            }
        }
    }

    override suspend fun fetchMeta(bridge: SyncBridge): BackupMeta? =
        fetchAllMeta(bridge).firstOrNull()

    override suspend fun fetchAllMeta(bridge: SyncBridge): List<BackupMeta> = withContext(Dispatchers.IO) {
        val email = bridge.googleSignedInAccountEmail() ?: return@withContext emptyList()
        try {
            val drive = buildDriveService(bridge, email)
            val files = drive.files().list()
                .setSpaces("appDataFolder")
                .setQ("name contains 'classnote_meta_'")
                .setFields("files(id, name)")
                .execute().files ?: return@withContext emptyList()
            files.filter { it.name.startsWith("classnote_meta_") && it.name.endsWith(".json") }
                .sortedByDescending { it.name } // newest first
                .mapNotNull { f ->
                    try {
                        val json = drive.files().get(f.id).executeMediaAsInputStream()
                            .bufferedReader().readText()
                        parseBackupMeta(json)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to fetch meta ${f.name}", e)
                        null
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "fetchAllMeta failed", e)
            emptyList()
        }
    }

    override suspend fun restore(bridge: SyncBridge, options: RestoreOptions): BackupOutcome = withContext(Dispatchers.IO) {
        val ctx = bridge.context
        if (!isNetworkAllowed(bridge)) return@withContext BackupOutcome.Error("網路條件不允許還原")
        val email = bridge.googleSignedInAccountEmail()
            ?: return@withContext BackupOutcome.AuthRequired(null)
        try {
            val drive = buildDriveService(bridge, email)

            // resolve which backup version to use
            val backupId: String = options.backupId ?: run {
                drive.files().list()
                    .setSpaces("appDataFolder")
                    .setQ("name contains 'classnote_meta_'")
                    .setFields("files(id, name)")
                    .execute().files
                    ?.filter { it.name.startsWith("classnote_meta_") && it.name.endsWith(".json") }
                    ?.sortedByDescending { it.name }
                    ?.firstOrNull()?.name
                    ?.removePrefix("classnote_meta_")?.removeSuffix(".json")
                    ?: return@withContext BackupOutcome.Error("Drive 上沒有備份")
            }

            // fetch meta for this version
            var meta = BackupMeta()
            val metaFiles = drive.files().list()
                .setSpaces("appDataFolder")
                .setQ("name='${metaFileName(backupId)}'")
                .execute().files
            if (!metaFiles.isNullOrEmpty()) {
                try {
                    val json = drive.files().get(metaFiles[0].id).executeMediaAsInputStream()
                        .bufferedReader().readText()
                    meta = parseBackupMeta(json) ?: BackupMeta()
                } catch (_: Exception) {}
            }

            // restore DB if requested
            if (options.restoreNotes) {
                val dbFiles = drive.files().list()
                    .setSpaces("appDataFolder")
                    .setQ("name='${dbFileName(backupId)}'")
                    .execute().files
                if (dbFiles.isNullOrEmpty()) return@withContext BackupOutcome.Error("找不到備份檔 ($backupId)")
                val dbFile = ctx.getDatabasePath(DB_NAME)
                drive.files().get(dbFiles[0].id).executeMediaAndDownloadTo(FileOutputStream(dbFile))
                ctx.getDatabasePath("$DB_NAME-wal").delete()
                ctx.getDatabasePath("$DB_NAME-shm").delete()
            }

            // restore AI settings if requested
            if (options.restoreAiSettings && meta.hasAiSettings && !metaFiles.isNullOrEmpty()) {
                try {
                    val json = drive.files().get(metaFiles[0].id).executeMediaAsInputStream()
                        .bufferedReader().readText()
                    val obj = JSONObject(json)
                    val aiObj = obj.optJSONObject("aiSettings")
                    if (aiObj != null) {
                        val map = mutableMapOf<String, String>()
                        aiObj.keys().forEach { k -> map[k] = aiObj.getString(k) }
                        bridge.applyAiSettings(map)
                    }
                } catch (_: Exception) {}
            }

            // restore weather settings if requested
            if (options.restoreWeatherSettings && meta.hasWeatherSettings && !metaFiles.isNullOrEmpty()) {
                try {
                    val json = drive.files().get(metaFiles[0].id).executeMediaAsInputStream()
                        .bufferedReader().readText()
                    val obj = JSONObject(json)
                    val weatherObj = obj.optJSONObject("weatherSettings")
                    if (weatherObj != null) {
                        val map = mutableMapOf<String, String>()
                        weatherObj.keys().forEach { k -> map[k] = weatherObj.getString(k) }
                        bridge.applyWeatherSettings(map)
                    }
                } catch (_: Exception) {}
            }

            bridge.logSync("DriveBackup", "restore", "成功 ($backupId)", true)
            BackupOutcome.Success(meta)
        } catch (e: UserRecoverableAuthIOException) {
            BackupOutcome.AuthRequired(e.intent)
        } catch (e: Exception) {
            Log.e(TAG, "Restore failed", e)
            BackupOutcome.Error(e.message ?: "還原失敗")
        }
    }

    private fun buildMetaJson(bridge: SyncBridge, backupId: String): String {
        val obj = JSONObject()
        obj.put("backupId", backupId)
        obj.put("timestamp", backupId) // same value, kept for readability

        val featArr = JSONArray()
        bridge.installedFeatureIds().forEach { featArr.put(it) }
        obj.put("installedFeatures", featArr)

        val aiSettings = bridge.getAiSettings()
        val aiObj = JSONObject()
        aiSettings.forEach { (k, v) -> if (v.isNotBlank()) aiObj.put(k, v) }
        obj.put("aiSettings", aiObj)

        val weatherSettings = bridge.getWeatherSettings()
        val weatherObj = JSONObject()
        weatherSettings.forEach { (k, v) -> if (v.isNotBlank() && v != "false") weatherObj.put(k, v) }
        obj.put("weatherSettings", weatherObj)

        return obj.toString()
    }

    private fun parseBackupMeta(json: String): BackupMeta? = try {
        val obj = JSONObject(json)
        val featArr = obj.optJSONArray("installedFeatures")
        val features = if (featArr != null) {
            (0 until featArr.length()).map { featArr.getString(it) }
        } else emptyList()
        val aiObj = obj.optJSONObject("aiSettings")
        val weatherObj = obj.optJSONObject("weatherSettings")
        val backupId = obj.optString("backupId", "")
        BackupMeta(
            installedFeatures = features,
            hasNotes = true,
            hasAiSettings = aiObj != null && aiObj.length() > 0,
            hasWeatherSettings = weatherObj != null && weatherObj.length() > 0,
            backupId = backupId,
            timestamp = backupId
        )
    } catch (e: Exception) {
        Log.e(TAG, "parseBackupMeta failed", e)
        null
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

package com.rendy.classnote.feature.google

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
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
import java.io.FileOutputStream

class GoogleBackupFeature : BackupFeature {

    private val TAG = "GoogleBackupFeature"
    private val BACKUP_FILENAME = "classnote_backup.db"
    private val META_FILENAME = "classnote_meta.json"
    private val DB_NAME = "classnote_database"

    override suspend fun backup(bridge: SyncBridge): BackupOutcome = withContext(Dispatchers.IO) {
        val ctx = bridge.context
        if (!isNetworkAllowed(bridge)) return@withContext BackupOutcome.Error("網路條件不允許備份")
        val email = bridge.googleSignedInAccountEmail()
            ?: return@withContext BackupOutcome.AuthRequired(null)
        try {
            val drive = buildDriveService(bridge, email)

            // upload DB
            val dbFile = ctx.getDatabasePath(DB_NAME)
            if (!dbFile.exists()) return@withContext BackupOutcome.Error("資料庫不存在")
            val existing = drive.files().list()
                .setSpaces("appDataFolder")
                .setQ("name='$BACKUP_FILENAME'")
                .execute().files
            val dbMeta = com.google.api.services.drive.model.File().apply {
                name = BACKUP_FILENAME
                parents = listOf("appDataFolder")
            }
            val content = FileContent("application/octet-stream", dbFile)
            if (existing.isNullOrEmpty()) {
                drive.files().create(dbMeta, content).execute()
            } else {
                drive.files().update(existing[0].id, com.google.api.services.drive.model.File(), content).execute()
            }

            // upload meta JSON
            val metaJson = buildMetaJson(bridge)
            val metaBytes = metaJson.toByteArray(Charsets.UTF_8)
            val existingMeta = drive.files().list()
                .setSpaces("appDataFolder")
                .setQ("name='$META_FILENAME'")
                .execute().files
            val metaFileMeta = com.google.api.services.drive.model.File().apply {
                name = META_FILENAME
                parents = listOf("appDataFolder")
            }
            val metaContent = ByteArrayContent("application/json", metaBytes)
            if (existingMeta.isNullOrEmpty()) {
                drive.files().create(metaFileMeta, metaContent).execute()
            } else {
                drive.files().update(existingMeta[0].id, com.google.api.services.drive.model.File(), metaContent).execute()
            }

            bridge.logSync("DriveBackup", "backup", "成功", true)
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

    override suspend fun fetchMeta(bridge: SyncBridge): BackupMeta? = withContext(Dispatchers.IO) {
        val email = bridge.googleSignedInAccountEmail() ?: return@withContext null
        try {
            val drive = buildDriveService(bridge, email)
            val files = drive.files().list()
                .setSpaces("appDataFolder")
                .setQ("name='$META_FILENAME'")
                .execute().files
            if (files.isNullOrEmpty()) return@withContext null
            val json = drive.files().get(files[0].id).executeMediaAsInputStream()
                .bufferedReader().readText()
            parseBackupMeta(json)
        } catch (e: Exception) {
            Log.e(TAG, "fetchMeta failed", e)
            null
        }
    }

    override suspend fun restore(bridge: SyncBridge, options: RestoreOptions): BackupOutcome = withContext(Dispatchers.IO) {
        val ctx = bridge.context
        if (!isNetworkAllowed(bridge)) return@withContext BackupOutcome.Error("網路條件不允許還原")
        val email = bridge.googleSignedInAccountEmail()
            ?: return@withContext BackupOutcome.AuthRequired(null)
        try {
            val drive = buildDriveService(bridge, email)

            // fetch meta first (for installed features + settings)
            var meta = BackupMeta()
            val metaFiles = drive.files().list()
                .setSpaces("appDataFolder")
                .setQ("name='$META_FILENAME'")
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
                val files = drive.files().list()
                    .setSpaces("appDataFolder")
                    .setQ("name='$BACKUP_FILENAME'")
                    .execute().files
                if (files.isNullOrEmpty()) return@withContext BackupOutcome.Error("Drive 上沒有備份檔")
                val dbFile = ctx.getDatabasePath(DB_NAME)
                drive.files().get(files[0].id).executeMediaAndDownloadTo(FileOutputStream(dbFile))
            }

            // restore AI settings if requested
            if (options.restoreAiSettings && meta.hasAiSettings) {
                val settingsFiles = drive.files().list()
                    .setSpaces("appDataFolder")
                    .setQ("name='$META_FILENAME'")
                    .execute().files
                if (!settingsFiles.isNullOrEmpty()) {
                    try {
                        val json = drive.files().get(settingsFiles[0].id).executeMediaAsInputStream()
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
            }

            // restore weather settings if requested
            if (options.restoreWeatherSettings && meta.hasWeatherSettings) {
                val settingsFiles = drive.files().list()
                    .setSpaces("appDataFolder")
                    .setQ("name='$META_FILENAME'")
                    .execute().files
                if (!settingsFiles.isNullOrEmpty()) {
                    try {
                        val json = drive.files().get(settingsFiles[0].id).executeMediaAsInputStream()
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
            }

            bridge.logSync("DriveBackup", "restore", "成功", true)
            BackupOutcome.Success(meta)
        } catch (e: UserRecoverableAuthIOException) {
            BackupOutcome.AuthRequired(e.intent)
        } catch (e: Exception) {
            Log.e(TAG, "Restore failed", e)
            BackupOutcome.Error(e.message ?: "還原失敗")
        }
    }

    private fun buildMetaJson(bridge: SyncBridge): String {
        val obj = JSONObject()
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
        BackupMeta(
            installedFeatures = features,
            hasNotes = true,
            hasAiSettings = aiObj != null && aiObj.length() > 0,
            hasWeatherSettings = weatherObj != null && weatherObj.length() > 0
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

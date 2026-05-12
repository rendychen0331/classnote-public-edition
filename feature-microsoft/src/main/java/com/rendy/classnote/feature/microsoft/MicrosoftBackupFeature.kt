package com.rendy.classnote.feature.microsoft

import android.util.Log
import com.rendy.classnote.feature.BackupFeature
import com.rendy.classnote.feature.BackupMeta
import com.rendy.classnote.feature.BackupOutcome
import com.rendy.classnote.feature.RestoreOptions
import com.rendy.classnote.feature.SyncBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val GRAPH_BASE = "https://graph.microsoft.com/v1.0"
private const val BACKUP_FILENAME = "classnote_backup.db"
private const val META_FILENAME = "classnote_meta.json"
private const val DB_NAME = "classnote_database"
private const val TAG = "MicrosoftBackupFeature"

class MicrosoftBackupFeature : BackupFeature {

    override suspend fun backup(bridge: SyncBridge): BackupOutcome = withContext(Dispatchers.IO) {
        val token = MsAuthHelper.acquireTokenSilent(bridge.context)
            ?: return@withContext BackupOutcome.AuthRequired(null)
        try {
            // upload DB
            val dbFile = bridge.context.getDatabasePath(DB_NAME)
            if (!dbFile.exists()) return@withContext BackupOutcome.Error("資料庫不存在")
            uploadToOneDrive(token, BACKUP_FILENAME, dbFile.readBytes(), "application/octet-stream")
                .let { if (it !in 200..299) return@withContext BackupOutcome.Error("HTTP $it") }

            // upload meta JSON
            val metaJson = buildMetaJson(bridge)
            uploadToOneDrive(token, META_FILENAME, metaJson.toByteArray(Charsets.UTF_8), "application/json")

            bridge.logSync("OneDriveBackup", "backup", "成功", true)
            BackupOutcome.Success()
        } catch (e: Exception) {
            Log.e(TAG, "Backup failed", e)
            bridge.logSync("OneDriveBackup", "backup", e.message ?: "失敗", false)
            BackupOutcome.Error(e.message ?: "備份失敗")
        }
    }

    override suspend fun fetchMeta(bridge: SyncBridge): BackupMeta? = withContext(Dispatchers.IO) {
        val token = MsAuthHelper.acquireTokenSilent(bridge.context) ?: return@withContext null
        try {
            val json = downloadFromOneDrive(token, META_FILENAME) ?: return@withContext null
            parseBackupMeta(String(json, Charsets.UTF_8))
        } catch (e: Exception) {
            Log.e(TAG, "fetchMeta failed", e)
            null
        }
    }

    override suspend fun restore(bridge: SyncBridge, options: RestoreOptions): BackupOutcome = withContext(Dispatchers.IO) {
        val token = MsAuthHelper.acquireTokenSilent(bridge.context)
            ?: return@withContext BackupOutcome.AuthRequired(null)
        try {
            // fetch meta
            var meta = BackupMeta()
            try {
                val metaBytes = downloadFromOneDrive(token, META_FILENAME)
                if (metaBytes != null) {
                    meta = parseBackupMeta(String(metaBytes, Charsets.UTF_8)) ?: BackupMeta()
                }
            } catch (_: Exception) {}

            // restore DB
            if (options.restoreNotes) {
                val bytes = downloadFromOneDrive(token, BACKUP_FILENAME)
                    ?: return@withContext BackupOutcome.Error("OneDrive 上沒有備份檔")
                bridge.context.getDatabasePath(DB_NAME).writeBytes(bytes)
            }

            // restore AI settings
            if (options.restoreAiSettings && meta.hasAiSettings) {
                try {
                    val metaBytes = downloadFromOneDrive(token, META_FILENAME)
                    if (metaBytes != null) {
                        val obj = JSONObject(String(metaBytes, Charsets.UTF_8))
                        val aiObj = obj.optJSONObject("aiSettings")
                        if (aiObj != null) {
                            val map = mutableMapOf<String, String>()
                            aiObj.keys().forEach { k -> map[k] = aiObj.getString(k) }
                            bridge.applyAiSettings(map)
                        }
                    }
                } catch (_: Exception) {}
            }

            // restore weather settings
            if (options.restoreWeatherSettings && meta.hasWeatherSettings) {
                try {
                    val metaBytes = downloadFromOneDrive(token, META_FILENAME)
                    if (metaBytes != null) {
                        val obj = JSONObject(String(metaBytes, Charsets.UTF_8))
                        val weatherObj = obj.optJSONObject("weatherSettings")
                        if (weatherObj != null) {
                            val map = mutableMapOf<String, String>()
                            weatherObj.keys().forEach { k -> map[k] = weatherObj.getString(k) }
                            bridge.applyWeatherSettings(map)
                        }
                    }
                } catch (_: Exception) {}
            }

            bridge.logSync("OneDriveBackup", "restore", "成功", true)
            BackupOutcome.Success(meta)
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

    private fun uploadToOneDrive(token: String, filename: String, bytes: ByteArray, contentType: String): Int {
        val url = URL("$GRAPH_BASE/me/drive/special/approot:/$filename:/content")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "PUT"
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.setRequestProperty("Content-Type", contentType)
        conn.doOutput = true
        conn.outputStream.write(bytes)
        val code = conn.responseCode
        conn.disconnect()
        return code
    }

    private fun downloadFromOneDrive(token: String, filename: String): ByteArray? {
        val url = URL("$GRAPH_BASE/me/drive/special/approot:/$filename:/content")
        val conn = url.openConnection() as HttpURLConnection
        conn.setRequestProperty("Authorization", "Bearer $token")
        val code = conn.responseCode
        if (code == 404) return null
        val bytes = conn.inputStream.readBytes()
        conn.disconnect()
        return bytes
    }
}

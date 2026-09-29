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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val GRAPH_BASE = "https://graph.microsoft.com/v1.0"
private const val DB_NAME = "classnote_database"
private const val TAG = "MicrosoftBackupFeature"
private const val MAX_BACKUPS = 3

class MicrosoftBackupFeature : BackupFeature {

    private val tsFormat = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US)
    private fun dbFileName(id: String) = "classnote_backup_$id.db"
    private fun metaFileName(id: String) = "classnote_meta_$id.json"

    override suspend fun backup(bridge: SyncBridge): BackupOutcome = withContext(Dispatchers.IO) {
        val token = MsAuthHelper.acquireTokenSilent(bridge.context)
            ?: return@withContext BackupOutcome.AuthRequired(null)
        try {
            val dbFile = bridge.context.getDatabasePath(DB_NAME)
            if (!dbFile.exists()) return@withContext BackupOutcome.Error("資料庫不存在")
            val backupId = tsFormat.format(Date())

            uploadToOneDrive(token, dbFileName(backupId), dbFile.readBytes(), "application/octet-stream")
                .let { if (it !in 200..299) return@withContext BackupOutcome.Error("HTTP $it") }

            val metaJson = buildMetaJson(bridge, backupId)
            uploadToOneDrive(token, metaFileName(backupId), metaJson.toByteArray(Charsets.UTF_8), "application/json")

            pruneOldFiles(token, "classnote_backup_", ".db")
            pruneOldFiles(token, "classnote_meta_", ".json")

            bridge.logSync("OneDriveBackup", "backup", "成功 ($backupId)", true)
            BackupOutcome.Success()
        } catch (e: Exception) {
            Log.e(TAG, "Backup failed", e)
            bridge.logSync("OneDriveBackup", "backup", e.message ?: "失敗", false)
            BackupOutcome.Error(e.message ?: "備份失敗")
        }
    }

    override suspend fun fetchMeta(bridge: SyncBridge): BackupMeta? =
        fetchAllMeta(bridge).firstOrNull()

    override suspend fun fetchAllMeta(bridge: SyncBridge): List<BackupMeta> = withContext(Dispatchers.IO) {
        val token = MsAuthHelper.acquireTokenSilent(bridge.context) ?: return@withContext emptyList()
        try {
            listOneDriveFiles(token)
                .filter { it.startsWith("classnote_meta_") && it.endsWith(".json") }
                .sortedDescending()
                .mapNotNull { filename ->
                    try {
                        val bytes = downloadFromOneDrive(token, filename) ?: return@mapNotNull null
                        parseBackupMeta(String(bytes, Charsets.UTF_8))
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to fetch meta $filename", e)
                        null
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "fetchAllMeta failed", e)
            emptyList()
        }
    }

    override suspend fun restore(bridge: SyncBridge, options: RestoreOptions): BackupOutcome = withContext(Dispatchers.IO) {
        val token = MsAuthHelper.acquireTokenSilent(bridge.context)
            ?: return@withContext BackupOutcome.AuthRequired(null)
        try {
            val backupId: String = options.backupId ?: run {
                listOneDriveFiles(token)
                    .filter { it.startsWith("classnote_meta_") && it.endsWith(".json") }
                    .maxOrNull()
                    ?.removePrefix("classnote_meta_")?.removeSuffix(".json")
                    ?: return@withContext BackupOutcome.Error("OneDrive 上沒有備份")
            }

            var meta = BackupMeta()
            try {
                val metaBytes = downloadFromOneDrive(token, metaFileName(backupId))
                if (metaBytes != null) meta = parseBackupMeta(String(metaBytes, Charsets.UTF_8)) ?: BackupMeta()
            } catch (_: Exception) {}

            if (options.restoreNotes) {
                val bytes = downloadFromOneDrive(token, dbFileName(backupId))
                    ?: return@withContext BackupOutcome.Error("OneDrive 上沒有備份檔 ($backupId)")
                bridge.context.getDatabasePath(DB_NAME).writeBytes(bytes)
                bridge.context.getDatabasePath("$DB_NAME-wal").delete()
                bridge.context.getDatabasePath("$DB_NAME-shm").delete()
            }

            if (options.restoreAiSettings && meta.hasAiSettings) {
                try {
                    val metaBytes = downloadFromOneDrive(token, metaFileName(backupId))
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

            if (options.restoreWeatherSettings && meta.hasWeatherSettings) {
                try {
                    val metaBytes = downloadFromOneDrive(token, metaFileName(backupId))
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

            bridge.logSync("OneDriveBackup", "restore", "成功 ($backupId)", true)
            BackupOutcome.Success(meta)
        } catch (e: Exception) {
            Log.e(TAG, "Restore failed", e)
            BackupOutcome.Error(e.message ?: "還原失敗")
        }
    }

    private fun listOneDriveFiles(token: String): List<String> {
        val url = URL("$GRAPH_BASE/me/drive/special/approot/children?\$select=name")
        val conn = url.openConnection() as HttpURLConnection
        conn.setRequestProperty("Authorization", "Bearer $token")
        val code = conn.responseCode
        if (code !in 200..299) return emptyList()
        val json = JSONObject(conn.inputStream.bufferedReader().readText())
        conn.disconnect()
        val arr = json.optJSONArray("value") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { arr.getJSONObject(it).optString("name").takeIf { n -> n.isNotEmpty() } }
    }

    private fun pruneOldFiles(token: String, prefix: String, suffix: String) {
        val files = listOneDriveFiles(token)
            .filter { it.startsWith(prefix) && it.endsWith(suffix) }
            .sorted()
        if (files.size > MAX_BACKUPS) {
            files.take(files.size - MAX_BACKUPS).forEach { filename ->
                try { deleteFromOneDrive(token, filename) } catch (e: Exception) {
                    Log.w(TAG, "Failed to delete old backup: $filename", e)
                }
            }
        }
    }

    private fun buildMetaJson(bridge: SyncBridge, backupId: String): String {
        val obj = JSONObject()
        obj.put("backupId", backupId)
        obj.put("timestamp", backupId)
        val featArr = JSONArray()
        bridge.installedFeatureIds().forEach { featArr.put(it) }
        obj.put("installedFeatures", featArr)
        val aiObj = JSONObject()
        bridge.getAiSettings().forEach { (k, v) -> if (v.isNotBlank()) aiObj.put(k, v) }
        obj.put("aiSettings", aiObj)
        val weatherObj = JSONObject()
        bridge.getWeatherSettings().forEach { (k, v) -> if (v.isNotBlank() && v != "false") weatherObj.put(k, v) }
        obj.put("weatherSettings", weatherObj)
        return obj.toString()
    }

    private fun parseBackupMeta(json: String): BackupMeta? = try {
        val obj = JSONObject(json)
        val featArr = obj.optJSONArray("installedFeatures")
        val features = if (featArr != null) (0 until featArr.length()).map { featArr.getString(it) } else emptyList()
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

    private fun deleteFromOneDrive(token: String, filename: String) {
        val url = URL("$GRAPH_BASE/me/drive/special/approot:/$filename")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "DELETE"
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.responseCode
        conn.disconnect()
    }
}

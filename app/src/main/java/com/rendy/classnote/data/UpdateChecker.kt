package com.rendy.classnote.data

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import com.rendy.classnote.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object UpdateChecker {

    private const val TAG = "UpdateChecker"
    private const val RELEASES_API = "https://api.github.com/repos/rendychen0331/classnote-public-edition/releases/latest"
    private const val PREFS_NAME = "update_checker"
    private const val KEY_LAST_CHECK = "last_check_ms"
    private const val CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L

    data class ReleaseInfo(val tagName: String, val apkUrl: String, val isNewer: Boolean)

    suspend fun checkForUpdate(context: Context, force: Boolean = false): ReleaseInfo? = withContext(Dispatchers.IO) {
        try {
            if (!force) {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val lastCheck = prefs.getLong(KEY_LAST_CHECK, 0L)
                if (System.currentTimeMillis() - lastCheck < CHECK_INTERVAL_MS) return@withContext null
                prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
            }

            val conn = (URL(RELEASES_API).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                connectTimeout = 10000
                readTimeout = 10000
            }
            val code = conn.responseCode
            if (code !in 200..299) { conn.disconnect(); return@withContext null }
            val body = conn.inputStream.bufferedReader().readText()
            conn.disconnect()

            val json = JSONObject(body)
            val tagName = json.optString("tag_name").takeIf { it.isNotEmpty() } ?: return@withContext null

            val apkUrl = json.optJSONArray("assets")?.let { assets ->
                (0 until assets.length()).mapNotNull { i ->
                    val asset = assets.getJSONObject(i)
                    val url = asset.optString("browser_download_url")
                    if (url.endsWith(".apk")) url else null
                }.firstOrNull()
            } ?: return@withContext null

            val currentTag = BuildConfig.RELEASE_TAG
            if (currentTag == "dev") return@withContext null
            val isNewer = isTagNewer(tagName, currentTag)
            ReleaseInfo(tagName, apkUrl, isNewer)
        } catch (e: Exception) {
            Log.e(TAG, "checkForUpdate error", e)
            null
        }
    }

    private fun isTagNewer(remote: String, local: String): Boolean {
        val remoteNum = remote.trimStart('v').replace("-", ".").split(".").mapNotNull { it.toIntOrNull() }
        val localNum = local.trimStart('v').replace("-", ".").split(".").mapNotNull { it.toIntOrNull() }
        for (i in 0 until maxOf(remoteNum.size, localNum.size)) {
            val r = remoteNum.getOrElse(i) { 0 }
            val l = localNum.getOrElse(i) { 0 }
            if (r > l) return true
            if (r < l) return false
        }
        return false
    }

    /** Return value -2L means APK was already cached — install triggered directly, no progress to track. */
    const val DOWNLOAD_ID_CACHED = -2L

    fun downloadAndInstall(context: Context, apkUrl: String, tagName: String): Long {
        val filename = "classnote-$tagName.apk"
        AppPreferences(context).activeApkFileName = filename
        val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        downloadsDir?.listFiles { f ->
            f.name.startsWith("classnote-") && f.name.endsWith(".apk") && f.name != filename
        }?.forEach { it.delete() }

        val cachedFile = File(downloadsDir, filename)
        if (cachedFile.exists() && cachedFile.length() > 0) {
            triggerInstallFromFile(context, cachedFile)
            return DOWNLOAD_ID_CACHED
        }

        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

        val request = DownloadManager.Request(Uri.parse(apkUrl)).apply {
            setTitle("ClassNote 更新")
            setDescription("正在下載版本 $tagName...")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, filename)
            setMimeType("application/vnd.android.package-archive")
        }

        return dm.enqueue(request)
    }

    fun getDownloadedApkFile(context: Context): File? {
        val filename = AppPreferences(context).activeApkFileName.takeIf { it.isNotEmpty() } ?: return null
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), filename)
        return if (file.exists() && file.length() > 0) file else null
    }

    fun triggerInstallFromFile(context: Context, apkFile: File) {
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
            context.startActivity(Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
            })
            apkFile.delete()
        } catch (e: Exception) {
            Log.e(TAG, "triggerInstallFromFile error", e)
            android.widget.Toast.makeText(context, "無法開啟安裝介面：${e.message}", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    fun queryProgress(context: Context, downloadId: Long): Int {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val cursor = dm.query(DownloadManager.Query().setFilterById(downloadId))
        if (!cursor.moveToFirst()) {
            cursor.close()
            ErrorLogger.e(TAG, "APK 下載記錄不存在 downloadId=$downloadId")
            return -1
        }
        val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
        val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
        val downloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
        val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
        cursor.close()
        return when {
            status == DownloadManager.STATUS_SUCCESSFUL -> 100
            status == DownloadManager.STATUS_FAILED -> {
                val reasonStr = downloadManagerReasonString(reason)
                Log.e(TAG, "APK download failed reason=$reason ($reasonStr)")
                ErrorLogger.e(TAG, "APK 下載失敗：$reasonStr (code=$reason)")
                -1
            }
            total > 0 -> (downloaded * 100 / total).toInt()
            else -> 0
        }
    }

    private fun downloadManagerReasonString(reason: Int) = when (reason) {
        DownloadManager.ERROR_CANNOT_RESUME         -> "ERROR_CANNOT_RESUME"
        DownloadManager.ERROR_DEVICE_NOT_FOUND      -> "ERROR_DEVICE_NOT_FOUND"
        DownloadManager.ERROR_FILE_ALREADY_EXISTS   -> "ERROR_FILE_ALREADY_EXISTS"
        DownloadManager.ERROR_FILE_ERROR            -> "ERROR_FILE_ERROR"
        DownloadManager.ERROR_HTTP_DATA_ERROR       -> "ERROR_HTTP_DATA_ERROR"
        DownloadManager.ERROR_INSUFFICIENT_SPACE    -> "ERROR_INSUFFICIENT_SPACE"
        DownloadManager.ERROR_TOO_MANY_REDIRECTS    -> "ERROR_TOO_MANY_REDIRECTS"
        DownloadManager.ERROR_UNHANDLED_HTTP_CODE   -> "ERROR_UNHANDLED_HTTP_CODE"
        DownloadManager.ERROR_UNKNOWN               -> "ERROR_UNKNOWN"
        DownloadManager.PAUSED_QUEUED_FOR_WIFI      -> "PAUSED_QUEUED_FOR_WIFI"
        DownloadManager.PAUSED_WAITING_FOR_NETWORK  -> "PAUSED_WAITING_FOR_NETWORK"
        DownloadManager.PAUSED_WAITING_TO_RETRY     -> "PAUSED_WAITING_TO_RETRY"
        else                                        -> "reason=$reason"
    }

}

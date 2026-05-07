package com.rendy.classnote.data

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
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
        val cachedFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), filename)
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

        val downloadId = dm.enqueue(request)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                if (id != downloadId) return
                ctx.unregisterReceiver(this)

                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor = dm.query(query)
                val success = cursor.moveToFirst() &&
                    cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) == DownloadManager.STATUS_SUCCESSFUL
                cursor.close()
                if (!success) return

                val apkUri = dm.getUriForDownloadedFile(downloadId) ?: return
                val installIntent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                    data = apkUri
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                    putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                }
                try { ctx.startActivity(installIntent) } catch (e: Exception) {
                    Log.e(TAG, "triggerInstall error", e)
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
        }

        return downloadId
    }

    private fun triggerInstallFromFile(context: Context, apkFile: File) {
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
            context.startActivity(Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                data = uri
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
            })
        } catch (e: Exception) {
            Log.e(TAG, "triggerInstallFromFile error", e)
        }
    }

    fun queryProgress(context: Context, downloadId: Long): Int {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val cursor = dm.query(DownloadManager.Query().setFilterById(downloadId))
        if (!cursor.moveToFirst()) { cursor.close(); return -1 }
        val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
        val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
        val downloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
        cursor.close()
        return when {
            status == DownloadManager.STATUS_SUCCESSFUL -> 100
            status == DownloadManager.STATUS_FAILED -> -1
            total > 0 -> (downloaded * 100 / total).toInt()
            else -> 0
        }
    }

}

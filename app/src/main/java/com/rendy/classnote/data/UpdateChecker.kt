package com.rendy.classnote.data

import android.content.Context
import android.content.Intent
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

    private fun apkDir(context: Context): File =
        File(context.filesDir, "apk_updates").also { it.mkdirs() }

    suspend fun downloadApk(
        context: Context,
        apkUrl: String,
        tagName: String,
        onProgress: (Int) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val prefs = AppPreferences(context)
            val dir = apkDir(context)
            val filename = "classnote-$tagName.apk"
            // Clean old APKs and stale tmp files
            dir.listFiles { f -> f.name != filename }?.forEach { it.delete() }

            val destFile = File(dir, filename)
            if (destFile.exists() && destFile.length() > 0 && prefs.apkDownloadComplete) {
                withContext(Dispatchers.Main) { onProgress(100) }
                return@withContext true
            }

            // Delete potentially corrupt destFile before re-downloading
            destFile.delete()
            prefs.apkDownloadComplete = false

            val conn = URL(apkUrl).openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 60000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", "ClassNote-Updater/1.0")
            try {
                conn.connect()
                if (conn.responseCode !in 200..299) {
                    ErrorLogger.e(TAG, "APK 下載失敗：HTTP ${conn.responseCode} url=$apkUrl")
                    return@withContext false
                }
                val total = conn.contentLengthLong
                var downloaded = 0L
                val tmp = File(dir, "$filename.tmp")
                conn.inputStream.use { input ->
                    tmp.outputStream().use { output ->
                        val buf = ByteArray(8192)
                        var read: Int
                        while (input.read(buf).also { read = it } != -1) {
                            output.write(buf, 0, read)
                            downloaded += read
                            if (total > 0) {
                                val pct = (downloaded * 100 / total).toInt()
                                withContext(Dispatchers.Main) { onProgress(pct) }
                            }
                        }
                    }
                }
                // Verify size matches Content-Length
                if (total > 0 && downloaded != total) {
                    tmp.delete()
                    ErrorLogger.e(TAG, "APK 下載截斷：預期=${total}B 實際=${downloaded}B")
                    return@withContext false
                }
                // Verify ZIP magic bytes (APK = ZIP, must start with PK\x03\x04)
                val magic = ByteArray(4)
                tmp.inputStream().use { it.read(magic) }
                if (magic[0] != 0x50.toByte() || magic[1] != 0x4B.toByte()) {
                    tmp.delete()
                    ErrorLogger.e(TAG, "下載到的檔案不是有效 APK（大小=${downloaded}B，可能是 HTML）")
                    return@withContext false
                }
                tmp.renameTo(destFile)
            } finally {
                conn.disconnect()
            }
            prefs.activeApkFileName = filename
            prefs.apkDownloadComplete = true
            true
        } catch (e: Exception) {
            Log.e(TAG, "downloadApk failed", e)
            ErrorLogger.e(TAG, "APK 下載例外：${e.javaClass.simpleName} ${e.message}", e)
            false
        }
    }

    fun getDownloadedApkFile(context: Context): File? {
        val prefs = AppPreferences(context)
        if (!prefs.apkDownloadComplete) return null
        val filename = prefs.activeApkFileName.takeIf { it.isNotEmpty() } ?: return null
        val file = File(apkDir(context), filename)
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
}

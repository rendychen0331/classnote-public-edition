package com.rendy.classnote.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

private const val TAG = "FeatureDownloader"
private const val MANIFEST_URL =
    "https://github.com/rendychen0331/classnote-public-edition/releases/latest/download/features.json"

// downloadUrl 來自遠端 manifest，屬不可信輸入。只允許 https + 這些 GitHub release 主機，
// 否則被竄改的 manifest 可把下載指向任意伺服器（見 SEC-H1）。單一驗證點，所有下載都必須先過。
private val ALLOWED_DOWNLOAD_HOSTS = setOf(
    "github.com",
    "objects.githubusercontent.com",
    "release-assets.githubusercontent.com",
    "raw.githubusercontent.com"
)

private fun isAllowedDownloadUrl(rawUrl: String): Boolean {
    return try {
        val u = URL(rawUrl)
        u.protocol.equals("https", ignoreCase = true) &&
            ALLOWED_DOWNLOAD_HOSTS.any { host ->
                u.host.equals(host, ignoreCase = true) || u.host.endsWith(".$host", ignoreCase = true)
            }
    } catch (e: Exception) {
        false
    }
}

data class FeatureInfo(
    val id: String,
    val downloadUrl: String,
    val sha256: String,
    val minAppVersion: Int,
    val sizeBytes: Long
)

sealed class DownloadResult {
    object Success : DownloadResult()
    data class Error(val message: String) : DownloadResult()
    object VersionTooOld : DownloadResult()
    object AlreadyUpToDate : DownloadResult()
}

object FeatureDownloader {

    suspend fun fetchManifest(): List<FeatureInfo> = withContext(Dispatchers.IO) {
        try {
            val text = URL(MANIFEST_URL).openStream().bufferedReader().readText()
            val arr = JSONObject(text).getJSONArray("features")
            (0 until arr.length()).map {
                val obj = arr.getJSONObject(it)
                FeatureInfo(
                    id             = obj.getString("id"),
                    downloadUrl    = obj.getString("downloadUrl"),
                    sha256         = obj.getString("sha256"),
                    minAppVersion  = obj.optInt("minAppVersion", 0),
                    sizeBytes      = obj.optLong("sizeBytes", 0L)
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchManifest failed", e)
            ErrorLogger.e(TAG, "取得功能清單失敗：${e.javaClass.simpleName} ${e.message}", e)
            emptyList()
        }
    }

    suspend fun download(context: Context, info: FeatureInfo, appVersionCode: Int): DownloadResult =
        withContext(Dispatchers.IO) {
            if (appVersionCode < info.minAppVersion) return@withContext DownloadResult.VersionTooOld

            // 拒絕非 https / 非允許主機的下載網址（竄改的 manifest 防護）
            if (!isAllowedDownloadUrl(info.downloadUrl)) {
                val msg = "拒絕不受信任的下載網址：${info.downloadUrl}"
                Log.e(TAG, "download ${info.id} rejected: untrusted url ${info.downloadUrl}")
                ErrorLogger.e(TAG, msg)
                return@withContext DownloadResult.Error("untrusted download url")
            }

            val destFile = dexFile(context, info.id)
            if (destFile.exists() && sha256(destFile) == info.sha256) {
                return@withContext DownloadResult.AlreadyUpToDate
            }

            try {
                val tmp = File(destFile.parent, "${info.id}.tmp")
                tmp.parentFile?.mkdirs()

                val conn = URL(info.downloadUrl).openConnection() as HttpURLConnection
                try {
                    conn.connect()
                    if (conn.responseCode !in 200..299) {
                        val msg = "HTTP ${conn.responseCode} — ${info.downloadUrl}"
                        Log.e(TAG, "download ${info.id} failed: $msg")
                        ErrorLogger.e(TAG, "下載 ${info.id} 失敗：$msg")
                        return@withContext DownloadResult.Error("HTTP ${conn.responseCode}")
                    }
                    conn.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
                } finally {
                    conn.disconnect()
                }

                val actual = sha256(tmp)
                if (actual != info.sha256) {
                    tmp.delete()
                    val msg = "SHA256 mismatch for ${info.id}: expected ${info.sha256}, got $actual"
                    Log.e(TAG, msg)
                    ErrorLogger.e(TAG, msg)
                    return@withContext DownloadResult.Error("SHA256 mismatch")
                }

                FeatureManager.unload(info.id)
                // clear stale odex so next load starts fresh
                File(context.codeCacheDir, "features/opt-${info.id}").deleteRecursively()
                destFile.delete()
                tmp.renameTo(destFile)
                // Android 10+ rejects dex files from writable paths
                destFile.setReadOnly()
                DownloadResult.Success
            } catch (e: Exception) {
                Log.e(TAG, "download ${info.id} failed", e)
                ErrorLogger.e(TAG, "下載 ${info.id} 例外：${e.javaClass.simpleName} ${e.message}", e)
                DownloadResult.Error(e.message ?: "download failed")
            }
        }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val buf = ByteArray(8192)
            var read: Int
            while (stream.read(buf).also { read = it } != -1) digest.update(buf, 0, read)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun dexFile(context: Context, featureId: String): File =
        File(context.filesDir, "features/feature-$featureId.dex")
}

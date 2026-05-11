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
    "https://github.com/rendychen0331/classnote-no-gs.json/releases/latest/download/features.json"

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
            emptyList()
        }
    }

    suspend fun download(context: Context, info: FeatureInfo, appVersionCode: Int): DownloadResult =
        withContext(Dispatchers.IO) {
            if (appVersionCode < info.minAppVersion) return@withContext DownloadResult.VersionTooOld

            val destFile = dexFile(context, info.id)
            if (destFile.exists() && sha256(destFile) == info.sha256) {
                return@withContext DownloadResult.AlreadyUpToDate
            }

            try {
                val conn = URL(info.downloadUrl).openConnection() as HttpURLConnection
                conn.connect()
                if (conn.responseCode !in 200..299) {
                    return@withContext DownloadResult.Error("HTTP ${conn.responseCode}")
                }

                val tmp = File(destFile.parent, "${info.id}.tmp")
                tmp.parentFile?.mkdirs()
                conn.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
                conn.disconnect()

                val actual = sha256(tmp)
                if (actual != info.sha256) {
                    tmp.delete()
                    return@withContext DownloadResult.Error("SHA256 mismatch: expected ${info.sha256}, got $actual")
                }

                FeatureManager.unload(info.id)
                tmp.renameTo(destFile)
                DownloadResult.Success
            } catch (e: Exception) {
                Log.e(TAG, "download ${info.id} failed", e)
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

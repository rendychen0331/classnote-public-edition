package com.rendy.classnote.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.Signature
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
import java.security.MessageDigest

object UpdateChecker {

    private const val TAG = "UpdateChecker"
    private const val RELEASES_API = "https://api.github.com/repos/rendychen0331/classnote-public-edition/releases/latest"
    private const val PREFS_NAME = "update_checker"
    private const val KEY_LAST_CHECK = "last_check_ms"
    private const val CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L

    // apkUrl 來自 GitHub API 回應。只允許 https + GitHub release 主機（見 SEC-H2）。
    private val ALLOWED_APK_HOSTS = setOf(
        "github.com",
        "objects.githubusercontent.com",
        "release-assets.githubusercontent.com"
    )

    private fun isAllowedApkUrl(rawUrl: String): Boolean = try {
        val u = URL(rawUrl)
        u.protocol.equals("https", ignoreCase = true) &&
            ALLOWED_APK_HOSTS.any { h -> u.host.equals(h, true) || u.host.endsWith(".$h", true) }
    } catch (e: Exception) {
        false
    }

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
            if (!isAllowedApkUrl(apkUrl)) {
                ErrorLogger.e(TAG, "拒絕不受信任的 APK 下載網址：$apkUrl")
                return@withContext false
            }
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
                if (!tmp.renameTo(destFile)) {
                    tmp.delete()
                    ErrorLogger.e(TAG, "APK rename 失敗：tmp → $filename")
                    return@withContext false
                }
            } finally {
                conn.disconnect()
            }
            prefs.activeApkFileName = filename
            prefs.apkDownloadComplete = true
            // Copy to external files/Downloads for manual access / debugging
            try {
                val extDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                if (extDir != null) {
                    extDir.mkdirs()
                    destFile.copyTo(File(extDir, filename), overwrite = true)
                }
            } catch (e: Exception) {
                Log.w(TAG, "copy to external Downloads failed (non-fatal)", e)
            }
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

    /**
     * 讀取某 APK 檔的簽章憑證 SHA-256 集合。null = 無法解析（視為不可信）。
     */
    private fun signingCertHashesOfApk(context: Context, apkPath: String): Set<String>? {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            PackageManager.GET_SIGNING_CERTIFICATES else @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES
        val info = pm.getPackageArchiveInfo(apkPath, flags) ?: return null
        val signatures: Array<Signature> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val sig = info.signingInfo ?: return null
            (sig.apkContentsSigners ?: return null)
        } else {
            @Suppress("DEPRECATION") (info.signatures ?: return null)
        }
        if (signatures.isEmpty()) return null
        return signatures.map { sha256Hex(it.toByteArray()) }.toSet()
    }

    /** 讀取目前已安裝 App 自身的簽章憑證 SHA-256 集合。 */
    private fun signingCertHashesOfInstalled(context: Context): Set<String>? {
        val pm = context.packageManager
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val info = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                val sig = info.signingInfo ?: return null
                (sig.apkContentsSigners ?: return null).map { sha256Hex(it.toByteArray()) }.toSet()
            } else {
                @Suppress("DEPRECATION")
                val info = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
                @Suppress("DEPRECATION")
                (info.signatures ?: return null).map { sha256Hex(it.toByteArray()) }.toSet()
            }
        } catch (e: Exception) {
            Log.e(TAG, "read installed signature failed", e)
            null
        }
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    /**
     * 下載的 APK 必須與目前安裝的 App 同一簽章金鑰，才允許安裝（見 SEC-H2）。
     * 系統安裝器本身也會擋不同簽章的「更新」，這是應用層縱深防禦：檔案被替換 / 網址被竄改時，
     * 在喚起安裝器前就先拒絕，而非依賴使用者看安裝提示。
     */
    fun isApkSignatureTrusted(context: Context, apkFile: File): Boolean {
        val installed = signingCertHashesOfInstalled(context)
        val downloaded = signingCertHashesOfApk(context, apkFile.absolutePath)
        if (installed.isNullOrEmpty() || downloaded.isNullOrEmpty()) {
            Log.e(TAG, "signature verify failed: installed=${installed?.size} downloaded=${downloaded?.size}")
            return false
        }
        return downloaded == installed
    }

    fun triggerInstallFromFile(context: Context, apkFile: File) {
        try {
            if (!isApkSignatureTrusted(context, apkFile)) {
                ErrorLogger.e(TAG, "APK 簽章與已安裝版本不符，拒絕安裝：${apkFile.name}")
                android.widget.Toast.makeText(
                    context, "更新檔簽章驗證失敗，已阻止安裝", android.widget.Toast.LENGTH_LONG
                ).show()
                apkFile.delete()
                val p = AppPreferences(context)
                p.apkDownloadComplete = false
                p.activeApkFileName = ""
                return
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
            context.startActivity(Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                // 不設 EXTRA_NOT_UNKNOWN_SOURCE：簽章已在應用層驗過，保留系統原本的來源提示（SEC-H2）
            })
            // Reset prefs so UI shows "檢查更新" again.
            // Do NOT delete the file here — the system installer reads it asynchronously
            // via the FileProvider URI; deleting immediately causes "套件剖析失敗".
            // Old APKs are cleaned up next time downloadApk() runs for a new version.
            val prefs = AppPreferences(context)
            prefs.apkDownloadComplete = false
            prefs.activeApkFileName = ""
        } catch (e: Exception) {
            Log.e(TAG, "triggerInstallFromFile error", e)
            android.widget.Toast.makeText(context, "無法開啟安裝介面：${e.message}", android.widget.Toast.LENGTH_LONG).show()
        }
    }
}

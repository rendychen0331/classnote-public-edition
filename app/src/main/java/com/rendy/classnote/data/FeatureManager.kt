package com.rendy.classnote.data

import android.content.Context
import android.util.Log
import com.rendy.classnote.feature.AiFeature
import com.rendy.classnote.feature.AuthFeature
import com.rendy.classnote.feature.BackupFeature
import com.rendy.classnote.feature.FeatureModule
import com.rendy.classnote.feature.SyncFeature
import com.rendy.classnote.feature.WeatherFeature
import dalvik.system.DexClassLoader
import java.io.File

private const val TAG = "FeatureManager"

object FeatureManager {

    private val cache = mutableMapOf<String, FeatureModule>()

    private val featureClassNames = mapOf(
        "google"    to "com.rendy.classnote.feature.google.GoogleFeatureModule",
        "microsoft" to "com.rendy.classnote.feature.microsoft.MicrosoftFeatureModule",
        "ai"        to "com.rendy.classnote.feature.ai.AiFeatureModule",
        "weather"   to "com.rendy.classnote.feature.weather.WeatherFeatureModule"
    )

    fun isDownloaded(context: Context, featureId: String): Boolean =
        dexFile(context, featureId).exists()

    fun getInstalledIds(context: Context): List<String> =
        featureClassNames.keys.filter { isDownloaded(context, it) }

    fun getSync(context: Context, featureId: String): SyncFeature? =
        load(context, featureId)?.sync()

    fun getBackup(context: Context, featureId: String): BackupFeature? =
        load(context, featureId)?.backup()

    fun getAuth(context: Context, featureId: String): AuthFeature? =
        load(context, featureId)?.auth()

    fun getAi(context: Context): AiFeature? =
        load(context, "ai")?.ai()

    fun getWeather(context: Context): WeatherFeature? =
        load(context, "weather")?.weather()

    fun unload(featureId: String) {
        cache.remove(featureId)
    }

    fun delete(context: Context, featureId: String) {
        unload(featureId)
        dexFile(context, featureId).delete()
        optimizedDir(context, featureId).deleteRecursively()
    }

    private fun load(context: Context, featureId: String): FeatureModule? {
        cache[featureId]?.let { return it }
        val dex = dexFile(context, featureId)
        if (!dex.exists()) return null
        return tryLoad(context, featureId, dex)
            ?: run {
                // stale odex can prevent load — clear and retry once
                optimizedDir(context, featureId).deleteRecursively()
                tryLoad(context, featureId, dex)
            }
            ?: run {
                // dex is incompatible with current app — delete so user re-downloads fresh
                Log.w(TAG, "Incompatible dex for $featureId, deleting for re-download")
                dex.delete()
                optimizedDir(context, featureId).deleteRecursively()
                null
            }
    }

    private fun tryLoad(context: Context, featureId: String, dex: File): FeatureModule? {
        return try {
            val optDir = optimizedDir(context, featureId).also { it.mkdirs() }
            val loader = DexClassLoader(
                dex.absolutePath,
                optDir.absolutePath,
                null,
                context.classLoader
            )
            val className = featureClassNames[featureId]
                ?: error("Unknown feature id: $featureId")
            val module = loader.loadClass(className)
                .getDeclaredConstructor()
                .newInstance() as FeatureModule
            cache[featureId] = module
            module
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load feature $featureId", e)
            null
        }
    }

    private fun dexFile(context: Context, featureId: String): File =
        File(context.filesDir, "features/feature-$featureId.dex")

    private fun optimizedDir(context: Context, featureId: String): File =
        File(context.codeCacheDir, "features/opt-$featureId")
}

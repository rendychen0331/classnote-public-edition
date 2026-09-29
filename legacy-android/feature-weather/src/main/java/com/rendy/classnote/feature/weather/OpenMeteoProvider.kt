package com.rendy.classnote.feature.weather

import android.util.Log
import com.rendy.classnote.feature.ForecastItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

internal object OpenMeteoProvider {

    private const val TAG = "OpenMeteoProvider"
    private const val GEO_URL = "https://geocoding-api.open-meteo.com/v1/search"
    private const val FORECAST_URL = "https://api.open-meteo.com/v1/forecast"

    suspend fun fetchForecast(displayName: String): Result<List<ForecastItem>> = withContext(Dispatchers.IO) {
        try {
            val geoUrl = URL("$GEO_URL?name=${java.net.URLEncoder.encode(displayName, "UTF-8")}&count=1&language=zh&format=json")
            val geoConn = geoUrl.openConnection() as HttpURLConnection
            geoConn.connectTimeout = 10_000
            geoConn.readTimeout = 15_000
            if (geoConn.responseCode != 200) return@withContext Result.failure(Exception("地理編碼失敗 HTTP ${geoConn.responseCode}"))
            val geoJson = JSONObject(geoConn.inputStream.bufferedReader().readText())
            geoConn.disconnect()
            val results = geoJson.optJSONArray("results")
                ?: return@withContext Result.failure(Exception("找不到「$displayName」的地理位置"))
            val loc = results.getJSONObject(0)
            val lat = loc.getDouble("latitude")
            val lon = loc.getDouble("longitude")

            val fUrl = URL("$FORECAST_URL?latitude=$lat&longitude=$lon&daily=weathercode,temperature_2m_max,temperature_2m_min,precipitation_probability_max&forecast_days=3&timezone=Asia%2FTaipei")
            val fConn = fUrl.openConnection() as HttpURLConnection
            fConn.connectTimeout = 10_000
            fConn.readTimeout = 15_000
            if (fConn.responseCode != 200) return@withContext Result.failure(Exception("天氣查詢失敗 HTTP ${fConn.responseCode}"))
            val fJson = JSONObject(fConn.inputStream.bufferedReader().readText())
            fConn.disconnect()

            val daily = fJson.getJSONObject("daily")
            val times = daily.getJSONArray("time")
            val codes = daily.getJSONArray("weathercode")
            val maxTemps = daily.getJSONArray("temperature_2m_max")
            val minTemps = daily.getJSONArray("temperature_2m_min")
            val pops = daily.getJSONArray("precipitation_probability_max")

            val items = (0 until times.length()).map { i ->
                ForecastItem(
                    timePeriod = times.getString(i),
                    description = weatherCodeToDesc(codes.getInt(i)),
                    tempMin = minTemps.getDouble(i).toInt(),
                    tempMax = maxTemps.getDouble(i).toInt(),
                    rainProb = pops.optInt(i, 0)
                )
            }
            Result.success(items)
        } catch (e: Exception) {
            Log.e(TAG, "fetchForecast failed for $displayName", e)
            Result.failure(e)
        }
    }

    private fun weatherCodeToDesc(code: Int) = when (code) {
        0 -> "晴天"
        1 -> "晴時多雲"
        2 -> "多雲"
        3 -> "陰天"
        45, 48 -> "霧"
        51, 53, 55 -> "毛毛雨"
        56, 57 -> "凍雨"
        61, 63, 65 -> "雨"
        66, 67 -> "凍雨"
        71, 73, 75 -> "雪"
        77 -> "冰粒"
        80, 81, 82 -> "陣雨"
        85, 86 -> "陣雪"
        95 -> "雷雨"
        96, 99 -> "強雷雨"
        else -> "未知"
    }
}

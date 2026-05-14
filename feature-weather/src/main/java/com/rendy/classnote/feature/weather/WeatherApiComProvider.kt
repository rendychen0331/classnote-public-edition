package com.rendy.classnote.feature.weather

import android.util.Log
import com.rendy.classnote.feature.ForecastItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

internal object WeatherApiComProvider {

    private const val TAG = "WeatherApiComProvider"
    private const val BASE_URL = "https://api.weatherapi.com/v1/forecast.json"

    suspend fun fetchForecast(displayName: String, apiKey: String): Result<List<ForecastItem>> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext Result.failure(Exception("請先在設定頁填入 WeatherAPI.com API Key"))
        try {
            val url = URL("$BASE_URL?key=$apiKey&q=${java.net.URLEncoder.encode(displayName, "UTF-8")}&days=3&lang=zh")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            val code = conn.responseCode
            if (code != 200) return@withContext Result.failure(Exception("HTTP $code"))
            val json = JSONObject(conn.inputStream.bufferedReader().readText())
            conn.disconnect()

            val forecastDays = json.getJSONObject("forecast").getJSONArray("forecastday")
            val items = (0 until forecastDays.length()).map { i ->
                val day = forecastDays.getJSONObject(i)
                val date = day.getString("date")
                val dayData = day.getJSONObject("day")
                ForecastItem(
                    timePeriod = date,
                    description = dayData.getJSONObject("condition").getString("text"),
                    tempMin = dayData.getDouble("mintemp_c").toInt(),
                    tempMax = dayData.getDouble("maxtemp_c").toInt(),
                    rainProb = dayData.optInt("daily_chance_of_rain", 0)
                )
            }
            Result.success(items)
        } catch (e: Exception) {
            Log.e(TAG, "fetchForecast failed for $displayName", e)
            Result.failure(e)
        }
    }
}

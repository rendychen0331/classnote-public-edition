package com.rendy.classnote.data

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray

class WeatherPreferences(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 使用者儲存的地區清單（有序） */
    var savedLocations: List<String>
        get() {
            val json = prefs.getString(KEY_LOCATIONS, null) ?: return emptyList()
            return try {
                val arr = JSONArray(json)
                (0 until arr.length()).map { arr.getString(it) }
            } catch (e: Exception) {
                emptyList()
            }
        }
        set(value) {
            val arr = JSONArray()
            value.forEach { arr.put(it) }
            prefs.edit { putString(KEY_LOCATIONS, arr.toString()) }
        }

    /** 每日天氣通知開關 */
    var weatherNotifEnabled: Boolean
        get() = prefs.getBoolean(KEY_NOTIF_ENABLED, false)
        set(value) = prefs.edit { putBoolean(KEY_NOTIF_ENABLED, value) }

    /** 通知時間（小時，24小時制） */
    var weatherNotifHour: Int
        get() = prefs.getInt(KEY_NOTIF_HOUR, 7)
        set(value) = prefs.edit { putInt(KEY_NOTIF_HOUR, value) }

    /** 通知時間（分鐘） */
    var weatherNotifMinute: Int
        get() = prefs.getInt(KEY_NOTIF_MINUTE, 0)
        set(value) = prefs.edit { putInt(KEY_NOTIF_MINUTE, value) }

    /** 通知地區（縣市名稱，空字串表示未設定） */
    var weatherNotifLocation: String
        get() = prefs.getString(KEY_NOTIF_LOCATION, "") ?: ""
        set(value) = prefs.edit { putString(KEY_NOTIF_LOCATION, value) }

    /** 天氣資料來源：cwa / open-meteo / weatherapi */
    var weatherProvider: String
        get() = prefs.getString(KEY_PROVIDER, "cwa") ?: "cwa"
        set(value) = prefs.edit { putString(KEY_PROVIDER, value) }

    companion object {
        private const val PREFS_NAME = "weather_prefs"
        private const val KEY_LOCATIONS = "saved_locations"
        private const val KEY_NOTIF_ENABLED = "notif_enabled"
        private const val KEY_NOTIF_HOUR = "notif_hour"
        private const val KEY_NOTIF_MINUTE = "notif_minute"
        private const val KEY_NOTIF_LOCATION = "notif_location"
        private const val KEY_PROVIDER = "weather_provider"
    }
}

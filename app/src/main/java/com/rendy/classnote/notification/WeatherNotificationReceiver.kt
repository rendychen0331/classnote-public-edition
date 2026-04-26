package com.rendy.classnote.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.rendy.classnote.R
import com.rendy.classnote.data.AppPreferences
import com.rendy.classnote.data.WeatherPreferences
import com.rendy.classnote.data.remote.WeatherApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WeatherNotificationReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION = "com.rendy.classnote.WEATHER_NOTIFICATION"
        private const val CHANNEL_ID = "weather_daily"
        private const val NOTIF_ID = 2000
    }

    override fun onReceive(context: Context, intent: Intent) {
        val weatherPrefs = WeatherPreferences(context)

        // 決定要查哪個地區
        val location = weatherPrefs.weatherNotifLocation.ifEmpty {
            weatherPrefs.savedLocations.firstOrNull()
        } ?: return

        // 排程明天的通知
        WeatherNotificationScheduler.schedule(context)

        val apiKey = AppPreferences(context).cwaApiKey
        if (apiKey.isBlank()) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                WeatherApi.fetchForecast(location, apiKey).onSuccess { forecasts ->
                    val first = forecasts.firstOrNull() ?: return@onSuccess
                    val text = "${first.description}，${first.tempMin}°C～${first.tempMax}°C，降雨 ${first.rainProb}%"
                    showNotification(context, location, text)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun showNotification(context: Context, location: String, text: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "天氣通知", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "每日天氣預報推播"
                }
            )
        }

        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_weather)
            .setContentTitle("$location 今日天氣")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .build()

        nm.notify(NOTIF_ID, notif)
    }
}

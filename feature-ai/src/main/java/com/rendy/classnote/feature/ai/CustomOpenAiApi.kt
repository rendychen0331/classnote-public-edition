package com.rendy.classnote.feature.ai

import android.util.Log
import com.rendy.classnote.feature.EventInfo
import com.rendy.classnote.feature.NotificationInput
import com.rendy.classnote.feature.PeriodTimeBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

internal object CustomOpenAiApi {

    private const val TAG = "CustomOpenAiApi"

    suspend fun analyzeNotifications(
        endpoint: String,
        model: String,
        apiKey: String,
        inputs: List<NotificationInput>,
        periodTimes: List<PeriodTimeBridge> = emptyList()
    ): List<List<EventInfo>> = withContext(Dispatchers.IO) {
        if (inputs.isEmpty()) return@withContext emptyList()
        val prompt = GeminiApi.buildBatchPrompt(inputs, periodTimes)
        var responseText: String? = null
        var success = false
        try {
            val url = URL(endpoint)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            if (apiKey.isNotBlank()) conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.doOutput = true
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000

            val body = JSONObject().apply {
                put("model", model)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", GeminiApi.SYSTEM_INSTRUCTION)
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", prompt)
                    })
                })
            }.toString()
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body) }

            val code = conn.responseCode
            if (code != 200) {
                Log.e(TAG, "analyzeNotifications HTTP $code: ${conn.errorStream?.bufferedReader()?.readText()}")
            } else {
                responseText = JSONObject(conn.inputStream.bufferedReader(Charsets.UTF_8).readText())
                    .getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content").trim()
                success = true
            }
        } catch (e: Exception) {
            Log.e(TAG, "analyzeNotifications failed", e)
        }
        if (!success || responseText == null) return@withContext List(inputs.size) { emptyList() }
        GeminiApi.parseNotificationJsonText(responseText, inputs.size)
    }

    suspend fun chatWithContext(
        endpoint: String,
        model: String,
        apiKey: String,
        noteContext: String,
        history: List<Pair<String, Boolean>>,
        userMessage: String
    ): String? = withContext(Dispatchers.IO) {
        var result: String? = null
        try {
            val url = URL(endpoint)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            if (apiKey.isNotBlank()) conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.doOutput = true
            conn.connectTimeout = 30_000
            conn.readTimeout = 60_000

            val messages = JSONArray()
            messages.put(JSONObject().apply {
                put("role", "system")
                put("content", "你是一個課堂筆記助理，以繁體中文回答問題。以下是本堂課的課堂筆記總結，作為對話背景參考：\n\n$noteContext")
            })
            history.forEach { (text, isUser) ->
                messages.put(JSONObject().apply {
                    put("role", if (isUser) "user" else "assistant")
                    put("content", text)
                })
            }
            messages.put(JSONObject().apply {
                put("role", "user")
                put("content", userMessage)
            })

            val body = JSONObject().apply {
                put("model", model)
                put("messages", messages)
                put("temperature", 0.5)
                put("max_tokens", 2000)
            }.toString()

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body) }

            val code = conn.responseCode
            if (code == 200) {
                result = JSONObject(conn.inputStream.bufferedReader(Charsets.UTF_8).readText())
                    .getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content").trim()
            } else {
                Log.e(TAG, "chatWithContext HTTP $code: ${conn.errorStream?.bufferedReader()?.readText()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "chatWithContext failed", e)
        }
        result
    }
}

package com.rendy.classnote.notification

import android.app.Notification
import android.os.Handler
import androidx.core.app.NotificationCompat
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import com.rendy.classnote.data.AppPreferences
import com.rendy.classnote.data.FeatureManager
import com.rendy.classnote.data.local.ClassNoteDatabase
import com.rendy.classnote.data.local.dao.ReminderNotificationDao
import com.rendy.classnote.data.local.entity.ReminderEntity
import com.rendy.classnote.data.local.entity.ReminderNotificationEntity
import com.rendy.classnote.feature.EventInfo
import com.rendy.classnote.feature.NotificationInput
import com.rendy.classnote.feature.PeriodTimeBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class ClassNoteNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val seenKeys = mutableSetOf<String>()

    private val handler = Handler(Looper.getMainLooper())
    private val pendingBatch = mutableListOf<NotificationInput>()
    private val processBatchRunnable = Runnable { flushBatch() }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val prefs = AppPreferences(applicationContext)
        if (!prefs.notificationListenerAutoAdd) return
        if (sbn.packageName == packageName) return

        val monitored = prefs.monitoredPackages
        if (monitored.isNotEmpty() && !monitored.contains(sbn.packageName)) return

        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim() ?: return
        if (title.isBlank()) return

        // MessagingStyle 提前取出，用於計算 channelName
        val messagingStyle = NotificationCompat.MessagingStyle
            .extractMessagingStyleFromNotification(sbn.notification)

        // Gmail 摘要通知（「2封新郵件」等）開頭為數字，不含任何作業資訊，直接忽略
        if (sbn.packageName == "com.google.android.gm" &&
            title.firstOrNull()?.isDigit() == true) return

        // 群組聊天（LINE、WhatsApp 等）的 conversationTitle 是群組名，不含「：發送者」後綴
        // 用它作頻道 key，避免同一群組多人發言被記成不同頻道
        // Gmail 個別郵件 title 為寄件人，統一以 "Gmail" 為頻道，避免每位寄件人變獨立頻道
        val channelName = when {
            sbn.packageName == "com.google.android.gm" -> "Gmail"
            else -> messagingStyle?.conversationTitle?.toString()?.trim()
                ?.takeIf { it.isNotBlank() } ?: title
        }

        // 頻道黑名單過濾（優先於白名單，黑名單內的頻道直接忽略）
        val channelBlacklist = prefs.getBlacklistedChannels()[sbn.packageName]
        if (!channelBlacklist.isNullOrEmpty() && channelBlacklist.contains(channelName)) return

        // 頻道白名單過濾（不透過 AI，直接用 channelName 比對）
        val channelWhitelist = prefs.getMonitoredChannels()[sbn.packageName]
        if (!channelWhitelist.isNullOrEmpty() && !channelWhitelist.contains(channelName)) return

        // 記錄看過的頻道名稱（供 UI 設定白名單），不受 AI key 或開關影響
        prefs.addSeenChannel(sbn.packageName, channelName)

        // 確認至少有一個啟用的 model 有 key
        val hasAnyKey = with(prefs) {
            (geminiEnabled && geminiApiKey.isNotBlank()) ||
            (mimoEnabled   && mimoApiKey.isNotBlank())   ||
            (claudeEnabled && claudeApiKey.isNotBlank()) ||
            (openaiEnabled && openaiApiKey.isNotBlank()) ||
            (groqEnabled      && groqApiKey.isNotBlank()) ||
            (deepseekEnabled  && deepseekApiKey.isNotBlank())
        }
        if (!hasAnyKey) return

        val text = if (messagingStyle != null && messagingStyle.messages.isNotEmpty()) {
            messagingStyle.messages
                .takeLast(3)
                .joinToString("\n") { it.text?.toString() ?: "" }
                .trim()
        } else {
            (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
                ?: extras.getCharSequence(Notification.EXTRA_TEXT))
                ?.toString()?.trim()
        } ?: return

        if (text.isBlank()) return

        if (prefs.sensitiveKeywordsEnabled) {
            val combined = "$title $text"
            if (SENSITIVE_KEYWORDS.any { combined.contains(it, ignoreCase = true) }) return
        }

        val userBlacklist = prefs.userKeywordBlacklist
        if (userBlacklist.isNotEmpty()) {
            val combined = "$title $text"
            if (userBlacklist.any { combined.contains(it, ignoreCase = true) }) return
        }

        val dedupeKey = "${sbn.packageName}|$title|${text.take(100)}"
        val isNew = synchronized(seenKeys) {
            if (seenKeys.contains(dedupeKey)) false
            else {
                seenKeys.add(dedupeKey)
                if (seenKeys.size > 200) seenKeys.clear()
                true
            }
        }
        if (!isNew) return

        val appLabel = try {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(sbn.packageName, 0)
            ).toString()
        } catch (_: Exception) { sbn.packageName }

        synchronized(pendingBatch) {
            pendingBatch.add(NotificationInput(appLabel, title, text))
        }

        // Reset debounce timer — flush 3 seconds after last notification arrives
        handler.removeCallbacks(processBatchRunnable)
        handler.postDelayed(processBatchRunnable, BATCH_DELAY_MS)
    }

    private fun flushBatch() {
        val batch = synchronized(pendingBatch) {
            if (pendingBatch.isEmpty()) return
            val copy = pendingBatch.toList()
            pendingBatch.clear()
            copy
        }

        NotificationHelper.createAiStatusChannel(applicationContext)
        NotificationHelper.showAiProcessing(applicationContext, batch.size)

        scope.launch {
            try {
                val prefs = AppPreferences(applicationContext)
                val ai = FeatureManager.getAi(applicationContext)
                if (ai == null) { NotificationHelper.cancelAiStatus(applicationContext); return@launch }
                val (provider, apiKey) = resolveNotifProviderKey(prefs)
                    ?: run { NotificationHelper.cancelAiStatus(applicationContext); return@launch }
                val db = ClassNoteDatabase.getDatabase(applicationContext)
                val periodTimes = db.periodTimeDao().getAllPeriodTimesOnce()
                val bridges = periodTimes.map { PeriodTimeBridge(it.period, it.startMinute) }
                val results = ai.analyzeNotifications(provider, apiKey, batch, bridges)

                // Flatten results with source tracking
                val allEvents = mutableListOf<Pair<EventInfo, NotificationInput>>()
                results.forEachIndexed { i, events ->
                    val input = batch.getOrNull(i) ?: return@forEachIndexed
                    events.forEach { event -> allEvents.add(event to input) }
                }

                if (allEvents.size > 5) {
                    // Too many events — ask user to confirm before inserting
                    val json = JSONArray().also { arr ->
                        allEvents.forEach { (event, input) ->
                            arr.put(JSONObject().apply {
                                put("title", event.title)
                                put("dueDate", event.dueDate ?: JSONObject.NULL)
                                put("dueTime", event.dueTime ?: JSONObject.NULL)
                                put("category", event.category)
                                put("note", event.note)
                                put("appLabel", input.appLabel)
                                put("notifTitle", input.title)
                                put("notifText", input.text)
                            })
                        }
                    }.toString()
                    prefs.pendingAiEvents = json
                    NotificationHelper.cancelAiStatus(applicationContext)
                    NotificationHelper.showAiPendingConfirmation(
                        applicationContext, allEvents.size, allEvents.map { it.first.title }
                    )
                    return@launch
                }

                val dao = db.reminderDao()
                val notifDao = db.reminderNotificationDao()

                val addedTitles = mutableListOf<String>()
                val recognizedTitles = allEvents.map { it.first.title }

                allEvents.forEach { (event, input) ->
                    try {
                        val eventDueDate = event.dueDate
                        val duplicate = if (eventDueDate != null) {
                            dao.findByTitleAndDueDate(event.title, eventDueDate) != null
                        } else {
                            dao.findByTitleWithNullDueDate(event.title) != null
                        }
                        if (duplicate) return@forEach

                        val reminderId = dao.insertReminder(
                            ReminderEntity(
                                title = event.title,
                                note = event.note.ifBlank { input.text.take(300) },
                                dueDate = event.dueDate,
                                dueTime = event.dueTime,
                                category = event.category,
                                syncSource = "notify",
                                sourceName = input.let { inp ->
                                    val groupName = inp.title.trim()
                                    if (groupName.isNotBlank() && groupName != inp.appLabel)
                                        "${inp.appLabel}・$groupName"
                                    else inp.appLabel
                                },
                                rawNotification = buildString {
                                    append("[${input.appLabel}]")
                                    if (input.title.isNotBlank()) append("\n${input.title}")
                                    if (input.text.isNotBlank()) append("\n${input.text}")
                                }
                            )
                        )

                        val effectiveDueDate = event.dueDate
                            ?: if (event.dueTime != null) LocalDate.now().toString() else null
                        if (effectiveDueDate != null) {
                            scheduleDefaultNotifications(reminderId, effectiveDueDate, event.dueTime, notifDao)
                        }
                        addedTitles.add(event.title)
                        Log.i(TAG, "Auto-added from notification: ${event.title}")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to save event: ${event.title}", e)
                    }
                }

                if (addedTitles.isNotEmpty()) {
                    NotificationHelper.showAiResult(applicationContext, addedTitles.size, addedTitles)
                    com.rendy.classnote.widget.ClassNoteWidget.refreshAll(applicationContext)
                } else {
                    NotificationHelper.showAiNoResult(applicationContext, recognizedTitles)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to process notification batch", e)
                NotificationHelper.cancelAiStatus(applicationContext)
            }
        }
    }

    private suspend fun scheduleDefaultNotifications(
        reminderId: Long,
        dueDate: String,
        dueTime: String?,
        notificationDao: ReminderNotificationDao
    ) {
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val prefs = AppPreferences(applicationContext)
        val triggerTimes: List<Long> = if (dueTime != null) {
            val (h, m) = dueTime.split(":").map { it.toInt() }
            val base = LocalDateTime.of(LocalDate.parse(dueDate), LocalTime.of(h, m))
            listOf(
                base.minusDays(1).atZone(zone).toInstant().toEpochMilli(),
                base.minusHours(1).atZone(zone).toInstant().toEpochMilli(),
                base.minusMinutes(1).atZone(zone).toInstant().toEpochMilli()
            )
        } else {
            val base = LocalDate.parse(dueDate).atTime(prefs.defaultRemindHour, prefs.defaultRemindMinute)
            listOf(base.atZone(zone).toInstant().toEpochMilli())
        }
        // 若所有 offset trigger 都已過期（例如「6:35」被解為 AM 但已是下午），
        // 補一個「due time 本身」作 fallback；若 due time 也過了，用 now+2min
        val dueMillis = LocalDateTime.of(LocalDate.parse(dueDate), LocalTime.of(
            dueTime?.split(":")?.getOrNull(0)?.toIntOrNull() ?: 8,
            dueTime?.split(":")?.getOrNull(1)?.toIntOrNull() ?: 0
        )).atZone(zone).toInstant().toEpochMilli()
        val effectiveTriggers = if (triggerTimes.none { it > now }) {
            listOf(if (dueMillis > now) dueMillis else now + 2 * 60_000L)
        } else triggerTimes

        val pendingTimes = notificationDao.getAllPendingNotifications().map { it.triggerAt }.toMutableSet()
        effectiveTriggers.map { ReminderScheduler.clampToQuietHours(it, prefs, zone) }.filter { it > now }.forEach { millis ->
            var adjustedMillis = millis
            while (pendingTimes.contains(adjustedMillis)) adjustedMillis += 60_000L
            pendingTimes.add(adjustedMillis)
            val entity = ReminderNotificationEntity(reminderId = reminderId, triggerAt = adjustedMillis)
            val id = notificationDao.insertNotification(entity)
            ReminderScheduler.scheduleNotification(applicationContext, entity.copy(id = id))
        }
    }

    private fun resolveNotifProviderKey(prefs: AppPreferences): Pair<String, String>? {
        val preferred = prefs.preferredNotifProvider
        val candidates = listOf(
            "gemini"   to (prefs.geminiEnabled   to prefs.geminiApiKey),
            "mimo"     to (prefs.mimoEnabled     to prefs.mimoApiKey),
            "claude"   to (prefs.claudeEnabled   to prefs.claudeApiKey),
            "openai"   to (prefs.openaiEnabled   to prefs.openaiApiKey),
            "groq"     to (prefs.groqEnabled     to prefs.groqApiKey),
            "deepseek" to (prefs.deepseekEnabled to prefs.deepseekApiKey),
            "custom-anthropic" to (prefs.customAnthropicEnabled to
                AppPreferences.encodeCustomKey(
                    prefs.customAnthropicEndpoint,
                    prefs.customAnthropicModel,
                    prefs.customAnthropicKey
                ).takeIf {
                    prefs.customAnthropicEndpoint.isNotBlank() && prefs.customAnthropicModel.isNotBlank() && prefs.customAnthropicKey.isNotBlank()
                }.orEmpty()),
            "custom-openai" to (prefs.customOpenaiEnabled to
                AppPreferences.encodeCustomKey(
                    prefs.customOpenaiEndpoint,
                    prefs.customOpenaiModel,
                    prefs.customOpenaiKey
                ).takeIf {
                    prefs.customOpenaiEndpoint.isNotBlank() && prefs.customOpenaiModel.isNotBlank()
                }.orEmpty()),
        )
        val prefMatch = candidates.firstOrNull { it.first == preferred && it.second.first && it.second.second.isNotBlank() }
        if (prefMatch != null) return prefMatch.first to prefMatch.second.second
        return candidates.firstOrNull { it.second.first && it.second.second.isNotBlank() }
            ?.let { it.first to it.second.second }
    }

    companion object {
        private const val TAG = "ClassNoteNotifListener"
        private const val BATCH_DELAY_MS = 10_000L

        private val SENSITIVE_KEYWORDS = setOf(
            "驗證碼", "認證碼", "OTP", "一次性密碼",
            "verification code", "one-time password", "one time password",
            "身份證", "身分證", "國民身分證", "證號", "身份證字號", "身分證字號"
        )
    }
}

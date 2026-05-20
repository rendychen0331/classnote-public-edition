package com.rendy.classnote.feature

data class NotificationInput(
    val appLabel: String,
    val title: String,
    val text: String
)

data class EventInfo(
    val title: String,
    val dueDate: String?,
    val dueTime: String?,
    val category: String,
    val note: String
)

data class PeriodTimeBridge(val period: Int, val startMinute: Int)

interface AiFeature {
    suspend fun analyzeNotifications(
        provider: String,
        apiKey: String,
        inputs: List<NotificationInput>,
        periodTimes: List<PeriodTimeBridge>
    ): List<List<EventInfo>>

    suspend fun summarizeSession(provider: String, apiKey: String, content: String): String?
    suspend fun summarizeAudio(provider: String, apiKey: String, audioPath: String): String?
    suspend fun summarizePhoto(provider: String, apiKey: String, photoPath: String): String?

    suspend fun chatWithContext(
        provider: String,
        apiKey: String,
        context: String,
        history: List<Pair<String, Boolean>>,
        message: String
    ): String?

    suspend fun generateTitle(apiKey: String, content: String): String?
    suspend fun analyzeKeepNote(apiKey: String, title: String, body: String): KeepEventInfo?
}

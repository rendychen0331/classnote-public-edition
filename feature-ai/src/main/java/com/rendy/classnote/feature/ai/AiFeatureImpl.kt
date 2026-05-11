package com.rendy.classnote.feature.ai

import com.rendy.classnote.feature.AiFeature
import com.rendy.classnote.feature.EventInfo
import com.rendy.classnote.feature.KeepEventInfo
import com.rendy.classnote.feature.NotificationInput
import com.rendy.classnote.feature.PeriodTimeBridge

internal class AiFeatureImpl : AiFeature {

    override suspend fun analyzeNotifications(
        provider: String,
        apiKey: String,
        inputs: List<NotificationInput>,
        periodTimes: List<PeriodTimeBridge>
    ): List<List<EventInfo>> = when (provider) {
        "gemini"   -> GeminiApi.analyzeNotifications(apiKey, inputs, periodTimes)
        "claude"   -> ClaudeApi.analyzeNotifications(apiKey, inputs, periodTimes)
        "openai"   -> OpenAiApi.analyzeNotifications(apiKey, inputs, periodTimes)
        "groq"     -> GroqApi.analyzeNotifications(apiKey, inputs, periodTimes)
        "deepseek" -> DeepSeekApi.analyzeNotifications(apiKey, inputs, periodTimes)
        "mimo"     -> MimoApi.analyzeNotifications(apiKey, inputs, periodTimes)
        else       -> emptyList()
    }

    override suspend fun summarizeSession(apiKey: String, content: String): String? =
        GeminiApi.summarizeSession(apiKey, content)

    override suspend fun summarizeAudio(apiKey: String, audioPath: String): String? =
        GeminiApi.summarizeAudio(apiKey, audioPath)

    override suspend fun summarizePhoto(apiKey: String, photoPath: String): String? =
        GeminiApi.summarizePhoto(apiKey, photoPath)

    override suspend fun chatWithContext(
        provider: String,
        apiKey: String,
        context: String,
        history: List<Pair<String, Boolean>>,
        message: String
    ): String? = when (provider) {
        "gemini"   -> GeminiApi.chatWithContext(apiKey, context, history, message)
        "claude"   -> ClaudeApi.chatWithContext(apiKey, context, history, message)
        "openai"   -> OpenAiApi.chatWithContext(apiKey, context, history, message)
        "groq"     -> GroqApi.chatWithContext(apiKey, context, history, message)
        "deepseek" -> DeepSeekApi.chatWithContext(apiKey, context, history, message)
        "mimo"     -> MimoApi.chatWithContext(apiKey, context, history, message)
        else       -> null
    }

    override suspend fun generateTitle(apiKey: String, content: String): String? =
        GeminiApi.generateTitle(apiKey, content)

    override suspend fun analyzeKeepNote(apiKey: String, title: String, body: String): KeepEventInfo? {
        val event = GeminiApi.analyzeKeepNote(apiKey, title, body) ?: return null
        return KeepEventInfo(
            title = event.title,
            dueDate = event.dueDate,
            dueTime = event.dueTime,
            category = event.category,
            note = event.note
        )
    }
}

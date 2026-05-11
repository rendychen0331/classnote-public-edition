package com.rendy.classnote.feature.google

import android.util.Base64
import android.util.Log
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.gmail.Gmail
import com.google.api.services.gmail.model.MessagePart
import com.rendy.classnote.feature.ReminderInsert
import com.rendy.classnote.feature.SyncBridge
import com.rendy.classnote.feature.SyncOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

private const val GMAIL_READONLY = "https://www.googleapis.com/auth/gmail.readonly"
private const val TAG = "GmailSyncDelegate"

private val DUE_DATE_BLOCK = Regex("""【截止日期】\s*\n+(\d+)月(\d+)日(?:\s*(上午|下午)?\s*(\d{1,2})[：:](\d{2}))?""")
private val DUE_DATE_INLINE = Regex("""截止日期[：:]\s*(\d+)月(\d+)日(?:\s*(上午|下午)?\s*(\d{1,2})[：:](\d{2}))?""")
private val BODY_TITLE_BLOCK = Regex("""【作業標題】\s*\n+([^\n【]+)""")
private val BODY_TITLE_EN_BLOCK = Regex("""\[Assignment\s+[Tt]itle\]\s*\n+([^\n\[]+)""")
private val SUBJECT_TITLE_ZH = Regex("""新作業[：:][「「]([^」\n]+)[」」]""")
private val SUBJECT_TITLE_EN = Regex("""New assignment:\s+(.+)""", RegexOption.IGNORE_CASE)
private val COURSE_IN_BODY = Regex("""前往課堂\n<[^\n]+>\n([^\n]+)\n""")
private val COURSE_AT_TOP = Regex("""([^\n]+)\n<[^\n]+>\n(?:新作業|[Nn]ew\s+assignment)""")
private val NOISE_LINE = Regex(
    """<[^>]*>|https?://|前往課堂|Go to Classroom|取消訂閱|unsubscribe|Google LLC""" +
    """|通知設定|瞭解詳情|Learn more|張貼日期|張貼者|不想接收""" +
    """|您曾表示|如果您不想|您。如果|You're receiving|To unsubscribe""" +
    """|^新作業$|^New assignment$|^Posted$|^Due date$""",
    RegexOption.IGNORE_CASE
)

object GmailSyncDelegate {

    suspend fun sync(bridge: SyncBridge, email: String): SyncOutcome = withContext(Dispatchers.IO) {
        if (email.isBlank()) return@withContext SyncOutcome.NoPermission
        try {
            val gmail = buildService(bridge, email)
            val queries = mutableListOf(
                "from:no-reply@classroom.google.com (subject:新作業 OR subject:\"new assignment\")"
            )
            if (bridge.gmailClassroomForwardEnabled()) {
                queries += "(subject:新作業 OR subject:\"new assignment\") -from:no-reply@classroom.google.com"
            }
            var imported = 0; var skipped = 0
            for (query in queries) {
                val messages = gmail.users().messages().list("me")
                    .setQ(query).setMaxResults(50L).execute().messages ?: continue
                for (msgRef in messages) {
                    val externalId = "gmail:${msgRef.id}"
                    if (bridge.findByExternalId(externalId)) { skipped++; continue }
                    val msg = gmail.users().messages().get("me", msgRef.id).setFormat("full").execute()
                    val parsed = parseMessage(msg) ?: run { skipped++; continue }
                    bridge.insertReminderAndSchedule(ReminderInsert(
                        title = parsed.title,
                        note = parsed.bodyNote,
                        dueDate = parsed.dueDate,
                        dueTime = parsed.dueTime,
                        category = "HOMEWORK",
                        externalId = externalId,
                        syncSource = "gmail",
                        sourceName = parsed.courseName?.ifBlank { null } ?: "unknown"
                    ))
                    imported++
                }
            }
            bridge.logSync("Gmail", "sync", "匯入$imported 略過$skipped", true)
            if (imported > 0) bridge.refreshWidget()
            SyncOutcome.Success(imported, skipped)
        } catch (e: UserRecoverableAuthIOException) {
            SyncOutcome.AuthRequired
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed for $email", e)
            bridge.logSync("Gmail", "sync", e.message ?: "失敗", false)
            SyncOutcome.Error(e.message ?: "同步失敗")
        }
    }

    private fun buildService(bridge: SyncBridge, email: String): Gmail {
        val credential = GoogleAccountCredential.usingOAuth2(
            bridge.context, listOf(GMAIL_READONLY)
        ).apply { selectedAccountName = email }
        return Gmail.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), credential)
            .setApplicationName("ClassNote").build()
    }

    private fun parseMessage(msg: com.google.api.services.gmail.model.Message): ParsedAssignment? {
        val headers = msg.payload?.headers ?: return null
        val subject = headers.find { it.name.equals("Subject", ignoreCase = true) }?.value ?: return null
        val bodyText = getPlainTextBody(msg.payload)
            ?.replace("\r\n", "\n")?.replace("\r", "\n") ?: return null
        val title = BODY_TITLE_BLOCK.find(bodyText)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
            ?: BODY_TITLE_EN_BLOCK.find(bodyText)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
            ?: SUBJECT_TITLE_ZH.find(subject)?.groupValues?.get(1)
            ?: SUBJECT_TITLE_EN.find(subject)?.groupValues?.get(1)
            ?: return null
        val (dueDate, dueTime) = parseDueDateTime(bodyText)
        val courseName = COURSE_IN_BODY.find(bodyText)?.groupValues?.get(1)?.trim()
            ?: COURSE_AT_TOP.find(bodyText)?.groupValues?.get(1)?.trim()
        val bodyNote = extractBodyNote(bodyText, title.trim(), courseName)
        return ParsedAssignment(title.trim(), courseName, dueDate, dueTime, bodyNote)
    }

    private fun getPlainTextBody(payload: MessagePart?): String? {
        if (payload == null) return null
        if (payload.mimeType == "text/plain" && payload.body?.data != null)
            return String(Base64.decode(payload.body.data, Base64.URL_SAFE), Charsets.UTF_8)
        payload.parts?.forEach { part ->
            val result = getPlainTextBody(part)
            if (result != null) return result
        }
        return null
    }

    private fun parseDueDateTime(body: String): Pair<String?, String?> {
        val match = DUE_DATE_BLOCK.find(body) ?: DUE_DATE_INLINE.find(body) ?: return Pair(null, null)
        val month = match.groupValues[1].toIntOrNull() ?: return Pair(null, null)
        val day = match.groupValues[2].toIntOrNull() ?: return Pair(null, null)
        val now = LocalDate.now()
        val candidate = runCatching { LocalDate.of(now.year, month, day) }.getOrNull() ?: return Pair(null, null)
        val year = if (candidate.isBefore(now.minusDays(60))) now.year + 1 else now.year
        val dueDate = "%04d-%02d-%02d".format(year, month, day)
        val period = match.groupValues[3]
        val hourRaw = match.groupValues[4].toIntOrNull()
        val minute = match.groupValues[5].toIntOrNull()
        val dueTime = if (hourRaw != null && minute != null) {
            val hour = when {
                period == "下午" && hourRaw < 12 -> hourRaw + 12
                period == "上午" && hourRaw == 12 -> 0
                else -> hourRaw
            }
            "%02d:%02d".format(hour, minute)
        } else null
        return Pair(dueDate, dueTime)
    }

    private fun extractBodyNote(body: String, title: String, courseName: String?): String =
        body.lines().map { it.trim() }.filter { line ->
            line.isNotBlank()
                && !NOISE_LINE.containsMatchIn(line)
                && !line.contains(title, ignoreCase = true)
                && (courseName.isNullOrBlank() || !line.contains(courseName, ignoreCase = true))
                && !DUE_DATE_BLOCK.containsMatchIn(line)
                && !DUE_DATE_INLINE.containsMatchIn(line)
        }.joinToString("\n").trim()

    private data class ParsedAssignment(
        val title: String, val courseName: String?,
        val dueDate: String?, val dueTime: String?, val bodyNote: String
    )
}

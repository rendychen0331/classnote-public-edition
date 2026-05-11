package com.rendy.classnote.feature.google

import android.util.Log
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.classroom.Classroom
import com.rendy.classnote.feature.ReminderInsert
import com.rendy.classnote.feature.SyncBridge
import com.rendy.classnote.feature.SyncOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

private const val CLASSROOM_COURSES = "https://www.googleapis.com/auth/classroom.courses.readonly"
private const val CLASSROOM_COURSEWORK = "https://www.googleapis.com/auth/classroom.coursework.me.readonly"
private const val TAG = "ClassroomSyncDelegate"

object ClassroomSyncDelegate {

    suspend fun sync(bridge: SyncBridge, email: String): SyncOutcome = withContext(Dispatchers.IO) {
        if (email.isBlank()) return@withContext SyncOutcome.NoPermission
        try {
            val service = buildService(bridge, email)
            val courses = service.courses().list().setStudentId("me").setPageSize(50)
                .execute().courses
            if (courses.isNullOrEmpty()) return@withContext SyncOutcome.Success(0, 0)

            var imported = 0; var skipped = 0

            for (course in courses) {
                val courseId = course.id ?: continue
                val courseName = course.name ?: ""
                val courseWorks = runCatching {
                    service.courses().courseWork().list(courseId).setPageSize(50).execute().courseWork
                }.getOrNull() ?: continue

                for (cw in courseWorks) {
                    val cwId = cw.id ?: continue
                    val title = cw.title?.trim() ?: continue
                    val externalId = "classroom:$cwId"

                    if (bridge.findByExternalId(externalId)) { skipped++; continue }

                    val rawDate = cw.dueDate
                    val rawTime = cw.dueTime
                    val dueDate: String?
                    val dueTime: String?
                    if (rawDate != null && rawDate.year != null && rawDate.month != null && rawDate.day != null) {
                        val utcDt = LocalDateTime.of(rawDate.year, rawDate.month, rawDate.day,
                            rawTime?.hours ?: 0, rawTime?.minutes ?: 0)
                        val localDt = utcDt.atOffset(ZoneOffset.UTC)
                            .atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime()
                        dueDate = "%04d-%02d-%02d".format(localDt.year, localDt.monthValue, localDt.dayOfMonth)
                        dueTime = if (rawTime != null) "%02d:%02d".format(localDt.hour, localDt.minute) else null
                    } else {
                        dueDate = null; dueTime = null
                    }

                    // cross-source dedup
                    val duplicate = if (dueDate != null) bridge.findByTitleAndDueDate(title, dueDate)
                                    else bridge.findByTitleWithNullDueDate(title)
                    if (duplicate) { skipped++; continue }

                    val note = cw.description?.lines()?.map { it.trim() }
                        ?.filter { it.isNotBlank() && it != title }?.joinToString("\n")?.trim().orEmpty()

                    bridge.insertReminderAndSchedule(ReminderInsert(
                        title = title, note = note,
                        dueDate = dueDate, dueTime = dueTime,
                        category = "HOMEWORK",
                        externalId = externalId,
                        syncSource = "classroom",
                        sourceName = courseName.ifBlank { null }
                    ))
                    imported++
                }
            }

            bridge.logSync("Classroom", "sync", "匯入$imported 略過$skipped", true)
            if (imported > 0) bridge.refreshWidget()
            SyncOutcome.Success(imported, skipped)
        } catch (e: UserRecoverableAuthIOException) {
            SyncOutcome.AuthRequired
        } catch (e: Exception) {
            Log.e(TAG, "Classroom sync failed", e)
            bridge.logSync("Classroom", "sync", e.message ?: "失敗", false)
            SyncOutcome.Error(e.message ?: "同步失敗")
        }
    }

    private fun buildService(bridge: SyncBridge, email: String): Classroom {
        val credential = GoogleAccountCredential.usingOAuth2(
            bridge.context, listOf(CLASSROOM_COURSES, CLASSROOM_COURSEWORK)
        ).apply { selectedAccountName = email }
        return Classroom.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), credential)
            .setApplicationName("ClassNote").build()
    }
}

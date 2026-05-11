package com.rendy.classnote.feature

import android.content.Context

/**
 * Entry point for a dynamically-loaded feature .dex.
 * Implementations must have a no-arg constructor — FeatureManager creates them via reflection.
 */
interface FeatureModule {
    val id: String
    fun sync(): SyncFeature?
    fun backup(): BackupFeature?
    fun auth(): AuthFeature? = null
}

interface SyncFeature {
    suspend fun syncAll(bridge: SyncBridge): Map<String, SyncOutcome>
    suspend fun sync(service: String, bridge: SyncBridge): SyncOutcome =
        syncAll(bridge)[service] ?: SyncOutcome.Error("service $service not handled")
}

interface BackupFeature {
    suspend fun backup(bridge: SyncBridge): BackupOutcome
    suspend fun restore(bridge: SyncBridge): BackupOutcome
}

interface AuthFeature {
    /** Returns true if successfully signed in. */
    suspend fun signIn(context: Context): Boolean
    fun signOut(context: Context)
    fun getAccountEmail(context: Context): String?
}

/** All operations a feature module may request from the base APK at runtime. */
interface SyncBridge {
    val context: Context

    // ── Auth ───────────────────────────────────────────────────────────────
    /** Returns account emails for a service key: gmail, classroom, calendar, tasks, keep, drive. */
    fun googleAccountEmails(service: String): Set<String>

    // ── Database ──────────────────────────────────────────────────────────
    suspend fun findByExternalId(externalId: String): Boolean
    suspend fun findByTitleAndDueDate(title: String, dueDate: String): Boolean
    suspend fun findByTitleWithNullDueDate(title: String): Boolean

    /**
     * Insert a reminder and schedule its default notifications.
     * Returns the new row ID.
     */
    suspend fun insertReminderAndSchedule(data: ReminderInsert): Long

    // ── Side-effects ──────────────────────────────────────────────────────
    fun refreshWidget()
    fun logSync(manager: String, method: String, result: String, success: Boolean)

    // ── AI helpers ────────────────────────────────────────────────────────
    /** Analyze a Keep note for event/reminder extraction. Returns null if no event detected or no key set. */
    suspend fun analyzeKeepNote(title: String, text: String): KeepEventInfo?

    // ── Preferences ───────────────────────────────────────────────────────
    fun gmailClassroomForwardEnabled(): Boolean
    fun backupNetworkType(): Int
    fun googleSignedInAccountEmail(): String?
}

data class KeepEventInfo(
    val title: String?,
    val dueDate: String?,
    val dueTime: String?,
    val category: String?,
    val note: String?
)

data class ReminderInsert(
    val title: String,
    val note: String = "",
    val dueDate: String? = null,
    val dueTime: String? = null,
    val startDate: String? = null,
    val category: String = "",
    val externalId: String? = null,
    val syncSource: String = "",
    val sourceName: String = ""
)

sealed class SyncOutcome {
    data class Success(val imported: Int, val skipped: Int) : SyncOutcome()
    data class Error(val message: String) : SyncOutcome()
    object NoPermission : SyncOutcome()
    object AuthRequired : SyncOutcome()
}

sealed class BackupOutcome {
    object Success : BackupOutcome()
    data class Error(val message: String) : BackupOutcome()
    data class AuthRequired(val intent: android.content.Intent?) : BackupOutcome()
}

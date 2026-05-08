package com.rendy.classnote.data

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.api.services.calendar.CalendarScopes
import com.google.api.services.classroom.ClassroomScopes
import com.google.api.services.drive.DriveScopes
import com.google.api.services.gmail.GmailScopes
import com.google.api.services.tasks.TasksScopes

object GoogleAuthManager {

    private const val PREFS_NAME = "classnote_prefs"
    private const val KEY_CLASSROOM_ACCOUNT_EMAIL = "classroom_account_email"
    private const val KEY_CALENDAR_ACCOUNT_EMAIL = "calendar_account_email"
    private const val KEY_TASKS_ACCOUNT_EMAIL = "tasks_account_email"

    private fun buildOptions(includeGmail: Boolean = false): GoogleSignInOptions {
        val builder = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_APPDATA))
        if (includeGmail) builder.requestScopes(Scope(GmailScopes.GMAIL_READONLY))
        return builder.build()
    }

    fun getSignInIntent(context: Context): Intent =
        GoogleSignIn.getClient(context, buildOptions()).signInIntent

    /** 取得包含 Gmail 讀取權限的登入 Intent（用於 Gmail 同步授權）。 */
    fun getSignInIntentWithGmail(context: Context): Intent =
        GoogleSignIn.getClient(context, buildOptions(includeGmail = true)).signInIntent

    /** 取得包含 DRIVE_FILE scope 的登入 Intent（用於匯出到可見 Drive 資料夾）。 */
    fun getSignInIntentForExport(context: Context): Intent {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_APPDATA))
            .requestScopes(Scope(DriveScopes.DRIVE_FILE))
            .build()
        return GoogleSignIn.getClient(context, options).signInIntent
    }

    /** 檢查帳號是否已有 DRIVE_FILE scope 授權。 */
    fun hasDriveFileScope(context: Context): Boolean {
        val account = getAccount(context) ?: return false
        return GoogleSignIn.hasPermissions(account, Scope(DriveScopes.DRIVE_FILE))
    }

    /** 取得 Google 日曆專屬登入 Intent（獨立帳號，含 CALENDAR_READONLY scope）。 */
    fun getSignInIntentForCalendar(context: Context): Intent {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(CalendarScopes.CALENDAR_READONLY))
            .build()
        return GoogleSignIn.getClient(context, options).signInIntent
    }

    /** 取得 Google Tasks 專屬登入 Intent（獨立帳號，含 TASKS_READONLY scope）。 */
    fun getSignInIntentForTasks(context: Context): Intent {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(TasksScopes.TASKS_READONLY))
            .build()
        return GoogleSignIn.getClient(context, options).signInIntent
    }

    // ── Calendar 帳號獨立儲存 ───────────────────────────────────────────────

    fun getCalendarAccountEmail(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_CALENDAR_ACCOUNT_EMAIL, null)

    fun setCalendarAccountEmail(context: Context, email: String?) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_CALENDAR_ACCOUNT_EMAIL, email).apply()

    fun clearCalendarAccount(context: Context) = setCalendarAccountEmail(context, null)

    fun signOutCalendar(context: Context, onDone: () -> Unit = {}) {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(CalendarScopes.CALENDAR_READONLY))
            .build()
        GoogleSignIn.getClient(context.applicationContext, options)
            .signOut()
            .addOnCompleteListener { onDone() }
    }

    // ── Tasks 帳號獨立儲存 ──────────────────────────────────────────────────

    fun getTasksAccountEmail(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_TASKS_ACCOUNT_EMAIL, null)

    fun setTasksAccountEmail(context: Context, email: String?) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_TASKS_ACCOUNT_EMAIL, email).apply()

    fun clearTasksAccount(context: Context) = setTasksAccountEmail(context, null)

    fun signOutTasks(context: Context, onDone: () -> Unit = {}) {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(TasksScopes.TASKS_READONLY))
            .build()
        GoogleSignIn.getClient(context.applicationContext, options)
            .signOut()
            .addOnCompleteListener { onDone() }
    }

    /** 取得 Classroom 專屬登入 Intent（獨立帳號，含 Classroom scopes）。 */
    fun getSignInIntentForClassroom(context: Context): Intent {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(
                Scope(ClassroomScopes.CLASSROOM_COURSES_READONLY),
                Scope(ClassroomScopes.CLASSROOM_COURSEWORK_ME_READONLY)
            )
            .build()
        return GoogleSignIn.getClient(context, options).signInIntent
    }

    // ── Classroom 帳號獨立儲存 ──────────────────────────────────────────────

    fun getClassroomAccountEmail(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_CLASSROOM_ACCOUNT_EMAIL, null)

    fun setClassroomAccountEmail(context: Context, email: String?) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_CLASSROOM_ACCOUNT_EMAIL, email).apply()

    fun clearClassroomAccount(context: Context) = setClassroomAccountEmail(context, null)

    /**
     * 登出 Classroom 專屬的 GoogleSignIn client，讓下次授權強制顯示帳號選擇器。
     * 不影響 Drive/Gmail 的登入狀態。
     */
    fun signOutClassroom(context: Context, onDone: () -> Unit = {}) {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(
                Scope(ClassroomScopes.CLASSROOM_COURSES_READONLY),
                Scope(ClassroomScopes.CLASSROOM_COURSEWORK_ME_READONLY)
            )
            .build()
        GoogleSignIn.getClient(context.applicationContext, options)
            .signOut()
            .addOnCompleteListener { onDone() }
    }

    // ── Drive / Gmail 帳號 ──────────────────────────────────────────────────

    fun handleSignInResult(data: Intent?): GoogleSignInAccount? {
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        return try {
            task.getResult(ApiException::class.java)
        } catch (e: ApiException) {
            Log.e("GoogleAuth", "Sign-in failed, statusCode=${e.statusCode}, message=${e.message}")
            null
        }
    }

    fun getAccount(context: Context): GoogleSignInAccount? =
        GoogleSignIn.getLastSignedInAccount(context)

    fun signOut(context: Context, onDone: () -> Unit = {}) {
        val client = GoogleSignIn.getClient(context, buildOptions())
        // revokeAccess 撤銷 Google 帳號層級的授權，讓下次登入重新跳 consent 畫面
        client.revokeAccess().addOnCompleteListener {
            client.signOut().addOnCompleteListener { onDone() }
        }
    }

    fun signOutGmail(context: Context, onDone: () -> Unit = {}) {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(GmailScopes.GMAIL_READONLY))
            .build()
        GoogleSignIn.getClient(context.applicationContext, options)
            .signOut()
            .addOnCompleteListener { onDone() }
    }

    // ── Gmail 多帳號 ──────────────────────────────────────────────────────

    private const val KEY_GMAIL_ACCOUNT_EMAILS = "gmail_account_emails"

    fun getGmailAccountEmails(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stored = prefs.getStringSet(KEY_GMAIL_ACCOUNT_EMAILS, null)
        if (stored != null) return stored
        val single = getAccount(context)?.email
        return if (single != null) setOf(single) else emptySet()
    }

    fun addGmailAccountEmail(context: Context, email: String) {
        val current = getGmailAccountEmails(context).toMutableSet()
        current.add(email)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putStringSet(KEY_GMAIL_ACCOUNT_EMAILS, current).apply()
    }

    fun removeGmailAccountEmail(context: Context, email: String) {
        val current = getGmailAccountEmails(context).toMutableSet()
        current.remove(email)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putStringSet(KEY_GMAIL_ACCOUNT_EMAILS, current).apply()
    }

    // ── Classroom 多帳號 ─────────────────────────────────────────────────

    private const val KEY_CLASSROOM_ACCOUNT_EMAILS = "classroom_account_emails"

    fun getClassroomAccountEmails(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stored = prefs.getStringSet(KEY_CLASSROOM_ACCOUNT_EMAILS, null)
        if (stored != null) return stored
        val single = getClassroomAccountEmail(context)
        return if (single != null) setOf(single) else emptySet()
    }

    fun addClassroomAccountEmail(context: Context, email: String) {
        val current = getClassroomAccountEmails(context).toMutableSet()
        current.add(email)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putStringSet(KEY_CLASSROOM_ACCOUNT_EMAILS, current).apply()
    }

    fun removeClassroomAccountEmail(context: Context, email: String) {
        val current = getClassroomAccountEmails(context).toMutableSet()
        current.remove(email)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putStringSet(KEY_CLASSROOM_ACCOUNT_EMAILS, current).apply()
    }

    // ── Calendar 多帳號 ──────────────────────────────────────────────────

    private const val KEY_CALENDAR_ACCOUNT_EMAILS = "calendar_account_emails"

    fun getCalendarAccountEmails(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stored = prefs.getStringSet(KEY_CALENDAR_ACCOUNT_EMAILS, null)
        if (stored != null) return stored
        val single = getCalendarAccountEmail(context)
        return if (single != null) setOf(single) else emptySet()
    }

    fun addCalendarAccountEmail(context: Context, email: String) {
        val current = getCalendarAccountEmails(context).toMutableSet()
        current.add(email)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putStringSet(KEY_CALENDAR_ACCOUNT_EMAILS, current).apply()
    }

    fun removeCalendarAccountEmail(context: Context, email: String) {
        val current = getCalendarAccountEmails(context).toMutableSet()
        current.remove(email)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putStringSet(KEY_CALENDAR_ACCOUNT_EMAILS, current).apply()
    }

    // ── Tasks 多帳號 ─────────────────────────────────────────────────────

    private const val KEY_TASKS_ACCOUNT_EMAILS = "tasks_account_emails"

    fun getTasksAccountEmails(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stored = prefs.getStringSet(KEY_TASKS_ACCOUNT_EMAILS, null)
        if (stored != null) return stored
        val single = getTasksAccountEmail(context)
        return if (single != null) setOf(single) else emptySet()
    }

    fun addTasksAccountEmail(context: Context, email: String) {
        val current = getTasksAccountEmails(context).toMutableSet()
        current.add(email)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putStringSet(KEY_TASKS_ACCOUNT_EMAILS, current).apply()
    }

    fun removeTasksAccountEmail(context: Context, email: String) {
        val current = getTasksAccountEmails(context).toMutableSet()
        current.remove(email)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putStringSet(KEY_TASKS_ACCOUNT_EMAILS, current).apply()
    }

    // ── Keep 登入 ──────────────────────────────────────────────────────────

    private const val KEEP_READONLY_SCOPE = "https://www.googleapis.com/auth/keep.readonly"

    fun getSignInIntentForKeep(context: Context): Intent {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(KEEP_READONLY_SCOPE))
            .build()
        return GoogleSignIn.getClient(context, options).signInIntent
    }

    fun signOutKeep(context: Context, onDone: () -> Unit = {}) {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(KEEP_READONLY_SCOPE))
            .build()
        GoogleSignIn.getClient(context.applicationContext, options)
            .signOut()
            .addOnCompleteListener { onDone() }
    }

    // ── Keep 多帳號 ──────────────────────────────────────────────────────

    private const val KEY_KEEP_ACCOUNT_EMAILS = "keep_account_emails"

    fun getKeepAccountEmails(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_KEEP_ACCOUNT_EMAILS, emptySet()) ?: emptySet()
    }

    fun addKeepAccountEmail(context: Context, email: String) {
        val current = getKeepAccountEmails(context).toMutableSet()
        current.add(email)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putStringSet(KEY_KEEP_ACCOUNT_EMAILS, current).apply()
    }

    fun removeKeepAccountEmail(context: Context, email: String) {
        val current = getKeepAccountEmails(context).toMutableSet()
        current.remove(email)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putStringSet(KEY_KEEP_ACCOUNT_EMAILS, current).apply()
    }
}

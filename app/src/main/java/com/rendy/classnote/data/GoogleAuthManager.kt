package com.rendy.classnote.data

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.api.services.classroom.ClassroomScopes
import com.google.api.services.drive.DriveScopes
import com.google.api.services.gmail.GmailScopes

object GoogleAuthManager {

    private const val PREFS_NAME = "classnote_prefs"
    private const val KEY_CLASSROOM_ACCOUNT_EMAIL = "classroom_account_email"

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
        } catch (_: ApiException) {
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
}

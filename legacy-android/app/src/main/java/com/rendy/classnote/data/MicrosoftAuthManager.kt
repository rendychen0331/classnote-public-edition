package com.rendy.classnote.data

import android.content.Context

private const val PREFS_NAME = "classnote_prefs"
private const val KEY_MS_EMAIL = "ms_account_email"

object MicrosoftAuthManager {

    fun isSignedIn(context: Context): Boolean = getAccountEmail(context) != null

    fun getAccountEmail(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_MS_EMAIL, null)

    /** Attempt silent token acquisition via feature module; returns true on success. */
    suspend fun signIn(context: Context): Boolean =
        FeatureManager.getAuth(context, "microsoft")?.signIn(context) ?: false

    fun signOut(context: Context) {
        FeatureManager.getAuth(context, "microsoft")?.signOut(context)
            ?: context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().remove(KEY_MS_EMAIL).apply()
    }
}

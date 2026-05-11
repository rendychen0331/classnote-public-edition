package com.rendy.classnote.feature.microsoft

import android.content.Context
import com.rendy.classnote.feature.AuthFeature

private const val PREFS_NAME = "classnote_prefs"
private const val KEY_MS_EMAIL = "ms_account_email"

class MicrosoftAuthFeature : AuthFeature {

    override suspend fun signIn(context: Context): Boolean {
        val token = MsAuthHelper.acquireTokenSilent(context)
        return token != null
    }

    override fun signOut(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().remove(KEY_MS_EMAIL).apply()
    }

    override fun getAccountEmail(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_MS_EMAIL, null)
}

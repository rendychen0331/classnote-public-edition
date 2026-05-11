package com.rendy.classnote.feature.microsoft

import android.content.Context
import android.util.Log
import com.microsoft.identity.client.AcquireTokenSilentParameters
import com.microsoft.identity.client.IAccount
import com.microsoft.identity.client.ISingleAccountPublicClientApplication
import com.microsoft.identity.client.IPublicClientApplication
import com.microsoft.identity.client.PublicClientApplication
import com.microsoft.identity.client.exception.MsalException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

private const val TAG = "MsAuthHelper"
private const val PREFS_NAME = "classnote_prefs"
private const val KEY_MS_EMAIL = "ms_account_email"

internal val MS_SCOPES = listOf(
    "Files.ReadWrite.AppFolder", "Files.ReadWrite",
    "Tasks.Read", "Calendars.Read", "Notes.Read", "User.Read"
)
internal val TEAMS_SCOPES = listOf("EduAssignments.ReadBasic", "User.Read")

internal object MsAuthHelper {

    @Volatile
    private var msalApp: ISingleAccountPublicClientApplication? = null

    private suspend fun getApp(context: Context): ISingleAccountPublicClientApplication? {
        msalApp?.let { return it }
        val configResId = context.resources.getIdentifier(
            "msal_config", "raw", context.packageName
        )
        if (configResId == 0) {
            Log.e(TAG, "msal_config resource not found")
            return null
        }
        return suspendCoroutine { cont ->
            PublicClientApplication.createSingleAccountPublicClientApplication(
                context.applicationContext,
                configResId,
                object : IPublicClientApplication.ISingleAccountApplicationCreatedListener {
                    override fun onCreated(app: ISingleAccountPublicClientApplication) {
                        msalApp = app; cont.resume(app)
                    }
                    override fun onError(e: MsalException) {
                        Log.e(TAG, "MSAL init error", e)
                        cont.resume(null)
                    }
                }
            )
        }
    }

    private suspend fun getCurrentAccount(context: Context): IAccount? {
        val app = getApp(context) ?: return null
        return withContext(Dispatchers.Default) {
            try { app.getCurrentAccount().currentAccount }
            catch (e: Exception) { Log.e(TAG, "get account error", e); null }
        }
    }

    suspend fun acquireTokenSilent(context: Context): String? =
        acquireTokenSilentWithScopes(context, MS_SCOPES)

    suspend fun acquireTokenSilentForTeams(context: Context): String? =
        acquireTokenSilentWithScopes(context, TEAMS_SCOPES)

    private suspend fun acquireTokenSilentWithScopes(context: Context, scopes: List<String>): String? {
        val app = getApp(context) ?: return null
        val account = getCurrentAccount(context) ?: return null
        return try {
            withContext(Dispatchers.IO) {
                val params = AcquireTokenSilentParameters.Builder()
                    .withScopes(scopes)
                    .fromAuthority(account.authority)
                    .forAccount(account)
                    .build()
                app.acquireTokenSilent(params).accessToken
            }
        } catch (e: Exception) {
            Log.e(TAG, "silent token error (scopes=$scopes)", e)
            null
        }
    }

    fun getSavedEmail(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_MS_EMAIL, null)
}

package com.rendy.classnote.data

import android.app.Activity
import android.content.Context
import android.util.Log
import com.microsoft.identity.client.AcquireTokenParameters
import com.microsoft.identity.client.AcquireTokenSilentParameters
import com.microsoft.identity.client.AuthenticationCallback
import com.microsoft.identity.client.IAccount
import com.microsoft.identity.client.IAuthenticationResult
import com.microsoft.identity.client.IPublicClientApplication
import com.microsoft.identity.client.ISingleAccountPublicClientApplication
import com.microsoft.identity.client.PublicClientApplication
import com.microsoft.identity.client.exception.MsalException
import com.rendy.classnote.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

object OneDriveAuthManager {

    private const val TAG = "OneDriveAuthManager"
    val SCOPES = listOf("Files.ReadWrite.AppFolder", "User.Read")

    @Volatile
    private var msalApp: ISingleAccountPublicClientApplication? = null

    suspend fun getApp(context: Context): ISingleAccountPublicClientApplication? {
        msalApp?.let { return it }
        return suspendCoroutine { cont ->
            PublicClientApplication.createSingleAccountPublicClientApplication(
                context.applicationContext,
                R.raw.msal_config,
                object : IPublicClientApplication.ISingleAccountApplicationCreatedListener {
                    override fun onCreated(app: ISingleAccountPublicClientApplication) {
                        msalApp = app
                        cont.resume(app)
                    }
                    override fun onError(e: MsalException) {
                        Log.e(TAG, "MSAL init error", e)
                        cont.resume(null)
                    }
                }
            )
        }
    }

    suspend fun getCurrentAccount(context: Context): IAccount? {
        val app = getApp(context) ?: return null
        return withContext(Dispatchers.Default) {
            try {
                app.getCurrentAccount().currentAccount
            } catch (e: Exception) {
                Log.e(TAG, "get current account error", e)
                null
            }
        }
    }

    suspend fun getAccountEmail(context: Context): String? = getCurrentAccount(context)?.username

    suspend fun signIn(activity: Activity): String? {
        val app = getApp(activity) ?: return null
        return suspendCoroutine { cont ->
            val params = AcquireTokenParameters.Builder()
                .startAuthorizationFromActivity(activity)
                .withScopes(SCOPES)
                .withCallback(object : AuthenticationCallback {
                    override fun onSuccess(result: IAuthenticationResult) {
                        cont.resume(result.accessToken)
                    }
                    override fun onError(e: MsalException) {
                        Log.e(TAG, "sign-in error", e)
                        cont.resume(null)
                    }
                    override fun onCancel() { cont.resume(null) }
                })
                .build()
            app.acquireToken(params)
        }
    }

    suspend fun acquireTokenSilent(context: Context): String? {
        val app = getApp(context) ?: return null
        val account = getCurrentAccount(context) ?: return null
        return try {
            withContext(Dispatchers.IO) {
                val params = AcquireTokenSilentParameters.Builder()
                    .withScopes(SCOPES)
                    .fromAuthority(account.authority)
                    .forAccount(account)
                    .build()
                app.acquireTokenSilent(params).accessToken
            }
        } catch (e: Exception) {
            Log.e(TAG, "silent token error", e)
            null
        }
    }

    suspend fun signOut(context: Context) {
        val app = getApp(context) ?: return
        suspendCoroutine<Unit> { cont ->
            app.signOut(object : ISingleAccountPublicClientApplication.SignOutCallback {
                override fun onSignOut() { cont.resume(Unit) }
                override fun onError(e: MsalException) { cont.resume(Unit) }
            })
        }
    }
}

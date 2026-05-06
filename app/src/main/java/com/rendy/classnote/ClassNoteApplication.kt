package com.rendy.classnote

import android.annotation.SuppressLint
import android.app.Application
import android.graphics.Color
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.rendy.classnote.data.AppPreferences
import com.rendy.classnote.data.WeatherPreferences
import com.rendy.classnote.data.local.ClassNoteDatabase
import com.rendy.classnote.data.remote.ApiLogger
import com.rendy.classnote.data.repository.ClassRecordRepository
import com.rendy.classnote.data.repository.CourseRepository
import com.rendy.classnote.data.repository.FormulaRepository
import com.rendy.classnote.data.repository.ReminderRepository
import com.rendy.classnote.notification.NotificationHelper

class ClassNoteApplication : Application() {

    val database by lazy { ClassNoteDatabase.getDatabase(this) }

    val courseRepository by lazy {
        CourseRepository(
            courseDao = database.courseDao(),
            overrideDao = database.courseOverrideDao(),
            periodTimeDao = database.periodTimeDao()
        )
    }

    val reminderRepository by lazy {
        ReminderRepository(
            reminderDao = database.reminderDao(),
            notificationDao = database.reminderNotificationDao()
        )
    }

    val formulaRepository by lazy { FormulaRepository(database.formulaDao()) }

    val classRecordRepository by lazy {
        ClassRecordRepository(
            recordDao = database.classRecordDao(),
            mediaDao = database.classRecordMediaDao(),
            sessionSummaryDao = database.classSessionSummaryDao()
        )
    }

    val appPreferences by lazy { AppPreferences(this) }
    val weatherPreferences by lazy { WeatherPreferences(this) }

    private var _formulaWebView: WebView? = null
    var formulaWebViewReady = false
        private set

    @SuppressLint("SetJavaScriptEnabled")
    fun warmFormulaEditor() {
        if (_formulaWebView != null) return
        _formulaWebView = WebView(this).also { wv ->
            wv.setBackgroundColor(Color.TRANSPARENT)
            wv.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                cacheMode = WebSettings.LOAD_DEFAULT
            }
            wv.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    formulaWebViewReady = true
                }
            }
            wv.loadUrl("file:///android_asset/mathquill/editor.html")
        }
    }

    val formulaEditorWebView: WebView
        get() = _formulaWebView ?: WebView(this).also { _formulaWebView = it }

    override fun onCreate() {
        super.onCreate()
        ApiLogger.init(database.apiLogDao())
        val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        val bypassDnd = appPreferences.bypassDndEnabled && nm.isNotificationPolicyAccessGranted
        NotificationHelper.createNotificationChannel(this, bypassDnd)
    }
}

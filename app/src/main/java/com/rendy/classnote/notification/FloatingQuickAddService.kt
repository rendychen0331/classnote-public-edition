package com.rendy.classnote.notification

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.PopupMenu
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.rendy.classnote.R
import com.rendy.classnote.data.AppPreferences
import com.rendy.classnote.ui.MainActivity

class FloatingQuickAddService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var themedCtx: ContextThemeWrapper

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        themedCtx = ContextThemeWrapper(this, R.style.Theme_ClassNote)
        startForegroundWithNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> if (overlayView == null) showOverlay()
            ACTION_DISMISS -> dismiss()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        removeOverlay()
    }

    @SuppressLint("InflateParams")
    private fun showOverlay() {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        val view = LayoutInflater.from(themedCtx).inflate(R.layout.overlay_quick_add, null)
        overlayView = view

        val leftWrap = view.findViewById<FrameLayout>(R.id.btn_reminder_wrap)
        val capsule = view.findViewById<View>(R.id.capsule)
        val rightWrap = view.findViewById<FrameLayout>(R.id.btn_classrecord_wrap)
        val btnModel = view.findViewById<ImageButton>(R.id.btn_model)

        view.findViewById<View>(R.id.overlay_root).setOnClickListener { dismiss() }
        leftWrap.setOnClickListener { openActivity("new_reminder") }
        capsule.setOnClickListener { openActivity("new_reminder") }
        view.findViewById<View>(R.id.hint_text).setOnClickListener { openActivity("new_reminder") }
        rightWrap.setOnClickListener { openActivity("new_classrecord") }
        view.findViewById<ImageButton>(R.id.btn_add_attachment).setOnClickListener {
            showAttachmentMenu(it)
        }
        btnModel.apply {
            updateModelIcon(this)
            setOnClickListener { showModelMenu(this) }
        }

        listOf(leftWrap, capsule, rightWrap).forEach {
            it.alpha = 0f
            it.translationY = dpToPx(100f)
        }

        windowManager?.addView(view, params)

        val interpolator = PathInterpolator(0.2f, 0f, 0f, 1f)
        val baseDelay = 350L
        listOf(leftWrap to 0L, capsule to 75L, rightWrap to 150L).forEach { (v, delay) ->
            handler.postDelayed({
                v.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(450)
                    .setInterpolator(interpolator)
                    .start()
            }, baseDelay + delay)
        }
    }

    private fun dismiss() {
        removeOverlay()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun removeOverlay() {
        overlayView?.let {
            try { windowManager?.removeView(it) } catch (_: Exception) {}
        }
        overlayView = null
    }

    private fun openActivity(navigateTo: String) {
        dismiss()
        startActivity(Intent(this, MainActivity::class.java).apply {
            putExtra("navigate_to", navigateTo)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        })
    }

    private fun showAttachmentMenu(anchor: View) {
        val popup = PopupMenu(themedCtx, anchor)
        popup.menu.add(0, 0, 0, getString(R.string.attach_camera))
        popup.menu.add(0, 1, 1, getString(R.string.attach_gallery))
        popup.menu.add(0, 2, 2, getString(R.string.attach_audio))
        popup.setOnMenuItemClickListener { item ->
            val type = when (item.itemId) {
                0 -> "new_classrecord_camera"
                1 -> "new_classrecord_gallery"
                2 -> "new_classrecord_audio"
                else -> return@setOnMenuItemClickListener false
            }
            openActivity(type)
            true
        }
        popup.show()
    }

    private fun showModelMenu(anchor: ImageButton) {
        val prefs = AppPreferences(this)
        val providers = buildList {
            if (prefs.geminiEnabled && prefs.geminiApiKey.isNotBlank()) add(Triple("gemini", "Gemini", R.drawable.ic_ai_gemini))
            if (prefs.mimoEnabled && prefs.mimoApiKey.isNotBlank()) add(Triple("mimo", "Mimo", R.drawable.ic_ai_mimo))
            if (prefs.claudeEnabled && prefs.claudeApiKey.isNotBlank()) add(Triple("claude", "Claude", R.drawable.ic_ai_claude))
            if (prefs.openaiEnabled && prefs.openaiApiKey.isNotBlank()) add(Triple("openai", "GPT", R.drawable.ic_ai_openai))
            if (prefs.groqEnabled && prefs.groqApiKey.isNotBlank()) add(Triple("groq", "Groq", R.drawable.ic_ai_groq))
            if (prefs.deepseekEnabled && prefs.deepseekApiKey.isNotBlank()) add(Triple("deepseek", "DS", R.drawable.ic_ai_deepseek))
        }
        if (providers.isEmpty()) return

        val popup = PopupMenu(themedCtx, anchor, android.view.Gravity.TOP)
        providers.forEachIndexed { i, (_, name, iconRes) ->
            popup.menu.add(0, i, i, name).icon = ContextCompat.getDrawable(themedCtx, iconRes)
        }
        try {
            val f = popup::class.java.getDeclaredField("mPopup")
            f.isAccessible = true
            val helper = f.get(popup)
            val m = helper::class.java.getDeclaredMethod("setForceShowIcon", Boolean::class.java)
            m.isAccessible = true
            m.invoke(helper, true)
        } catch (_: Exception) {}
        popup.setOnMenuItemClickListener { item ->
            val (id) = providers[item.itemId]
            prefs.preferredChatProvider = id
            prefs.preferredNotifProvider = id
            updateModelIcon(anchor)
            true
        }
        popup.show()
    }

    private fun updateModelIcon(button: ImageButton) {
        val iconRes = when (AppPreferences(this).preferredChatProvider) {
            "claude" -> R.drawable.ic_ai_claude
            "openai" -> R.drawable.ic_ai_openai
            "groq" -> R.drawable.ic_ai_groq
            "deepseek" -> R.drawable.ic_ai_deepseek
            "mimo" -> R.drawable.ic_ai_mimo
            else -> R.drawable.ic_ai_gemini
        }
        button.setImageResource(iconRes)
    }

    private fun startForegroundWithNotification() {
        val channelId = "quick_add_overlay"
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(channelId) == null) {
            val channel = NotificationChannel(channelId, "助手模式", NotificationManager.IMPORTANCE_MIN)
            channel.setShowBadge(false)
            nm.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.tile_quick_add))
            .setSmallIcon(R.drawable.ic_quick_add_tile)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()
        startForeground(NOTIF_ID, notification)
    }

    private fun dpToPx(dp: Float) = dp * resources.displayMetrics.density

    companion object {
        const val ACTION_SHOW = "com.rendy.classnote.action.SHOW_OVERLAY"
        const val ACTION_DISMISS = "com.rendy.classnote.action.DISMISS_OVERLAY"
        private const val NOTIF_ID = 9001
    }
}

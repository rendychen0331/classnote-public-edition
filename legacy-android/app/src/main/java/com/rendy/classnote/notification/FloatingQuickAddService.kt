package com.rendy.classnote.notification

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.PathInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.core.app.NotificationCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.ChipGroup
import com.rendy.classnote.ClassNoteApplication
import com.rendy.classnote.data.FeatureManager
import com.rendy.classnote.R
import com.rendy.classnote.data.AppPreferences
import com.rendy.classnote.data.local.entity.ClassRecordEntity
import com.rendy.classnote.data.local.entity.ReminderEntity
import com.rendy.classnote.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class FloatingQuickAddService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var themedCtx: ContextThemeWrapper
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var reminderSelectedDate: LocalDate = LocalDate.now()
    private val chatMessages = mutableListOf<OverlayChatAdapter.ChatMessage>()
    private var chatAdapter: OverlayChatAdapter? = null
    private var isChatFullScreen = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        themedCtx = ContextThemeWrapper(this, R.style.Theme_ClassNote)
        startForegroundWithNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> {
                if (!FeatureManager.isDownloaded(this, "assistant")) {
                    android.widget.Toast.makeText(this, "請先至功能模組管理下載助手模組", android.widget.Toast.LENGTH_SHORT).show()
                    dismiss()
                } else if (overlayView == null) {
                    showOverlay()
                }
            }
            ACTION_DISMISS -> dismiss()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        removeOverlay()
        serviceScope.cancel()
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
        val aiAvailable = FeatureManager.isDownloaded(this, "ai")
        if (!aiAvailable) {
            capsule.visibility = View.GONE
            view.findViewById<View>(R.id.hint_text).visibility = View.GONE
            btnModel.visibility = View.GONE
            view.findViewById<View>(R.id.btn_add_attachment).visibility = View.GONE
        }

        view.findViewById<View>(R.id.overlay_root).setOnClickListener {
            val modelPicker = view.findViewById<View>(R.id.model_picker_panel)
            val attachPicker = view.findViewById<View>(R.id.attachment_picker_panel)
            when {
                modelPicker.visibility == View.VISIBLE -> modelPicker.visibility = View.GONE
                attachPicker.visibility == View.VISIBLE -> attachPicker.visibility = View.GONE
                else -> dismiss()
            }
        }
        leftWrap.setOnClickListener { showReminderForm() }
        capsule.setOnClickListener { showChatPanel() }
        view.findViewById<View>(R.id.hint_text).setOnClickListener { showChatPanel() }
        rightWrap.setOnClickListener { showClassRecordForm() }
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
        val view = overlayView ?: return
        val panel = view.findViewById<LinearLayout>(R.id.attachment_picker_panel)
        if (panel.visibility == View.VISIBLE) { panel.visibility = View.GONE; return }

        val items = listOf(
            Pair("camera", getString(R.string.attach_camera)),
            Pair("gallery", getString(R.string.attach_gallery)),
            Pair("audio", getString(R.string.attach_audio))
        )

        panel.removeAllViews()
        val padH = dpToPx(16f).toInt()
        val padV = dpToPx(12f).toInt()
        items.forEach { (type, label) ->
            val tv = android.widget.TextView(themedCtx).apply {
                text = label
                textSize = 14f
                setPadding(padH, padV, padH, padV)
                isClickable = true
                isFocusable = true
                val ta = themedCtx.obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground))
                background = ta.getDrawable(0)
                ta.recycle()
                setOnClickListener {
                    panel.visibility = View.GONE
                    openActivity("new_classrecord_$type")
                }
            }
            panel.addView(tv)
        }
        panel.visibility = View.VISIBLE
    }

    private fun showModelMenu(anchor: ImageButton) {
        val view = overlayView ?: return
        val panel = view.findViewById<LinearLayout>(R.id.model_picker_panel)
        if (panel.visibility == View.VISIBLE) { panel.visibility = View.GONE; return }

        val prefs = AppPreferences(this)
        val providers = buildList {
            if (prefs.geminiEnabled && prefs.geminiApiKey.isNotBlank()) add(Pair("gemini", R.drawable.ic_ai_gemini))
            if (prefs.mimoEnabled && prefs.mimoApiKey.isNotBlank()) add(Pair("mimo", R.drawable.ic_ai_mimo))
            if (prefs.claudeEnabled && prefs.claudeApiKey.isNotBlank()) add(Pair("claude", R.drawable.ic_ai_claude))
            if (prefs.openaiEnabled && prefs.openaiApiKey.isNotBlank()) add(Pair("openai", R.drawable.ic_ai_openai))
            if (prefs.groqEnabled && prefs.groqApiKey.isNotBlank()) add(Pair("groq", R.drawable.ic_ai_groq))
            if (prefs.deepseekEnabled && prefs.deepseekApiKey.isNotBlank()) add(Pair("deepseek", R.drawable.ic_ai_deepseek))
        }
        if (providers.isEmpty()) return

        panel.removeAllViews()
        val size = dpToPx(44f).toInt()
        val pad = dpToPx(8f).toInt()
        providers.forEach { (id, iconRes) ->
            val btn = ImageButton(themedCtx).apply {
                layoutParams = LinearLayout.LayoutParams(size, size)
                setImageResource(iconRes)
                background = null
                scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                setPadding(pad, pad, pad, pad)
                setOnClickListener {
                    prefs.preferredChatProvider = id
                    prefs.preferredNotifProvider = id
                    updateModelIcon(anchor)
                    panel.visibility = View.GONE
                }
            }
            panel.addView(btn)
        }
        panel.visibility = View.VISIBLE
    }

    private fun hideModelPicker() {
        overlayView?.findViewById<View>(R.id.model_picker_panel)?.visibility = View.GONE
    }

    private fun hideAttachmentPicker() {
        overlayView?.findViewById<View>(R.id.attachment_picker_panel)?.visibility = View.GONE
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

    // ── AI 對話面板 ──────────────────────────────────────────────────────────

    private fun showChatPanel() {
        val view = overlayView ?: return
        hideModelPicker()
        hideAttachmentPicker()
        view.findViewById<View>(R.id.bottom_bar).visibility = View.GONE
        val panel = view.findViewById<LinearLayout>(R.id.chat_panel)
        chatMessages.clear()
        chatAdapter = OverlayChatAdapter(chatMessages)
        val rv = panel.findViewById<RecyclerView>(R.id.rv_chat)
        rv.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        rv.adapter = chatAdapter
        panel.visibility = View.VISIBLE

        val modelBtn = panel.findViewById<ImageButton>(R.id.btn_chat_model_panel)
        updateModelIcon(modelBtn)
        modelBtn.setOnClickListener { showModelMenu(it as ImageButton) }

        panel.findViewById<View>(R.id.btn_chat_close).setOnClickListener { hideChatPanel() }

        val inputEt = panel.findViewById<EditText>(R.id.et_chat_input)
        inputEt.setText("")
        panel.findViewById<View>(R.id.btn_chat_send).setOnClickListener {
            val msg = inputEt.text.toString().trim()
            if (msg.isEmpty()) return@setOnClickListener
            inputEt.setText("")
            sendChat(msg, rv, panel.findViewById(R.id.pb_chat))
        }

        setupDragHandle(panel)

        inputEt.requestFocus()
        showKeyboard(inputEt)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupDragHandle(panel: LinearLayout) {
        var dragStartY = 0f
        panel.findViewById<View>(R.id.drag_handle_area).setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { dragStartY = event.rawY; true }
                MotionEvent.ACTION_UP -> {
                    val delta = dragStartY - event.rawY
                    when {
                        delta > 80 && !isChatFullScreen -> expandChat(panel)
                        delta < -80 && isChatFullScreen -> collapseChat(panel)
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun expandChat(panel: LinearLayout) {
        val displayHeight = resources.displayMetrics.heightPixels
        animateChatHeight(panel, panel.height, displayHeight)
        isChatFullScreen = true
    }

    private fun collapseChat(panel: LinearLayout) {
        animateChatHeight(panel, panel.height, dpToPx(400f).toInt())
        isChatFullScreen = false
    }

    private fun animateChatHeight(panel: LinearLayout, from: Int, to: Int) {
        ValueAnimator.ofInt(from, to).apply {
            duration = 280
            interpolator = PathInterpolator(0.2f, 0f, 0f, 1f)
            addUpdateListener {
                panel.layoutParams = panel.layoutParams.also { lp -> lp.height = it.animatedValue as Int }
            }
            start()
        }
    }

    private fun hideChatPanel() {
        val view = overlayView ?: return
        view.findViewById<View>(R.id.chat_panel).visibility = View.GONE
        view.findViewById<View>(R.id.bottom_bar).visibility = View.VISIBLE
        chatMessages.clear()
        chatAdapter = null
        isChatFullScreen = false
        hideKeyboard()
    }

    private fun sendChat(message: String, rv: RecyclerView, pb: View) {
        val prefs = AppPreferences(this)
        chatMessages.add(OverlayChatAdapter.ChatMessage(message, true))
        chatAdapter?.notifyItemInserted(chatMessages.size - 1)
        rv.scrollToPosition(chatMessages.size - 1)
        pb.visibility = View.VISIBLE

        val history = chatMessages.dropLast(1).map { Pair(it.text, it.isUser) }

        serviceScope.launch(Dispatchers.IO) {
            val provider = prefs.preferredChatProvider
            val apiKey = when (provider) {
                "openai"   -> prefs.openaiApiKey
                "claude"   -> prefs.claudeApiKey
                "groq"     -> prefs.groqApiKey
                "deepseek" -> prefs.deepseekApiKey
                "mimo"     -> prefs.mimoApiKey
                else       -> prefs.geminiApiKey
            }
            val response: String? = FeatureManager.getAi(this@FloatingQuickAddService)
                ?.chatWithContext(provider, apiKey, "", history, message)
            withContext(Dispatchers.Main) {
                pb.visibility = View.GONE
                chatMessages.add(OverlayChatAdapter.ChatMessage(response ?: "（發生錯誤，請稍後再試）", false))
                chatAdapter?.notifyItemInserted(chatMessages.size - 1)
                rv.scrollToPosition(chatMessages.size - 1)
            }
        }
    }

    // ── 提醒 mini form ────────────────────────────────────────────────────────

    private fun showReminderForm() {
        val view = overlayView ?: return
        hideModelPicker()
        hideAttachmentPicker()
        view.findViewById<View>(R.id.bottom_bar).visibility = View.GONE
        val form = view.findViewById<LinearLayout>(R.id.form_reminder)
        reminderSelectedDate = LocalDate.now()
        val titleEt = form.findViewById<EditText>(R.id.et_reminder_title)
        titleEt.setText("")
        view.findViewById<com.google.android.material.chip.Chip>(R.id.chip_today).isChecked = true
        form.visibility = View.VISIBLE
        titleEt.requestFocus()
        showKeyboard(titleEt)

        form.findViewById<com.google.android.material.chip.ChipGroup>(R.id.chip_group_date)
            .setOnCheckedStateChangeListener { _, checkedIds ->
                reminderSelectedDate = when (checkedIds.firstOrNull()) {
                    R.id.chip_today -> LocalDate.now()
                    R.id.chip_tomorrow -> LocalDate.now().plusDays(1)
                    R.id.chip_day_after -> LocalDate.now().plusDays(2)
                    else -> LocalDate.now()
                }
            }
        form.findViewById<View>(R.id.btn_reminder_cancel).setOnClickListener { hideReminderForm() }
        form.findViewById<View>(R.id.btn_reminder_save).setOnClickListener {
            val title = titleEt.text.toString().trim()
            if (title.isEmpty()) return@setOnClickListener
            saveReminder(title)
        }
    }

    private fun hideReminderForm() {
        val view = overlayView ?: return
        view.findViewById<View>(R.id.form_reminder).visibility = View.GONE
        view.findViewById<View>(R.id.bottom_bar).visibility = View.VISIBLE
        hideKeyboard()
    }

    private fun saveReminder(title: String) {
        val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val entity = ReminderEntity(
            title = title,
            dueDate = reminderSelectedDate.format(fmt),
            category = "REMINDER"
        )
        serviceScope.launch {
            (application as ClassNoteApplication).reminderRepository.insertReminder(entity)
            withContext(Dispatchers.Main) { dismiss() }
        }
    }

    // ── 上課筆記 mini form ───────────────────────────────────────────────────

    private fun showClassRecordForm() {
        val view = overlayView ?: return
        hideModelPicker()
        hideAttachmentPicker()
        view.findViewById<View>(R.id.bottom_bar).visibility = View.GONE
        val form = view.findViewById<LinearLayout>(R.id.form_classrecord)
        val titleEt = form.findViewById<EditText>(R.id.et_record_title)
        titleEt.setText("")
        form.findViewById<EditText>(R.id.et_record_note).setText("")
        form.visibility = View.VISIBLE
        titleEt.requestFocus()
        showKeyboard(titleEt)

        form.findViewById<View>(R.id.btn_record_cancel).setOnClickListener { hideClassRecordForm() }
        form.findViewById<View>(R.id.btn_record_save).setOnClickListener {
            val title = titleEt.text.toString().trim()
            val note = form.findViewById<EditText>(R.id.et_record_note).text.toString().trim()
            saveClassRecord(title, note)
        }
    }

    private fun hideClassRecordForm() {
        val view = overlayView ?: return
        view.findViewById<View>(R.id.form_classrecord).visibility = View.GONE
        view.findViewById<View>(R.id.bottom_bar).visibility = View.VISIBLE
        hideKeyboard()
    }

    private fun saveClassRecord(title: String, note: String) {
        val today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val entity = ClassRecordEntity(
            date = today,
            timeLabel = "",
            title = title.ifEmpty { "上課筆記" },
            textNote = note,
            aiSummary = "",
            createdAt = System.currentTimeMillis()
        )
        serviceScope.launch {
            (application as ClassNoteApplication).classRecordRepository.insert(entity)
            withContext(Dispatchers.Main) { dismiss() }
        }
    }

    // ── IME helpers ──────────────────────────────────────────────────────────

    private fun showKeyboard(et: EditText) {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        handler.postDelayed({ imm.showSoftInput(et, InputMethodManager.SHOW_IMPLICIT) }, 200)
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        overlayView?.windowToken?.let { imm.hideSoftInputFromWindow(it, 0) }
    }

    private fun dpToPx(dp: Float) = dp * resources.displayMetrics.density

    companion object {
        const val ACTION_SHOW = "com.rendy.classnote.action.SHOW_OVERLAY"
        const val ACTION_DISMISS = "com.rendy.classnote.action.DISMISS_OVERLAY"
        private const val NOTIF_ID = 9001
    }
}

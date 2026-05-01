package com.rendy.classnote.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rendy.classnote.ClassNoteApplication
import com.rendy.classnote.R
import com.rendy.classnote.data.AppPreferences
import com.rendy.classnote.databinding.SheetAlarmPermBinding
import com.rendy.classnote.notification.NotificationHelper

class AlarmPermSheet : Fragment() {

    private var _binding: SheetAlarmPermBinding? = null
    private val binding get() = _binding!!

    private lateinit var prefs: AppPreferences

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = SheetAlarmPermBinding.inflate(inflater, container, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = (requireActivity().application as ClassNoteApplication).appPreferences
        setupAlarmSection()
        setupPermissionsSection()
        setupDndSection()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatuses()
        updateDndStatus()
    }

    // ── 鬧鐘提醒 ─────────────────────────────────────────────────────────────

    private fun setupAlarmSection() {
        binding.switchFullScreenAlarm.isChecked = prefs.fullScreenAlarmEnabled
        binding.switchFullScreenAlarm.setOnCheckedChangeListener { _, checked ->
            prefs.fullScreenAlarmEnabled = checked
            binding.cardSnooze.alpha = if (checked) 1f else 0.4f
        }
        binding.cardSnooze.alpha = if (prefs.fullScreenAlarmEnabled) 1f else 0.4f

        updateSnoozeSummary()
        binding.cardSnooze.setOnClickListener {
            if (!prefs.fullScreenAlarmEnabled) return@setOnClickListener
            showSnoozeDialog()
        }

        setupQuietHoursSection()
    }

    private fun setupQuietHoursSection() {
        updateQuietHoursTimes()

        binding.tvQuietStart.setOnClickListener {
            val h = prefs.quietHoursStart
            android.app.TimePickerDialog(requireContext(), { _, hour, _ ->
                prefs.quietHoursStart = hour
                updateQuietHoursTimes()
            }, h, 0, true).show()
        }

        binding.tvQuietEnd.setOnClickListener {
            val h = prefs.quietHoursEnd
            android.app.TimePickerDialog(requireContext(), { _, hour, _ ->
                prefs.quietHoursEnd = hour
                updateQuietHoursTimes()
            }, h, 0, true).show()
        }

        binding.rgQuietPolicy.check(
            if (prefs.quietHoursPolicyBefore) R.id.rbQuietBefore else R.id.rbQuietAfter
        )
        binding.rgQuietPolicy.setOnCheckedChangeListener { _, checkedId ->
            prefs.quietHoursPolicyBefore = (checkedId == R.id.rbQuietBefore)
        }
    }

    private fun updateQuietHoursTimes() {
        binding.tvQuietStart.text = "%02d:00".format(prefs.quietHoursStart)
        binding.tvQuietEnd.text = "%02d:00".format(prefs.quietHoursEnd)
    }

    private fun updateSnoozeSummary() {
        val label = when (prefs.snoozeDurationMinutes) {
            5 -> getString(R.string.snooze_5min)
            15 -> getString(R.string.snooze_15min)
            30 -> getString(R.string.snooze_30min)
            else -> getString(R.string.snooze_10min)
        }
        binding.tvSnoozeValue.text = label
    }

    private fun showSnoozeDialog() {
        val options = arrayOf(
            getString(R.string.snooze_5min),
            getString(R.string.snooze_10min),
            getString(R.string.snooze_15min),
            getString(R.string.snooze_30min)
        )
        val minutes = intArrayOf(5, 10, 15, 30)
        val current = minutes.indexOfFirst { it == prefs.snoozeDurationMinutes }.coerceAtLeast(0)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.settings_snooze_title))
            .setSingleChoiceItems(options, current) { dialog, which ->
                prefs.snoozeDurationMinutes = minutes[which]
                updateSnoozeSummary()
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    // ── 權限狀態 ──────────────────────────────────────────────────────────────

    private fun setupPermissionsSection() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            binding.cardPermFullScreen.visibility = View.VISIBLE
            binding.cardPermFullScreen.setOnClickListener {
                startActivity(
                    Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                        data = Uri.fromParts("package", requireContext().packageName, null)
                    }
                )
            }
        } else {
            binding.cardPermFullScreen.visibility = View.GONE
        }

        binding.cardPermOverlay.setOnClickListener {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                    data = Uri.fromParts("package", requireContext().packageName, null)
                }
            )
        }

        if (isXiaomiDevice()) {
            binding.cardPermXiaomi.visibility = View.VISIBLE
            binding.btnXiaomiPermSettings.setOnClickListener {
                openXiaomiAppPermissions()
            }
        } else {
            binding.cardPermXiaomi.visibility = View.GONE
        }

        updatePermissionStatuses()
    }

    private fun updatePermissionStatuses() {
        val granted = getString(R.string.settings_perm_granted)
        val notGranted = getString(R.string.settings_perm_not_granted)
        val grantedColor = requireContext().getColor(R.color.chip_reminder_color)
        val notGrantedColor = requireContext().getColor(R.color.chip_exam_color)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val ok = NotificationManagerCompat.from(requireContext()).canUseFullScreenIntent()
            binding.tvPermFullScreenStatus.text = if (ok) granted else notGranted
            binding.tvPermFullScreenStatus.setTextColor(if (ok) grantedColor else notGrantedColor)
        }

        val overlayOk = Settings.canDrawOverlays(requireContext())
        binding.tvPermOverlayStatus.text = if (overlayOk) granted else notGranted
        binding.tvPermOverlayStatus.setTextColor(if (overlayOk) grantedColor else notGrantedColor)

        if (isXiaomiDevice()) {
            when (isXiaomiLockScreenGranted(requireContext())) {
                true -> {
                    binding.tvPermXiaomiStatus.text = granted
                    binding.tvPermXiaomiStatus.setTextColor(grantedColor)
                }
                false -> {
                    binding.tvPermXiaomiStatus.text = notGranted
                    binding.tvPermXiaomiStatus.setTextColor(notGrantedColor)
                }
                null -> {
                    binding.tvPermXiaomiStatus.text = getString(R.string.settings_perm_status_unknown)
                    binding.tvPermXiaomiStatus.setTextColor(requireContext().getColor(android.R.color.darker_gray))
                }
            }
        }
    }

    // ── 勿擾模式穿透 ─────────────────────────────────────────────────────────────

    private fun setupDndSection() {
        binding.switchBypassDnd.isChecked = prefs.bypassDndEnabled
        updateDndStatus()

        binding.switchBypassDnd.setOnCheckedChangeListener { _, checked ->
            prefs.bypassDndEnabled = checked
            if (checked) {
                val nm = requireContext().getSystemService(android.app.NotificationManager::class.java)
                if (!nm.isNotificationPolicyAccessGranted) {
                    startActivity(Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                }
            }
            applyDndChannel()
            updateDndStatus()
        }

        binding.tvPermDndStatus.setOnClickListener {
            startActivity(Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        }
    }

    private fun updateDndStatus() {
        val enabled = prefs.bypassDndEnabled
        binding.tvPermDndStatus.visibility = if (enabled) View.VISIBLE else View.GONE
        if (!enabled) return

        val nm = requireContext().getSystemService(android.app.NotificationManager::class.java)
        val grantedDnd = nm.isNotificationPolicyAccessGranted
        val grantedColor = requireContext().getColor(R.color.chip_reminder_color)
        val notGrantedColor = requireContext().getColor(R.color.chip_exam_color)
        binding.tvPermDndStatus.text = if (grantedDnd)
            getString(R.string.settings_perm_dnd_access_granted)
        else
            getString(R.string.settings_perm_dnd_access_denied)
        binding.tvPermDndStatus.setTextColor(if (grantedDnd) grantedColor else notGrantedColor)

        if (grantedDnd) applyDndChannel()
    }

    private fun applyDndChannel() {
        val nm = requireContext().getSystemService(android.app.NotificationManager::class.java)
        val bypassDnd = prefs.bypassDndEnabled && nm.isNotificationPolicyAccessGranted
        NotificationHelper.createNotificationChannel(requireContext(), bypassDnd)
    }

    // ── Xiaomi 偵測與權限查詢 ─────────────────────────────────────────────────

    private fun isXiaomiLockScreenGranted(context: Context): Boolean? = try {
        val manager = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        val method = android.app.AppOpsManager::class.java.getDeclaredMethod(
            "checkOpNoThrow",
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            String::class.java
        )
        val result = method.invoke(
            manager,
            10020,
            android.os.Binder.getCallingUid(),
            context.packageName
        ) as Int
        result == android.app.AppOpsManager.MODE_ALLOWED
    } catch (_: Exception) { null }

    private fun isXiaomiDevice(): Boolean =
        android.os.Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true) ||
        android.os.Build.MANUFACTURER.equals("Redmi", ignoreCase = true) ||
        !getSystemProperty("ro.miui.ui.version.name").isNullOrEmpty()

    private fun getSystemProperty(key: String): String? = try {
        @Suppress("PrivateApi")
        val method = Class.forName("android.os.SystemProperties")
            .getMethod("get", String::class.java)
        method.invoke(null, key) as? String
    } catch (_: Exception) { null }

    private fun openXiaomiAppPermissions() {
        val miuiIntent = Intent("miui.intent.action.APP_PERM_EDITOR").apply {
            setClassName(
                "com.miui.securitycenter",
                "com.miui.permcenter.permissions.PermissionsEditorActivity"
            )
            putExtra("extra_pkgname", requireContext().packageName)
        }
        val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", requireContext().packageName, null)
        }
        try {
            startActivity(miuiIntent)
        } catch (_: Exception) {
            startActivity(fallback)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "AlarmPermSheet"
    }
}

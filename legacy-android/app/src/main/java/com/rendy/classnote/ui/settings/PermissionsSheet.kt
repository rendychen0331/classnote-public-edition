package com.rendy.classnote.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.app.NotificationManagerCompat
import androidx.fragment.app.Fragment
import com.rendy.classnote.ClassNoteApplication
import com.rendy.classnote.R
import com.rendy.classnote.data.AppPreferences
import com.rendy.classnote.databinding.SheetPermissionsBinding

class PermissionsSheet : Fragment() {

    private var _binding: SheetPermissionsBinding? = null
    private val binding get() = _binding!!

    private lateinit var prefs: AppPreferences

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = SheetPermissionsBinding.inflate(inflater, container, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = (requireActivity().application as ClassNoteApplication).appPreferences
        setupPermissionsSection()
        setupDndSection()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatuses()
        updateDndStatus()
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
        binding.cardPermDnd.setOnClickListener {
            startActivity(Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        }
        updateDndStatus()
    }

    private fun updateDndStatus() {
        val nm = requireContext().getSystemService(android.app.NotificationManager::class.java)
        val granted = nm.isNotificationPolicyAccessGranted
        val grantedColor = requireContext().getColor(R.color.chip_reminder_color)
        val notGrantedColor = requireContext().getColor(R.color.chip_exam_color)
        binding.tvPermDndStatus.text = if (granted)
            getString(R.string.settings_perm_dnd_access_granted)
        else
            getString(R.string.settings_perm_dnd_access_denied)
        binding.tvPermDndStatus.setTextColor(if (granted) grantedColor else notGrantedColor)
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
        const val TAG = "PermissionsSheet"
    }
}

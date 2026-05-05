package com.rendy.classnote.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rendy.classnote.R
import com.rendy.classnote.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 停用 edge-to-edge，讓系統 bar 正常佔用空間，不讓內容畫到 status bar 後面
        WindowCompat.setDecorFitsSystemWindows(window, true)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        binding.bottomNavigation.setupWithNavController(navController)
        // setupWithNavController 同步 NavController→BottomNav 選中狀態（保留）
        // 覆寫 item 點擊：切換 tab 前若在設定頁（含子頁面），先 pop 回根，避免子頁被留在 back stack
        val settingsDestinations = setOf(
            R.id.settingsFragment, R.id.alarmPermFragment, R.id.syncFragment,
            R.id.backupSyncFragment, R.id.googleSyncFragment, R.id.microsoftSyncFragment,
            R.id.localSyncFragment, R.id.weatherNotifFragment,
            R.id.aiSettingsFragment, R.id.apiLogFragment
        )
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            if (navController.currentDestination?.id in settingsDestinations) {
                navController.popBackStack(R.id.settingsFragment, inclusive = true)
            }
            NavigationUI.onNavDestinationSelected(item, navController)
        }
        // 重選同一 tab 時，若目前在設定子頁，強制導回該 tab
        binding.bottomNavigation.setOnItemReselectedListener { item ->
            if (navController.currentDestination?.id != item.itemId) {
                NavigationUI.onNavDestinationSelected(item, navController)
            }
        }

        val appBarConfig = AppBarConfiguration(
            setOf(
                R.id.scheduleFragment,
                R.id.reminderListFragment,
                R.id.formulaListFragment,
                R.id.weatherFragment,
                R.id.classRecordListFragment
            )
        )
        binding.toolbar.setupWithNavController(navController, appBarConfig)

        binding.toolbar.inflateMenu(R.menu.toolbar_menu)
        binding.toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_settings) {
                navController.navigate(R.id.settingsFragment)
                true
            } else false
        }

        navController.addOnDestinationChangedListener { _, destination, _ ->
            binding.toolbar.menu.findItem(R.id.action_settings)
                ?.isVisible = destination.id !in settingsDestinations
        }

        // adjustNothing：BottomNav 固定不動，鍵盤彈起時手動把內容區上推
        // 注意：必須呼叫 ViewCompat.onApplyWindowInsets(view, insets) 讓
        // CoordinatorLayout 的 fitsSystemWindows 繼續處理 status bar padding
        val defaultNavBottomMargin = (80 * resources.displayMetrics.density).toInt()
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val imeHeight = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            binding.navHostFragment.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = if (imeHeight > 0) imeHeight else defaultNavBottomMargin
            }
            ViewCompat.onApplyWindowInsets(view, insets)
        }

        // 僅首次建立時請求權限（避免旋轉螢幕重複跳轉設定頁）
        if (savedInstanceState == null) {
            requestRequiredPermissions()
            handleNavigateIntent(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        // 標準權限都已授予後，才引導小米鎖屏額外設定
        if (areStandardPermissionsGranted()) {
            requestXiaomiLockScreenPermission()
        }
    }

    private fun areStandardPermissionsGranted(): Boolean {
        val nm = NotificationManagerCompat.from(this)
        if (!nm.areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(android.app.AlarmManager::class.java)
            if (!alarmManager.canScheduleExactAlarms()) return false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            if (!nm.canUseFullScreenIntent()) return false
        }
        if (!Settings.canDrawOverlays(this)) return false
        return true
    }

    // app 已在後台時，通知點擊會觸發 onNewIntent 而非 onCreate
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNavigateIntent(intent)
    }

    private fun handleNavigateIntent(intent: Intent?) {
        when (intent?.getStringExtra("navigate_to")) {
            "reminders" -> binding.bottomNavigation.selectedItemId = R.id.reminderListFragment
            "schedule"  -> binding.bottomNavigation.selectedItemId = R.id.scheduleFragment
        }
    }

    private fun requestRequiredPermissions() {
        val prefs = getSharedPreferences("classnote_prefs", MODE_PRIVATE)

        // POST_NOTIFICATIONS (Android 13+) — 系統自動處理已授予的狀況，不需額外記錄
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        // SCHEDULE_EXACT_ALARM (Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(android.app.AlarmManager::class.java)
            if (!alarmManager.canScheduleExactAlarms() &&
                !prefs.getBoolean("exact_alarm_prompted", false)) {
                prefs.edit().putBoolean("exact_alarm_prompted", true).apply()
                startActivity(
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        data = Uri.fromParts("package", packageName, null)
                    }
                )
            }
        }

        // USE_FULL_SCREEN_INTENT (Android 14+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val nm = NotificationManagerCompat.from(this)
            if (!nm.canUseFullScreenIntent() &&
                !prefs.getBoolean("full_screen_intent_prompted", false)) {
                showFullScreenIntentDialog(prefs)
            }
        }

        // SYSTEM_ALERT_WINDOW
        if (!Settings.canDrawOverlays(this) &&
            !prefs.getBoolean("overlay_prompted", false)) {
            showOverlayPermissionDialog(prefs)
        }

        // 允許安裝未知來源 App (Android 8+，非 Play Store 安裝)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !packageManager.canRequestPackageInstalls() &&
            !prefs.getBoolean("install_unknown_prompted", false) &&
            !isInstalledFromPlayStore()) {
            showInstallUnknownAppsDialog(prefs)
        }

        // 允許受限制的設定 (Android 13+，非 Play Store 安裝)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !prefs.getBoolean("restricted_settings_prompted", false) &&
            !isInstalledFromPlayStore()) {
            showRestrictedSettingsDialog(prefs)
        }
    }

    private fun showInstallUnknownAppsDialog(prefs: android.content.SharedPreferences) {
        prefs.edit().putBoolean("install_unknown_prompted", true).apply()
        MaterialAlertDialogBuilder(this)
            .setTitle("允許安裝未知來源應用程式")
            .setMessage(
                "ClassNote 的應用程式內更新功能需要此權限，才能直接安裝下載的新版 APK。\n\n" +
                "不開啟：更新功能仍可下載 APK，但需手動從檔案管理器安裝。\n\n" +
                "⚠️ 安全提示：此權限允許 ClassNote 安裝 APK 檔案。" +
                "ClassNote 的更新來源為官方 GitHub Releases，建議開啟後僅信任已知來源的 App。"
            )
            .setPositiveButton("前往設定") { _, _ ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startActivity(
                        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                            data = Uri.fromParts("package", packageName, null)
                        }
                    )
                }
            }
            .setNegativeButton("略過", null)
            .show()
    }

    private fun isInstalledFromPlayStore(): Boolean = try {
        val installer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            packageManager.getInstallSourceInfo(packageName).installingPackageName
        } else {
            @Suppress("DEPRECATION")
            packageManager.getInstallerPackageName(packageName)
        }
        installer == "com.android.vending"
    } catch (_: Exception) { false }

    private fun showRestrictedSettingsDialog(prefs: android.content.SharedPreferences) {
        prefs.edit().putBoolean("restricted_settings_prompted", true).apply()
        val steps = if (isXiaomiDevice()) {
            "步驟：\n" +
            "1. 點擊「前往設定」開啟應用程式資訊\n" +
            "2. 捲到最底部「進階設定」區域\n" +
            "3. 開啟「允許受限制的設定」"
        } else {
            "步驟：\n" +
            "1. 點擊「前往設定」開啟應用程式資訊\n" +
            "2. 點右上角 ⋮（三點選單）\n" +
            "3. 選擇「允許受限制的設定」"
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("開啟「允許受限制的設定」")
            .setMessage(
                "此 App 非從 Play Store 安裝，Android 13+ 預設限制部分進階功能。\n\n" +
                "不開啟：大部分功能正常使用。\n" +
                "受影響功能：通知辨識、應用程式內安裝更新（下載後需手動從檔案管理器安裝）。\n\n" +
                steps
            )
            .setPositiveButton("前往設定") { _, _ ->
                startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", packageName, null)
                    }
                )
            }
            .setNegativeButton("略過", null)
            .show()
    }

    private fun showFullScreenIntentDialog(prefs: android.content.SharedPreferences) {
        prefs.edit().putBoolean("full_screen_intent_prompted", true).apply()
        MaterialAlertDialogBuilder(this)
            .setTitle("開啟全螢幕提醒")
            .setMessage("允許 ClassNote 在鎖屏或使用其他 App 時，以全螢幕方式顯示提醒（類似鬧鐘）。")
            .setPositiveButton("前往設定") { _, _ ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startActivity(
                        Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                            data = Uri.fromParts("package", packageName, null)
                        }
                    )
                }
            }
            .setNegativeButton("略過", null)
            .show()
    }

    private fun showOverlayPermissionDialog(prefs: android.content.SharedPreferences) {
        prefs.edit().putBoolean("overlay_prompted", true).apply()
        MaterialAlertDialogBuilder(this)
            .setTitle("顯示在其他 App 上方")
            .setMessage("允許 ClassNote 在使用其他 App 時也能顯示提醒視窗（類似鬧鐘）。\n\n前往設定 → 允許「顯示在其他應用程式上方」。")
            .setPositiveButton("前往設定") { _, _ ->
                startActivity(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                        data = Uri.fromParts("package", packageName, null)
                    }
                )
            }
            .setNegativeButton("略過", null)
            .show()
    }

    // ── Xiaomi HyperOS / MIUI 額外鎖屏權限引導 ──────────────────────────────

    private fun requestXiaomiLockScreenPermission() {
        if (!isXiaomiDevice()) return
        val prefs = getSharedPreferences("classnote_prefs", MODE_PRIVATE)
        if (prefs.getBoolean("xiaomi_lockscreen_prompted", false)) return
        MaterialAlertDialogBuilder(this)
            .setTitle("小米 / HyperOS：開啟鎖屏顯示")
            .setMessage(
                "在小米 HyperOS / MIUI 上，需額外手動開啟「鎖定螢幕顯示」，" +
                "否則提醒視窗在鎖屏時無法彈出。\n\n" +
                "路徑：設定 → 應用程式 → 管理應用程式 → ClassNote\n" +
                "→「其他權限」→ 開啟「在鎖定螢幕上顯示」"
            )
            .setPositiveButton("前往 App 設定") { _, _ ->
                prefs.edit().putBoolean("xiaomi_lockscreen_prompted", true).apply()
                openXiaomiAppPermissions()
            }
            .setNegativeButton("略過") { _, _ ->
                prefs.edit().putBoolean("xiaomi_lockscreen_prompted", true).apply()
            }
            .show()
    }

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
        // 嘗試直接開啟 MIUI 的 App 權限編輯頁
        val miuiIntent = Intent("miui.intent.action.APP_PERM_EDITOR").apply {
            setClassName(
                "com.miui.securitycenter",
                "com.miui.permcenter.permissions.PermissionsEditorActivity"
            )
            putExtra("extra_pkgname", packageName)
        }
        val fallbackIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        try {
            startActivity(miuiIntent)
        } catch (_: Exception) {
            startActivity(fallbackIntent)
        }
    }
}

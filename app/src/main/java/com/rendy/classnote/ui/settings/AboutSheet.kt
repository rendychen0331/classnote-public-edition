package com.rendy.classnote.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rendy.classnote.BuildConfig
import com.rendy.classnote.data.AppPreferences
import com.rendy.classnote.data.AutoUpdateWorker
import com.rendy.classnote.data.UpdateChecker
import com.rendy.classnote.databinding.SheetAboutBinding
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

private val UPDATE_INTERVAL_OPTIONS = listOf(24, 72, 168)
private fun intervalLabel(hours: Int) = when (hours) {
    24   -> "每天"
    72   -> "每 3 天"
    168  -> "每週"
    else -> "每 $hours 小時"
}

class AboutSheet : Fragment() {

    private var _binding: SheetAboutBinding? = null
    private val binding get() = _binding!!
    private lateinit var prefs: AppPreferences

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = SheetAboutBinding.inflate(inflater, container, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = AppPreferences(requireContext())
        setupVersionInfo()
        setupUpdateSection()
        refreshInstallButton()
        autoCheckUpdate()
    }

    private fun setupVersionInfo() {
        val versionName = BuildConfig.VERSION_NAME
        binding.tvAboutVersion.text = versionName
        val dashIdx = versionName.indexOf('-')
        if (dashIdx >= 0) {
            val raw = versionName.substring(dashIdx + 1)
            binding.tvAboutBuildTime.text = runCatching {
                val parts = raw.split("-")
                val d = parts[0]
                val t = parts[1]
                "${d.substring(0, 4)}/${d.substring(4, 6)}/${d.substring(6, 8)}  UTC ${t.substring(0, 2)}:${t.substring(2, 4)}"
            }.getOrDefault(raw)
        } else {
            binding.tvAboutBuildTime.text = "-"
        }
    }

    private fun refreshInstallButton() {
        val cached = UpdateChecker.getDownloadedApkFile(requireContext())
        if (cached != null) {
            binding.btnCheckUpdate.text = "立即安裝"
            binding.btnCheckUpdate.isEnabled = true
            binding.btnCheckUpdate.setOnClickListener {
                UpdateChecker.triggerInstallFromFile(requireContext(), cached)
                refreshInstallButton()
            }
        } else {
            prefs.activeApkFileName = ""
            binding.btnCheckUpdate.text = "檢查更新"
            binding.btnCheckUpdate.isEnabled = true
            binding.btnCheckUpdate.setOnClickListener {
                performUpdateCheck(force = true)
            }
        }
    }

    private fun setupUpdateSection() {
        val autoUpdateEnabled = prefs.autoUpdateEnabled
        binding.switchAutoUpdate.isChecked = autoUpdateEnabled
        binding.rowAutoUpdateInterval.visibility = if (autoUpdateEnabled) View.VISIBLE else View.GONE
        binding.tvAutoUpdateInterval.text = intervalLabel(prefs.autoUpdateIntervalHours)
        if (autoUpdateEnabled) scheduleAutoUpdate()

        binding.switchAutoUpdate.setOnCheckedChangeListener { _, checked ->
            prefs.autoUpdateEnabled = checked
            binding.rowAutoUpdateInterval.visibility = if (checked) View.VISIBLE else View.GONE
            if (checked) scheduleAutoUpdate() else cancelAutoUpdate()
        }

        binding.rowAutoUpdateInterval.setOnClickListener {
            showAutoUpdateIntervalDialog()
        }
    }

    private fun autoCheckUpdate() {
        lifecycleScope.launch {
            val info = UpdateChecker.checkForUpdate(requireContext(), force = false) ?: return@launch
            if (info.isNewer) showUpdateAvailable(info)
        }
    }

    private fun performUpdateCheck(force: Boolean) {
        binding.btnCheckUpdate.isEnabled = false
        binding.tvUpdateStatus.text = "檢查中..."
        lifecycleScope.launch {
            val info = UpdateChecker.checkForUpdate(requireContext(), force = force)
            if (_binding == null) return@launch
            when {
                info == null  -> binding.tvUpdateStatus.text = "檢查失敗"
                info.isNewer  -> {
                    binding.tvUpdateStatus.text = "有新版本"
                    binding.btnCheckUpdate.isEnabled = true
                    showUpdateAvailable(info)
                }
                else          -> {
                    binding.tvUpdateStatus.text = "已是最新"
                    binding.btnCheckUpdate.isEnabled = true
                }
            }
        }
    }

    private fun showUpdateAvailable(info: UpdateChecker.ReleaseInfo) {
        if (!isAdded) return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("發現新版本 ${info.tagName}")
            .setMessage("目前版本：${BuildConfig.VERSION_NAME}\n\n是否立即下載並安裝？")
            .setPositiveButton("下載安裝") { _, _ ->
                startDownload(info.apkUrl, info.tagName)
            }
            .setNegativeButton("稍後", null)
            .show()
    }

    private fun startDownload(apkUrl: String, tagName: String) {
        binding.tvUpdateStatus.text = "下載中... 0%"
        binding.btnCheckUpdate.isEnabled = false
        binding.btnCheckUpdate.text = "下載中..."

        lifecycleScope.launch {
            val success = UpdateChecker.downloadApk(
                context = requireContext(),
                apkUrl = apkUrl,
                tagName = tagName,
                onProgress = { pct ->
                    _binding?.tvUpdateStatus?.text = "下載中... $pct%"
                }
            )
            if (_binding == null) return@launch
            if (success) {
                binding.tvUpdateStatus.text = "下載完成"
                refreshInstallButton()
                UpdateChecker.getDownloadedApkFile(requireContext())?.let {
                    UpdateChecker.triggerInstallFromFile(requireContext(), it)
                }
            } else {
                binding.tvUpdateStatus.text = "下載失敗"
                refreshInstallButton()
            }
        }
    }

    private fun scheduleAutoUpdate() {
        val hours = prefs.autoUpdateIntervalHours.toLong()
        val request = PeriodicWorkRequestBuilder<AutoUpdateWorker>(hours, TimeUnit.HOURS).build()
        WorkManager.getInstance(requireContext()).enqueueUniquePeriodicWork(
            AutoUpdateWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
            request
        )
    }

    private fun cancelAutoUpdate() {
        WorkManager.getInstance(requireContext()).cancelUniqueWork(AutoUpdateWorker.WORK_NAME)
    }

    private fun showAutoUpdateIntervalDialog() {
        val labels = UPDATE_INTERVAL_OPTIONS.map { intervalLabel(it) }.toTypedArray()
        val current = prefs.autoUpdateIntervalHours
        val checked = UPDATE_INTERVAL_OPTIONS.indexOf(current).coerceAtLeast(0)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("更新頻率")
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                val hours = UPDATE_INTERVAL_OPTIONS[which]
                prefs.autoUpdateIntervalHours = hours
                binding.tvAutoUpdateInterval.text = intervalLabel(hours)
                scheduleAutoUpdate()
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

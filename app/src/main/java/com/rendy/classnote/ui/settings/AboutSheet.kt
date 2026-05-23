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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
        autoCheckUpdate()
        resumePendingDownload()
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
                "${d.substring(0, 4)}/${d.substring(4, 6)}/${d.substring(6, 8)}  ${t.substring(0, 2)}:${t.substring(2, 4)}"
            }.getOrDefault(raw)
        } else {
            binding.tvAboutBuildTime.text = "-"
        }
    }

    private fun setupUpdateSection() {
        binding.btnCheckUpdate.setOnClickListener {
            performUpdateCheck(force = true)
        }

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
            binding.btnCheckUpdate.isEnabled = true
            when {
                info == null   -> binding.tvUpdateStatus.text = "檢查失敗"
                info.isNewer   -> {
                    binding.tvUpdateStatus.text = "有新版本"
                    showUpdateAvailable(info)
                }
                else           -> binding.tvUpdateStatus.text = "已是最新"
            }
        }
    }

    private fun showUpdateAvailable(info: UpdateChecker.ReleaseInfo) {
        if (!isAdded) return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("發現新版本 ${info.tagName}")
            .setMessage("目前版本：${BuildConfig.VERSION_NAME}\n\n是否立即下載並安裝？")
            .setPositiveButton("下載安裝") { _, _ ->
                val downloadId = UpdateChecker.downloadAndInstall(requireContext(), info.apkUrl, info.tagName)
                if (downloadId == UpdateChecker.DOWNLOAD_ID_CACHED) {
                    binding.tvUpdateStatus.text = "正在開啟安裝介面…"
                } else {
                    prefs.activeApkDownloadId = downloadId
                    binding.tvUpdateStatus.text = "下載中... 0%"
                    binding.btnCheckUpdate.isEnabled = false
                    trackDownloadProgress(downloadId)
                }
            }
            .setNegativeButton("稍後", null)
            .show()
    }

    private fun resumePendingDownload() {
        val downloadId = prefs.activeApkDownloadId
        if (downloadId <= 0L) return
        binding.tvUpdateStatus.text = "下載中..."
        binding.btnCheckUpdate.isEnabled = false
        trackDownloadProgress(downloadId)
    }

    private fun trackDownloadProgress(downloadId: Long) {
        viewLifecycleOwner.lifecycleScope.launch {
            while (isAdded && _binding != null) {
                val progress = withContext(Dispatchers.IO) {
                    UpdateChecker.queryProgress(requireContext(), downloadId)
                }
                when {
                    progress == 100 -> {
                        val apkFile = UpdateChecker.getDownloadedApkFile(requireContext())
                        prefs.activeApkDownloadId = 0L
                        prefs.activeApkFileName = ""
                        if (_binding != null) {
                            binding.tvUpdateStatus.text = "下載完成"
                            binding.btnCheckUpdate.isEnabled = true
                        }
                        if (apkFile != null) UpdateChecker.triggerInstallFromFile(requireContext(), apkFile)
                        break
                    }
                    progress < 0 -> {
                        prefs.activeApkDownloadId = 0L
                        if (_binding != null) {
                            binding.tvUpdateStatus.text = "下載失敗"
                            binding.btnCheckUpdate.isEnabled = true
                        }
                        break
                    }
                    else -> _binding?.tvUpdateStatus?.text = "下載中... $progress%"
                }
                delay(500)
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

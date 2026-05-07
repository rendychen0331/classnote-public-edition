package com.rendy.classnote.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rendy.classnote.BuildConfig
import com.rendy.classnote.R
import com.rendy.classnote.data.AppPreferences
import com.rendy.classnote.data.AutoUpdateWorker
import com.rendy.classnote.data.UpdateChecker
import com.rendy.classnote.databinding.FragmentSettingsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

private val UPDATE_INTERVAL_OPTIONS = listOf(24, 72, 168)
private fun intervalLabel(hours: Int) = when (hours) {
    24 -> "每天"
    72 -> "每 3 天"
    168 -> "每週"
    else -> "每 $hours 小時"
}

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var prefs: AppPreferences

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = AppPreferences(requireContext())
        setupMenuRows()
        setupAboutSection()
        autoCheckUpdate()
    }

    private fun setupMenuRows() {
        binding.cardMenuAlarm.setOnClickListener {
            findNavController().navigate(R.id.actionSettingsToAlarmPerm)
        }

        binding.cardMenuPermissions.setOnClickListener {
            findNavController().navigate(R.id.actionSettingsToPermissions)
        }

        binding.cardMenuSync.setOnClickListener {
            findNavController().navigate(R.id.actionSettingsToSync)
        }

        binding.cardMenuWeather.setOnClickListener {
            findNavController().navigate(R.id.actionSettingsToWeatherNotif)
        }

        binding.cardMenuAiSettings.setOnClickListener {
            findNavController().navigate(R.id.actionSettingsToAiSettings)
        }

        binding.cardMenuApiLog.setOnClickListener {
            findNavController().navigate(R.id.actionSettingsToApiLog)
        }
    }

    private fun setupAboutSection() {
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

    private fun scheduleAutoUpdate() {
        val hours = prefs.autoUpdateIntervalHours.toLong()
        val request = PeriodicWorkRequestBuilder<AutoUpdateWorker>(hours, TimeUnit.HOURS).build()
        WorkManager.getInstance(requireContext()).enqueueUniquePeriodicWork(
            AutoUpdateWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
            request
        )
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

    private fun cancelAutoUpdate() {
        WorkManager.getInstance(requireContext()).cancelUniqueWork(AutoUpdateWorker.WORK_NAME)
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
                info == null -> binding.tvUpdateStatus.text = "檢查失敗"
                info.isNewer -> {
                    binding.tvUpdateStatus.text = "有新版本"
                    showUpdateAvailable(info)
                }
                else -> binding.tvUpdateStatus.text = "已是最新"
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
                    binding.tvUpdateStatus.text = "已從快取安裝"
                } else {
                    binding.tvUpdateStatus.text = "下載中... 0%"
                    trackDownloadProgress(downloadId)
                }
            }
            .setNegativeButton("稍後", null)
            .show()
    }

    private fun trackDownloadProgress(downloadId: Long) {
        viewLifecycleOwner.lifecycleScope.launch {
            while (isAdded) {
                val progress = withContext(Dispatchers.IO) {
                    UpdateChecker.queryProgress(requireContext(), downloadId)
                }
                when {
                    progress == 100 -> {
                        binding.tvUpdateStatus.text = "下載完成"
                        break
                    }
                    progress < 0 -> {
                        binding.tvUpdateStatus.text = "下載失敗"
                        break
                    }
                    else -> binding.tvUpdateStatus.text = "下載中... $progress%"
                }
                delay(500)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

package com.rendy.classnote.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rendy.classnote.BuildConfig
import com.rendy.classnote.R
import com.rendy.classnote.data.UpdateChecker
import com.rendy.classnote.databinding.FragmentSettingsBinding
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupMenuRows()
        setupAboutSection()
        autoCheckUpdate()
    }

    private fun setupMenuRows() {
        binding.cardMenuAlarm.setOnClickListener {
            findNavController().navigate(R.id.actionSettingsToAlarmPerm)
        }

        binding.cardMenuBackupSync.setOnClickListener {
            findNavController().navigate(R.id.actionSettingsToBackupSync)
        }

        binding.cardMenuWeather.setOnClickListener {
            findNavController().navigate(R.id.actionSettingsToWeatherNotif)
        }

        binding.cardMenuAiSettings.setOnClickListener {
            findNavController().navigate(R.id.actionSettingsToAiSettings)
        }

        binding.cardMenuNotifListener.setOnClickListener {
            findNavController().navigate(R.id.actionSettingsToNotifListener)
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
                binding.tvUpdateStatus.text = "下載中..."
                UpdateChecker.downloadAndInstall(requireContext(), info.apkUrl, info.tagName)
            }
            .setNegativeButton("稍後", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

package com.rendy.classnote.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rendy.classnote.data.DownloadResult
import com.rendy.classnote.data.FeatureDownloader
import com.rendy.classnote.data.FeatureManager
import com.rendy.classnote.databinding.FragmentFeatureModuleBinding
import kotlinx.coroutines.launch

class FeatureModuleSheet : Fragment() {

    private var _binding: FragmentFeatureModuleBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = FragmentFeatureModuleBinding.inflate(inflater, container, false)
        .also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        refreshStatus()
        setupButtons()
    }

    private fun refreshStatus() {
        val ctx = requireContext()
        val googleInstalled = FeatureManager.isDownloaded(ctx, "google")
        val msInstalled = FeatureManager.isDownloaded(ctx, "microsoft")
        val aiInstalled = FeatureManager.isDownloaded(ctx, "ai")
        val weatherInstalled = FeatureManager.isDownloaded(ctx, "weather")

        binding.tvGoogleStatus.text = if (googleInstalled) "已安裝" else "未安裝"
        binding.tvMicrosoftStatus.text = if (msInstalled) "已安裝" else "未安裝"
        binding.tvAiStatus.text = if (aiInstalled) "已安裝" else "未安裝"
        binding.tvWeatherStatus.text = if (weatherInstalled) "已安裝" else "未安裝"
        binding.btnGoogleDownload.text = if (googleInstalled) "刪除" else "下載"
        binding.btnMicrosoftDownload.text = if (msInstalled) "刪除" else "下載"
        binding.btnAiDownload.text = if (aiInstalled) "刪除" else "下載"
        binding.btnWeatherDownload.text = if (weatherInstalled) "刪除" else "下載"
        binding.btnGoogleUpdate.visibility = if (googleInstalled) View.VISIBLE else View.GONE
        binding.btnMicrosoftUpdate.visibility = if (msInstalled) View.VISIBLE else View.GONE
        binding.btnAiUpdate.visibility = if (aiInstalled) View.VISIBLE else View.GONE
        binding.btnWeatherUpdate.visibility = if (weatherInstalled) View.VISIBLE else View.GONE
    }

    private fun setupButtons() {
        binding.btnGoogleDownload.setOnClickListener {
            val ctx = requireContext()
            if (FeatureManager.isDownloaded(ctx, "google")) {
                confirmDelete("Google") { FeatureManager.delete(ctx, "google"); refreshStatus() }
            } else {
                downloadFeature("google", "Google 功能模組", binding.tvGoogleProgress)
            }
        }

        binding.btnMicrosoftDownload.setOnClickListener {
            val ctx = requireContext()
            if (FeatureManager.isDownloaded(ctx, "microsoft")) {
                confirmDelete("Microsoft") { FeatureManager.delete(ctx, "microsoft"); refreshStatus() }
            } else {
                downloadFeature("microsoft", "Microsoft 功能模組", binding.tvMicrosoftProgress)
            }
        }

        binding.btnAiDownload.setOnClickListener {
            val ctx = requireContext()
            if (FeatureManager.isDownloaded(ctx, "ai")) {
                confirmDelete("AI 功能") { FeatureManager.delete(ctx, "ai"); refreshStatus() }
            } else {
                downloadFeature("ai", "AI 功能模組", binding.tvAiProgress)
            }
        }

        binding.btnWeatherDownload.setOnClickListener {
            val ctx = requireContext()
            if (FeatureManager.isDownloaded(ctx, "weather")) {
                confirmDelete("天氣") { FeatureManager.delete(ctx, "weather"); refreshStatus() }
            } else {
                downloadFeature("weather", "天氣模組", binding.tvWeatherProgress)
            }
        }

        binding.btnGoogleUpdate.setOnClickListener {
            updateSingleModule("google", "Google 功能模組", binding.tvGoogleProgress)
        }
        binding.btnMicrosoftUpdate.setOnClickListener {
            updateSingleModule("microsoft", "Microsoft 功能模組", binding.tvMicrosoftProgress)
        }
        binding.btnAiUpdate.setOnClickListener {
            updateSingleModule("ai", "AI 功能模組", binding.tvAiProgress)
        }
        binding.btnWeatherUpdate.setOnClickListener {
            updateSingleModule("weather", "天氣模組", binding.tvWeatherProgress)
        }

        binding.btnCheckUpdates.setOnClickListener {
            checkUpdates()
        }
    }

    private fun downloadFeature(featureId: String, displayName: String, progressView: TextView) {
        val ctx = requireContext()
        setAllButtonsEnabled(false)
        progressView.visibility = View.VISIBLE
        progressView.text = "正在取得功能清單…"

        viewLifecycleOwner.lifecycleScope.launch {
            val manifest = FeatureDownloader.fetchManifest()
            val info = manifest.find { it.id == featureId }
            if (info == null) {
                Toast.makeText(ctx, "找不到 $displayName 的下載資訊", Toast.LENGTH_LONG).show()
                resetButtons()
                return@launch
            }

            val sizeMb = "%.1f MB".format(info.sizeBytes / 1_048_576.0)
            progressView.text = "下載中（$sizeMb）…"

            val versionCode = ctx.packageManager.getPackageInfo(ctx.packageName, 0).longVersionCode.toInt()
            when (val result = FeatureDownloader.download(ctx, info, versionCode)) {
                is DownloadResult.Success -> {
                    Toast.makeText(ctx, "$displayName 安裝成功", Toast.LENGTH_SHORT).show()
                }
                is DownloadResult.AlreadyUpToDate -> {
                    Toast.makeText(ctx, "$displayName 已是最新版本", Toast.LENGTH_SHORT).show()
                }
                is DownloadResult.VersionTooOld -> {
                    Toast.makeText(ctx, "請先更新 App 再安裝此功能模組", Toast.LENGTH_LONG).show()
                }
                is DownloadResult.Error -> {
                    Toast.makeText(ctx, "下載失敗：${result.message}", Toast.LENGTH_LONG).show()
                }
            }
            resetButtons()
        }
    }

    private fun updateSingleModule(featureId: String, displayName: String, progressView: TextView) {
        val ctx = requireContext()
        setAllButtonsEnabled(false)
        progressView.visibility = View.VISIBLE
        progressView.text = "更新中…"

        viewLifecycleOwner.lifecycleScope.launch {
            val manifest = FeatureDownloader.fetchManifest()
            val info = manifest.find { it.id == featureId }
            if (info == null) {
                Toast.makeText(ctx, "無法取得 $displayName 資訊", Toast.LENGTH_LONG).show()
                resetButtons()
                return@launch
            }
            val versionCode = ctx.packageManager.getPackageInfo(ctx.packageName, 0).longVersionCode.toInt()
            when (val result = FeatureDownloader.download(ctx, info, versionCode)) {
                is DownloadResult.Success -> Toast.makeText(ctx, "$displayName 已更新", Toast.LENGTH_SHORT).show()
                is DownloadResult.AlreadyUpToDate -> Toast.makeText(ctx, "$displayName 已是最新版本", Toast.LENGTH_SHORT).show()
                is DownloadResult.VersionTooOld -> Toast.makeText(ctx, "請先更新 App 再安裝此功能模組", Toast.LENGTH_LONG).show()
                is DownloadResult.Error -> Toast.makeText(ctx, "更新失敗：${result.message}", Toast.LENGTH_LONG).show()
            }
            resetButtons()
        }
    }

    private fun checkUpdates() {
        val ctx = requireContext()
        setAllButtonsEnabled(false)

        val progressMap = mapOf(
            "google" to binding.tvGoogleProgress,
            "microsoft" to binding.tvMicrosoftProgress,
            "ai" to binding.tvAiProgress,
            "weather" to binding.tvWeatherProgress
        )
        progressMap.values.forEach { it.visibility = View.GONE }

        viewLifecycleOwner.lifecycleScope.launch {
            val manifest = FeatureDownloader.fetchManifest()
            if (manifest.isEmpty()) {
                Toast.makeText(ctx, "無法取得功能清單，請檢查網路", Toast.LENGTH_LONG).show()
                resetButtons()
                return@launch
            }

            val versionCode = ctx.packageManager.getPackageInfo(ctx.packageName, 0).longVersionCode.toInt()
            var updated = 0
            for (info in manifest) {
                if (!FeatureManager.isDownloaded(ctx, info.id)) continue
                val progressView = progressMap[info.id]
                progressView?.visibility = View.VISIBLE
                progressView?.text = "更新中…"
                when (FeatureDownloader.download(ctx, info, versionCode)) {
                    is DownloadResult.Success -> updated++
                    else -> {}
                }
                progressView?.visibility = View.GONE
            }

            val msg = if (updated > 0) "已更新 $updated 個功能模組" else "所有已安裝模組均為最新版本"
            Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
            resetButtons()
        }
    }

    private fun confirmDelete(name: String, onConfirm: () -> Unit) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("刪除 $name 功能模組")
            .setMessage("刪除後 ${name} 同步功能將無法使用，可隨時重新下載。")
            .setPositiveButton("刪除") { _, _ -> onConfirm() }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun setAllButtonsEnabled(enabled: Boolean) {
        binding.btnGoogleDownload.isEnabled = enabled
        binding.btnMicrosoftDownload.isEnabled = enabled
        binding.btnAiDownload.isEnabled = enabled
        binding.btnWeatherDownload.isEnabled = enabled
        binding.btnGoogleUpdate.isEnabled = enabled
        binding.btnMicrosoftUpdate.isEnabled = enabled
        binding.btnAiUpdate.isEnabled = enabled
        binding.btnWeatherUpdate.isEnabled = enabled
        binding.btnCheckUpdates.isEnabled = enabled
    }

    private fun resetButtons() {
        setAllButtonsEnabled(true)
        binding.tvGoogleProgress.visibility = View.GONE
        binding.tvMicrosoftProgress.visibility = View.GONE
        binding.tvAiProgress.visibility = View.GONE
        binding.tvWeatherProgress.visibility = View.GONE
        refreshStatus()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

package com.rendy.classnote.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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

        binding.tvGoogleStatus.text = if (googleInstalled) "已安裝" else "未安裝"
        binding.tvMicrosoftStatus.text = if (msInstalled) "已安裝" else "未安裝"
        binding.btnGoogleDownload.text = if (googleInstalled) "刪除" else "下載"
        binding.btnMicrosoftDownload.text = if (msInstalled) "刪除" else "下載"
    }

    private fun setupButtons() {
        binding.btnGoogleDownload.setOnClickListener {
            val ctx = requireContext()
            if (FeatureManager.isDownloaded(ctx, "google")) {
                confirmDelete("Google") { FeatureManager.delete(ctx, "google"); refreshStatus() }
            } else {
                downloadFeature("google", "Google 功能模組")
            }
        }

        binding.btnMicrosoftDownload.setOnClickListener {
            val ctx = requireContext()
            if (FeatureManager.isDownloaded(ctx, "microsoft")) {
                confirmDelete("Microsoft") { FeatureManager.delete(ctx, "microsoft"); refreshStatus() }
            } else {
                downloadFeature("microsoft", "Microsoft 功能模組")
            }
        }

        binding.btnCheckUpdates.setOnClickListener {
            checkUpdates()
        }
    }

    private fun downloadFeature(featureId: String, displayName: String) {
        val ctx = requireContext()
        binding.btnGoogleDownload.isEnabled = false
        binding.btnMicrosoftDownload.isEnabled = false
        binding.tvDownloadProgress.visibility = View.VISIBLE
        binding.tvDownloadProgress.text = "正在取得功能清單…"

        viewLifecycleOwner.lifecycleScope.launch {
            val manifest = FeatureDownloader.fetchManifest()
            val info = manifest.find { it.id == featureId }
            if (info == null) {
                Toast.makeText(ctx, "找不到 $displayName 的下載資訊", Toast.LENGTH_LONG).show()
                resetButtons()
                return@launch
            }

            val sizeMb = "%.1f MB".format(info.sizeBytes / 1_048_576.0)
            binding.tvDownloadProgress.text = "下載中（$sizeMb）…"

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

    private fun checkUpdates() {
        val ctx = requireContext()
        binding.btnCheckUpdates.isEnabled = false
        binding.tvDownloadProgress.visibility = View.VISIBLE
        binding.tvDownloadProgress.text = "檢查更新中…"

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
                when (FeatureDownloader.download(ctx, info, versionCode)) {
                    is DownloadResult.Success -> updated++
                    else -> {}
                }
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

    private fun resetButtons() {
        binding.btnGoogleDownload.isEnabled = true
        binding.btnMicrosoftDownload.isEnabled = true
        binding.btnCheckUpdates.isEnabled = true
        binding.tvDownloadProgress.visibility = View.GONE
        refreshStatus()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

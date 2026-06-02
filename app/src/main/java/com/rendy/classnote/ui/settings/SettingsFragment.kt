package com.rendy.classnote.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rendy.classnote.BuildConfig
import com.rendy.classnote.R
import com.rendy.classnote.data.AppPreferences
import com.rendy.classnote.data.FeatureManager
import com.rendy.classnote.databinding.FragmentSettingsBinding

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
        refreshAiCard()
        refreshWeatherCard()
        binding.tvAboutVersionInline.text = BuildConfig.VERSION_NAME
        refreshLanguageSubtitle()
    }

    override fun onResume() {
        super.onResume()
        refreshAiCard()
        refreshWeatherCard()
        refreshLanguageSubtitle()
    }

    private fun refreshAiCard() {
        val installed = FeatureManager.isDownloaded(requireContext(), "ai")
        binding.cardMenuAiSettings.alpha = if (installed) 1f else 0.4f
        binding.cardMenuAiSettings.isEnabled = installed
        binding.cardMenuAiSettings.isClickable = installed
        binding.tvAiSettingsSubtitle.text = if (installed) "api key、通知辨識" else "未安裝 AI 功能模組"
    }

    private fun refreshWeatherCard() {
        val installed = FeatureManager.isDownloaded(requireContext(), "weather")
        binding.cardMenuWeather.alpha = if (installed) 1f else 0.4f
        binding.cardMenuWeather.isEnabled = installed
        binding.cardMenuWeather.isClickable = installed
        binding.tvWeatherSettingsSubtitle.text = if (installed) "每日天氣推播、時間與地區設定" else "未安裝天氣模組"
    }

    private fun refreshLanguageSubtitle() {
        binding.tvLanguageSubtitle.text = currentLanguageLabel()
    }

    private fun currentLanguageLabel(): String {
        val tag = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        return when {
            tag.startsWith("zh-TW") || tag.startsWith("zh-Hant") -> "繁體中文"
            tag.startsWith("zh-CN") || tag.startsWith("zh-Hans") || tag.startsWith("zh") -> "简体中文"
            tag.startsWith("en") -> "English"
            tag.isEmpty() -> "繁體中文"
            else -> tag
        }
    }

    private fun setupMenuRows() {
        binding.cardMenuAlarm.setOnClickListener {
            findNavController().navigate(R.id.actionSettingsToAlarmPerm)
        }

        binding.cardMenuPermissions.setOnClickListener {
            findNavController().navigate(R.id.actionSettingsToPermissions)
        }

        binding.cardMenuLanguage.setOnClickListener {
            showLanguagePicker()
        }

        binding.cardMenuSync.setOnClickListener {
            findNavController().navigate(R.id.actionSettingsToSync)
        }

        binding.cardMenuWeather.setOnClickListener {
            findNavController().navigate(R.id.actionSettingsToWeatherNotif)
        }

        binding.cardMenuAiSettings.setOnClickListener {
            if (FeatureManager.isDownloaded(requireContext(), "ai")) {
                findNavController().navigate(R.id.actionSettingsToAiSettings)
            } else {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("需要 AI 功能模組")
                    .setMessage("請先至「功能模組管理」下載 AI 功能模組。")
                    .setPositiveButton("前往下載") { _, _ ->
                        findNavController().navigate(R.id.actionSettingsToFeatureModules)
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
        }

        binding.cardMenuApiLog.setOnClickListener {
            findNavController().navigate(R.id.actionSettingsToApiLog)
        }

        binding.cardMenuErrorLog.setOnClickListener {
            findNavController().navigate(R.id.actionSettingsToErrorLog)
        }

        binding.cardMenuFeatureModules.setOnClickListener {
            findNavController().navigate(R.id.actionSettingsToFeatureModules)
        }

        binding.cardMenuAbout.setOnClickListener {
            findNavController().navigate(R.id.actionSettingsToAbout)
        }
    }

    private fun showLanguagePicker() {
        val options = arrayOf("繁體中文", "简体中文", "English")
        val tags = arrayOf("zh-TW", "zh-CN", "en")
        val current = currentLanguageLabel()
        val checkedIndex = options.indexOfFirst { it == current }.coerceAtLeast(0)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("選擇語言 / Select Language")
            .setSingleChoiceItems(options, checkedIndex) { dialog, which ->
                dialog.dismiss()
                val locales = LocaleListCompat.forLanguageTags(tags[which])
                AppCompatDelegate.setApplicationLocales(locales)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

package com.rendy.classnote.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.rendy.classnote.BuildConfig
import com.rendy.classnote.R
import com.rendy.classnote.databinding.FragmentSettingsBinding

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
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

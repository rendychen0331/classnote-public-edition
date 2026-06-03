package com.rendy.classnote.ui.more

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.rendy.classnote.R
import com.rendy.classnote.data.FeatureManager
import com.rendy.classnote.databinding.FragmentMoreBinding

class MoreFragment : Fragment() {

    private var _binding: FragmentMoreBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMoreBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupCards()
    }

    override fun onResume() {
        super.onResume()
        refreshWeatherCard()
    }

    private fun setupCards() {
        binding.cardMoreFormula.setOnClickListener {
            findNavController().navigate(R.id.actionMoreToFormulaList)
        }
        binding.cardMoreSettings.setOnClickListener {
            findNavController().navigate(R.id.actionMoreToSettings)
        }
        binding.cardMoreWeather.setOnClickListener {
            if (FeatureManager.isDownloaded(requireContext(), "weather")) {
                findNavController().navigate(R.id.actionMoreToWeather)
            }
        }
        refreshWeatherCard()
    }

    private fun refreshWeatherCard() {
        val installed = FeatureManager.isDownloaded(requireContext(), "weather")
        binding.cardMoreWeather.alpha = if (installed) 1f else 0.4f
        binding.cardMoreWeather.isClickable = installed
        binding.tvMoreWeatherSubtitle.text =
            if (installed) "今日天氣預報" else "未安裝天氣模組"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

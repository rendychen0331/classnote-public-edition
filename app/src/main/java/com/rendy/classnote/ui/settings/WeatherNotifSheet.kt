package com.rendy.classnote.ui.settings

import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rendy.classnote.ClassNoteApplication
import com.rendy.classnote.R
import com.rendy.classnote.data.AppPreferences
import com.rendy.classnote.data.WeatherPreferences
import com.rendy.classnote.databinding.SheetWeatherNotifBinding
import com.rendy.classnote.notification.WeatherNotificationScheduler
import com.rendy.classnote.ui.BiometricHelper

class WeatherNotifSheet : Fragment() {

    private var _binding: SheetWeatherNotifBinding? = null
    private val binding get() = _binding!!

    private lateinit var prefs: AppPreferences
    private lateinit var weatherPrefs: WeatherPreferences

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = SheetWeatherNotifBinding.inflate(inflater, container, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = (requireActivity().application as ClassNoteApplication).appPreferences
        weatherPrefs = WeatherPreferences(requireContext())
        setupCwaApiKey()
        setupProviderSelector()
        setupWeatherNotificationSection()
    }

    private fun setupCwaApiKey() {
        val savedCwaKey = prefs.cwaApiKey
        if (savedCwaKey.isNotBlank()) binding.etCwaApiKey.setText(savedCwaKey)
        var cwaKeyVisible = false
        binding.tilCwaApiKey.setEndIconOnClickListener {
            if (cwaKeyVisible) {
                cwaKeyVisible = false
                binding.etCwaApiKey.inputType =
                    android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                binding.tilCwaApiKey.setEndIconDrawable(R.drawable.ic_visibility_off)
                binding.etCwaApiKey.setSelection(binding.etCwaApiKey.text?.length ?: 0)
            } else {
                BiometricHelper.authenticate(
                    fragment = this,
                    title = getString(R.string.biometric_title_monitor),
                    subtitle = getString(R.string.biometric_subtitle_apikey),
                    onSuccess = {
                        cwaKeyVisible = true
                        binding.etCwaApiKey.inputType =
                            android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                        binding.tilCwaApiKey.setEndIconDrawable(R.drawable.ic_visibility)
                        binding.etCwaApiKey.setSelection(binding.etCwaApiKey.text?.length ?: 0)
                    }
                )
            }
        }
        binding.btnSaveCwaApiKey.setOnClickListener {
            BiometricHelper.authenticate(
                fragment = this,
                title = getString(R.string.biometric_title_monitor),
                subtitle = getString(R.string.biometric_subtitle_apikey),
                onSuccess = {
                    val key = binding.etCwaApiKey.text?.toString()?.trim() ?: ""
                    prefs.cwaApiKey = key
                    Toast.makeText(requireContext(), getString(R.string.settings_weather_cwa_key_saved), Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    private fun setupProviderSelector() {
        val provider = weatherPrefs.weatherProvider
        when (provider) {
            "open-meteo" -> binding.chipOpenMeteo.isChecked = true
            "weatherapi" -> binding.chipWeatherApi.isChecked = true
            else -> binding.chipCwa.isChecked = true
        }
        updateProviderSections(provider)

        binding.chipGroupProvider.setOnCheckedStateChangeListener { _, checkedIds ->
            val selected = when {
                checkedIds.contains(R.id.chipOpenMeteo) -> "open-meteo"
                checkedIds.contains(R.id.chipWeatherApi) -> "weatherapi"
                else -> "cwa"
            }
            weatherPrefs.weatherProvider = selected
            updateProviderSections(selected)
        }

        // WeatherAPI.com key setup
        val savedKey = prefs.weatherApiComKey
        if (savedKey.isNotBlank()) binding.etWeatherApiKey.setText(savedKey)
        var keyVisible = false
        binding.tilWeatherApiKey.setEndIconOnClickListener {
            if (keyVisible) {
                keyVisible = false
                binding.etWeatherApiKey.inputType =
                    android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                binding.tilWeatherApiKey.setEndIconDrawable(R.drawable.ic_visibility_off)
                binding.etWeatherApiKey.setSelection(binding.etWeatherApiKey.text?.length ?: 0)
            } else {
                BiometricHelper.authenticate(
                    fragment = this,
                    title = getString(R.string.biometric_title_monitor),
                    subtitle = getString(R.string.biometric_subtitle_apikey),
                    onSuccess = {
                        keyVisible = true
                        binding.etWeatherApiKey.inputType =
                            android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                        binding.tilWeatherApiKey.setEndIconDrawable(R.drawable.ic_visibility)
                        binding.etWeatherApiKey.setSelection(binding.etWeatherApiKey.text?.length ?: 0)
                    }
                )
            }
        }
        binding.btnSaveWeatherApiKey.setOnClickListener {
            BiometricHelper.authenticate(
                fragment = this,
                title = getString(R.string.biometric_title_monitor),
                subtitle = getString(R.string.biometric_subtitle_apikey),
                onSuccess = {
                    prefs.weatherApiComKey = binding.etWeatherApiKey.text?.toString()?.trim() ?: ""
                    Toast.makeText(requireContext(), "WeatherAPI.com Key 已儲存", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    private fun updateProviderSections(provider: String) {
        binding.cardCwaApiKey.visibility = if (provider == "cwa") View.VISIBLE else View.GONE
        binding.cardWeatherApiKey.visibility = if (provider == "weatherapi") View.VISIBLE else View.GONE
    }

    private fun setupWeatherNotificationSection() {
        binding.switchWeatherNotif.isChecked = weatherPrefs.weatherNotifEnabled
        updateWeatherNotifSettings()

        binding.switchWeatherNotif.setOnCheckedChangeListener { _, checked ->
            weatherPrefs.weatherNotifEnabled = checked
            updateWeatherNotifSettings()
            WeatherNotificationScheduler.schedule(requireContext())
        }

        binding.rowWeatherNotifTime.setOnClickListener {
            showWeatherTimePicker()
        }

        binding.rowWeatherNotifLocation.setOnClickListener {
            showWeatherLocationPicker()
        }
    }

    private fun updateWeatherNotifSettings() {
        val enabled = weatherPrefs.weatherNotifEnabled
        binding.cardWeatherNotifSettings.visibility = if (enabled) View.VISIBLE else View.GONE
        if (!enabled) return

        val h = weatherPrefs.weatherNotifHour
        val m = weatherPrefs.weatherNotifMinute
        binding.tvWeatherNotifTime.text = String.format("%02d:%02d", h, m)

        val loc = weatherPrefs.weatherNotifLocation
        binding.tvWeatherNotifLocation.text = loc.ifEmpty { getString(R.string.settings_weather_no_location) }
    }

    private fun showWeatherTimePicker() {
        TimePickerDialog(
            requireContext(),
            { _, hour, minute ->
                weatherPrefs.weatherNotifHour = hour
                weatherPrefs.weatherNotifMinute = minute
                updateWeatherNotifSettings()
                WeatherNotificationScheduler.schedule(requireContext())
            },
            weatherPrefs.weatherNotifHour,
            weatherPrefs.weatherNotifMinute,
            true
        ).show()
    }

    private fun showWeatherLocationPicker() {
        val saved = weatherPrefs.savedLocations
        if (saved.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.weather_no_locations), Toast.LENGTH_SHORT).show()
            return
        }
        val locations = saved.toTypedArray()
        val current = locations.indexOfFirst { it == weatherPrefs.weatherNotifLocation }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.settings_weather_notif_location))
            .setSingleChoiceItems(locations, current) { dialog, which ->
                weatherPrefs.weatherNotifLocation = locations[which]
                updateWeatherNotifSettings()
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "WeatherNotifSheet"
    }
}

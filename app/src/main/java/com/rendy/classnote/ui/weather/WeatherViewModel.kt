package com.rendy.classnote.ui.weather

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rendy.classnote.data.AppPreferences
import com.rendy.classnote.data.FeatureManager
import com.rendy.classnote.feature.ForecastItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class WeatherUiState {
    object Loading : WeatherUiState()
    data class Success(val forecasts: List<ForecastItem>) : WeatherUiState()
    data class Error(val message: String) : WeatherUiState()
}

class WeatherViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = AppPreferences(app)
    private val _uiState = MutableStateFlow<WeatherUiState>(WeatherUiState.Loading)
    val uiState: StateFlow<WeatherUiState> = _uiState

    private var currentLocation: String = "臺北市"

    fun loadWeather(locationName: String = currentLocation) {
        currentLocation = locationName
        _uiState.value = WeatherUiState.Loading
        viewModelScope.launch {
            val weather = FeatureManager.getWeather(getApplication())
            if (weather == null) {
                _uiState.value = WeatherUiState.Error("請先下載天氣功能模組")
                return@launch
            }
            val result = weather.fetchForecast(locationName, prefs.cwaApiKey)
            _uiState.value = result.fold(
                onSuccess = { WeatherUiState.Success(it) },
                onFailure = { WeatherUiState.Error(it.message ?: "載入失敗") }
            )
        }
    }
}

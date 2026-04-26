package com.rendy.classnote.ui.weather

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rendy.classnote.data.remote.ForecastItem
import com.rendy.classnote.data.remote.WeatherApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class WeatherUiState {
    object Loading : WeatherUiState()
    data class Success(val forecasts: List<ForecastItem>) : WeatherUiState()
    data class Error(val message: String) : WeatherUiState()
}

class WeatherViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<WeatherUiState>(WeatherUiState.Loading)
    val uiState: StateFlow<WeatherUiState> = _uiState

    private var currentLocation: String = WeatherApi.LOCATIONS.first().displayName

    fun loadWeather(locationName: String = currentLocation) {
        currentLocation = locationName
        _uiState.value = WeatherUiState.Loading
        viewModelScope.launch {
            val result = WeatherApi.fetchForecast(locationName)
            _uiState.value = result.fold(
                onSuccess = { WeatherUiState.Success(it) },
                onFailure = { WeatherUiState.Error(it.message ?: "載入失敗") }
            )
        }
    }
}

package com.rendy.classnote.feature

data class WeatherConfig(
    val provider: String = "cwa",       // "cwa", "open-meteo", "weatherapi"
    val cwaKey: String = "",
    val weatherApiKey: String = ""
)

data class WeatherLocation(
    val displayName: String,
    val apiName: String,
    val datasetId: String,
    val countyName: String = ""
)

data class ForecastItem(
    val timePeriod: String,
    val description: String,
    val tempMin: Int,
    val tempMax: Int,
    val rainProb: Int
)

interface WeatherFeature {
    val locations: List<WeatherLocation>
    val countyNames: List<String>
    fun districtsOf(county: String): List<WeatherLocation>
    suspend fun fetchForecast(displayName: String, apiKey: String): Result<List<ForecastItem>>
    suspend fun fetchForecast(displayName: String, config: WeatherConfig): Result<List<ForecastItem>> =
        fetchForecast(displayName, config.cwaKey)
}

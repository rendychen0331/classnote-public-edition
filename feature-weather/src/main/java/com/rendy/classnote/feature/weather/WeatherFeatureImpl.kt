package com.rendy.classnote.feature.weather

import com.rendy.classnote.feature.ForecastItem
import com.rendy.classnote.feature.WeatherFeature
import com.rendy.classnote.feature.WeatherLocation

internal class WeatherFeatureImpl : WeatherFeature {
    override val locations: List<WeatherLocation> get() = WeatherApiImpl.LOCATIONS
    override val countyNames: List<String> get() = WeatherApiImpl.COUNTY_NAMES
    override fun districtsOf(county: String): List<WeatherLocation> = WeatherApiImpl.districtsOf(county)
    override suspend fun fetchForecast(displayName: String, apiKey: String): Result<List<ForecastItem>> =
        WeatherApiImpl.fetchForecast(displayName, apiKey)
}

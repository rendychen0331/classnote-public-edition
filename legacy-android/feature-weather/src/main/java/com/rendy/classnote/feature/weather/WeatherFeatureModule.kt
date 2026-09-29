package com.rendy.classnote.feature.weather

import com.rendy.classnote.feature.BackupFeature
import com.rendy.classnote.feature.FeatureModule
import com.rendy.classnote.feature.SyncFeature
import com.rendy.classnote.feature.WeatherFeature

class WeatherFeatureModule : FeatureModule {
    override val id: String = "weather"
    override fun sync(): SyncFeature? = null
    override fun backup(): BackupFeature? = null
    override fun weather(): WeatherFeature = WeatherFeatureImpl()
}

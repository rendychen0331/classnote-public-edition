package com.rendy.classnote.feature.ai

import com.rendy.classnote.feature.AiFeature
import com.rendy.classnote.feature.BackupFeature
import com.rendy.classnote.feature.FeatureModule
import com.rendy.classnote.feature.SyncFeature

class AiFeatureModule : FeatureModule {
    override val id: String = "ai"
    override fun sync(): SyncFeature? = null
    override fun backup(): BackupFeature? = null
    override fun ai(): AiFeature = AiFeatureImpl()
}

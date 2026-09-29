package com.rendy.classnote.feature.microsoft

import com.rendy.classnote.feature.AuthFeature
import com.rendy.classnote.feature.BackupFeature
import com.rendy.classnote.feature.FeatureModule
import com.rendy.classnote.feature.SyncFeature

/** Entry point loaded by FeatureManager via DexClassLoader reflection. */
class MicrosoftFeatureModule : FeatureModule {
    override val id = "microsoft"
    override fun sync(): SyncFeature = MicrosoftSyncFeature()
    override fun backup(): BackupFeature = MicrosoftBackupFeature()
    override fun auth(): AuthFeature = MicrosoftAuthFeature()
}

package com.rendy.classnote.feature.google

import com.rendy.classnote.feature.BackupFeature
import com.rendy.classnote.feature.FeatureModule
import com.rendy.classnote.feature.SyncFeature

/** Entry point loaded by FeatureManager via DexClassLoader reflection. */
class GoogleFeatureModule : FeatureModule {
    override val id = "google"
    override fun sync(): SyncFeature = GoogleSyncFeature()
    override fun backup(): BackupFeature = GoogleBackupFeature()
}

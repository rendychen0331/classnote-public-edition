package com.rendy.classnote.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rendy.classnote.ClassNoteApplication
import com.rendy.classnote.R
import com.rendy.classnote.data.AppPreferences
import com.rendy.classnote.data.FeatureManager
import com.rendy.classnote.data.MsTodoSyncWorker
import com.rendy.classnote.data.MicrosoftAuthManager
import com.rendy.classnote.data.OneDriveBackupWorker
import com.rendy.classnote.data.OutlookCalendarSyncWorker
import com.rendy.classnote.data.TeamsAssignmentSyncWorker
import com.rendy.classnote.data.OneNoteSyncWorker
import com.rendy.classnote.databinding.SheetMicrosoftSyncBinding
import com.rendy.classnote.feature.BackupOutcome
import com.rendy.classnote.data.SyncBridgeImpl
import com.rendy.classnote.feature.SyncOutcome
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class MicrosoftSyncSheet : Fragment() {

    private var _binding: SheetMicrosoftSyncBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = SheetMicrosoftSyncBinding.inflate(inflater, container, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val prefs = (requireActivity().application as ClassNoteApplication).appPreferences
        setupOneDriveSection(prefs)
        setupMsTodoSection(prefs)
        setupOutlookCalendarSection(prefs)
        setupTeamsAssignmentSection(prefs)
        setupOneNoteSection(prefs)
    }

    private fun setupOneDriveSection(prefs: AppPreferences) {
        binding.switchOneDriveBackup.isChecked = prefs.oneDriveBackupEnabled
        updateOneDriveSection(prefs)

        binding.switchOneDriveBackup.setOnCheckedChangeListener { _, checked ->
            prefs.oneDriveBackupEnabled = checked
            updateOneDriveSection(prefs)
        }

        binding.ibtnMsSignIn.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val token = MicrosoftAuthManager.signIn(requireContext())
                    if (token == null) {
                        Toast.makeText(requireContext(),
                            getString(R.string.settings_onedrive_sign_in_failed), Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    val msg = e.message ?: getString(R.string.settings_onedrive_sign_in_failed)
                    Toast.makeText(requireContext(), "登入錯誤：$msg", Toast.LENGTH_LONG).show()
                }
                updateOneDriveSection(prefs)
            }
        }

        binding.btnOneDriveSignOut.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                MicrosoftAuthManager.signOut(requireContext())
                updateOneDriveSection(prefs)
            }
        }

        binding.btnOneDriveBackup.setOnClickListener {
            binding.btnOneDriveBackup.isEnabled = false
            Toast.makeText(requireContext(),
                getString(R.string.settings_onedrive_backup_in_progress), Toast.LENGTH_SHORT).show()
            viewLifecycleOwner.lifecycleScope.launch {
                val feature = FeatureManager.getBackup(requireContext(), "microsoft")
                if (feature == null) {
                    binding.btnOneDriveBackup.isEnabled = true
                    Toast.makeText(requireContext(), "Microsoft 功能模組未安裝，請下載", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val bridge = SyncBridgeImpl(requireContext())
                val result = feature.backup(bridge)
                binding.btnOneDriveBackup.isEnabled = true
                when (result) {
                    is BackupOutcome.Success ->
                        Toast.makeText(requireContext(),
                            getString(R.string.settings_onedrive_backup_success), Toast.LENGTH_SHORT).show()
                    is BackupOutcome.AuthRequired -> {
                        if (result.intent != null) {
                            startActivity(result.intent)
                        } else {
                            Toast.makeText(requireContext(), "帳號授權已過期", Toast.LENGTH_SHORT).show()
                        }
                    }
                    is BackupOutcome.Error ->
                        Toast.makeText(requireContext(), result.message, Toast.LENGTH_LONG).show()
                }
            }
        }

        binding.btnOneDriveRestore.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.settings_onedrive_restore_confirm_title))
                .setMessage(getString(R.string.settings_onedrive_restore_confirm_msg))
                .setPositiveButton("還原") { _, _ ->
                    binding.btnOneDriveRestore.isEnabled = false
                    viewLifecycleOwner.lifecycleScope.launch {
                        val feature = FeatureManager.getBackup(requireContext(), "microsoft")
                        if (feature == null) {
                            binding.btnOneDriveRestore.isEnabled = true
                            Toast.makeText(requireContext(), "Microsoft 功能模組未安裝，請下載", Toast.LENGTH_SHORT).show()
                            return@launch
                        }
                        val bridge = SyncBridgeImpl(requireContext())
                        val result = feature.restore(bridge)
                        binding.btnOneDriveRestore.isEnabled = true
                        when (result) {
                            is BackupOutcome.Success ->
                                Toast.makeText(requireContext(),
                                    getString(R.string.settings_onedrive_restore_success), Toast.LENGTH_LONG).show()
                            is BackupOutcome.AuthRequired -> {
                                if (result.intent != null) {
                                    startActivity(result.intent)
                                } else {
                                    Toast.makeText(requireContext(), "帳號授權已過期", Toast.LENGTH_SHORT).show()
                                }
                            }
                            is BackupOutcome.Error ->
                                Toast.makeText(requireContext(), result.message, Toast.LENGTH_LONG).show()
                        }
                    }
                }
                .setNegativeButton(getString(R.string.cancel), null)
                .show()
        }

        binding.cardOneDriveNetwork.setOnClickListener { showOneDriveNetworkDialog(prefs) }
    }

    private fun setupOneDriveAutoBackupCard(prefs: AppPreferences) {
        binding.switchOneDriveAutoBackup.isChecked = prefs.autoOneDriveBackupEnabled
        binding.tvOneDriveAutoBackupInterval.text = oneDriveIntervalLabel(prefs.autoOneDriveBackupIntervalHours)
        binding.rowOneDriveAutoBackupInterval.visibility =
            if (prefs.autoOneDriveBackupEnabled) View.VISIBLE else View.GONE
        binding.rowOneDriveAutoBackupInterval.setOnClickListener { showOneDriveIntervalDialog(prefs) }

        binding.switchOneDriveAutoBackup.setOnCheckedChangeListener { _, checked ->
            prefs.autoOneDriveBackupEnabled = checked
            binding.rowOneDriveAutoBackupInterval.visibility = if (checked) View.VISIBLE else View.GONE
            if (checked) scheduleOneDriveAutoBackup(prefs) else cancelOneDriveAutoBackup()
        }
    }

    private fun updateOneDriveSection(prefs: AppPreferences) {
        val enabled = prefs.oneDriveBackupEnabled
        binding.cardOneDriveAccount.visibility = if (enabled) View.VISIBLE else View.GONE
        if (!enabled) {
            binding.cardOneDriveActions.visibility = View.GONE
            binding.cardOneDriveNetwork.visibility = View.GONE
            binding.cardOneDriveAutoBackup.visibility = View.GONE
            return
        }

        // 用快取 email 立即渲染，避免等 MSAL 初始化造成畫面閃跳
        applyOneDriveLoginState(prefs.msAccountEmail, prefs)

        // 背景確認 MSAL 實際帳號
        viewLifecycleOwner.lifecycleScope.launch {
            val email = MicrosoftAuthManager.getAccountEmail(requireContext())
            applyOneDriveLoginState(email, prefs)
        }
    }

    private fun applyOneDriveLoginState(email: String?, prefs: AppPreferences) {
        binding.tvOneDriveAccountEmail.text = email
            ?: getString(R.string.settings_onedrive_not_signed_in)
        binding.ibtnMsSignIn.visibility = if (email != null) View.GONE else View.VISIBLE
        binding.btnOneDriveSignOut.visibility = if (email != null) View.VISIBLE else View.GONE
        if (email != null) {
            binding.cardOneDriveActions.visibility = View.VISIBLE
            binding.cardOneDriveNetwork.visibility = View.VISIBLE
            binding.cardOneDriveAutoBackup.visibility = View.VISIBLE
            binding.tvOneDriveLastBackup.visibility = View.VISIBLE
            updateOneDriveNetworkLabel(prefs)
            setupOneDriveAutoBackupCard(prefs)
        } else {
            binding.cardOneDriveActions.visibility = View.GONE
            binding.cardOneDriveNetwork.visibility = View.GONE
            binding.cardOneDriveAutoBackup.visibility = View.GONE
            binding.tvOneDriveLastBackup.visibility = View.GONE
        }
    }

    private fun updateOneDriveNetworkLabel(prefs: AppPreferences) {
        binding.tvOneDriveNetworkValue.text = when (prefs.oneDriveBackupNetworkType) {
            AppPreferences.NETWORK_WIFI -> getString(R.string.backup_network_wifi)
            AppPreferences.NETWORK_MOBILE -> getString(R.string.backup_network_mobile)
            else -> getString(R.string.backup_network_any)
        }
    }

    private fun showOneDriveNetworkDialog(prefs: AppPreferences) {
        val keys = listOf(
            AppPreferences.NETWORK_ANY,
            AppPreferences.NETWORK_WIFI,
            AppPreferences.NETWORK_MOBILE
        )
        val labels = arrayOf(
            getString(R.string.backup_network_any),
            getString(R.string.backup_network_wifi),
            getString(R.string.backup_network_mobile)
        )
        val current = keys.indexOfFirst { it == prefs.oneDriveBackupNetworkType }.coerceAtLeast(0)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.backup_network_title))
            .setSingleChoiceItems(labels, current) { dialog, which ->
                prefs.oneDriveBackupNetworkType = keys[which]
                updateOneDriveNetworkLabel(prefs)
                if (prefs.autoOneDriveBackupEnabled) scheduleOneDriveAutoBackup(prefs)
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun oneDriveIntervalLabel(hours: Int): String = when (hours) {
        6 -> getString(R.string.settings_auto_backup_interval_6h)
        12 -> getString(R.string.settings_auto_backup_interval_12h)
        72 -> getString(R.string.settings_auto_backup_interval_72h)
        else -> getString(R.string.settings_auto_backup_interval_24h)
    }

    private fun showOneDriveIntervalDialog(prefs: AppPreferences) {
        val options = intArrayOf(6, 12, 24, 72)
        val labels = options.map { oneDriveIntervalLabel(it) }.toTypedArray()
        val current = options.indexOfFirst { it == prefs.autoOneDriveBackupIntervalHours }.takeIf { it >= 0 } ?: 2
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.settings_auto_backup_interval))
            .setSingleChoiceItems(labels, current) { dialog, which ->
                prefs.autoOneDriveBackupIntervalHours = options[which]
                binding.tvOneDriveAutoBackupInterval.text = labels[which]
                if (prefs.autoOneDriveBackupEnabled) scheduleOneDriveAutoBackup(prefs)
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun scheduleOneDriveAutoBackup(prefs: AppPreferences) {
        val networkType = when (prefs.oneDriveBackupNetworkType) {
            AppPreferences.NETWORK_WIFI -> NetworkType.UNMETERED
            AppPreferences.NETWORK_MOBILE -> NetworkType.METERED
            else -> NetworkType.CONNECTED
        }
        val request = PeriodicWorkRequestBuilder<OneDriveBackupWorker>(
            prefs.autoOneDriveBackupIntervalHours.toLong(), TimeUnit.HOURS
        ).setConstraints(
            Constraints.Builder().setRequiredNetworkType(networkType).build()
        ).build()
        WorkManager.getInstance(requireContext()).enqueueUniquePeriodicWork(
            "onedrive_auto_backup", ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE, request
        )
    }

    private fun cancelOneDriveAutoBackup() {
        WorkManager.getInstance(requireContext()).cancelUniqueWork("onedrive_auto_backup")
    }

    private fun setupMsTodoSection(prefs: AppPreferences) {
        binding.switchMsTodoSync.isChecked = prefs.msTodoSyncEnabled
        updateMsTodoSection(prefs)

        binding.switchMsTodoSync.setOnCheckedChangeListener { _, checked ->
            prefs.msTodoSyncEnabled = checked
            updateMsTodoSection(prefs)
        }

        binding.btnMsTodoSyncNow.setOnClickListener {
            binding.btnMsTodoSyncNow.isEnabled = false
            viewLifecycleOwner.lifecycleScope.launch {
                val feature = FeatureManager.getSync(requireContext(), "microsoft")
                if (feature == null) {
                    binding.btnMsTodoSyncNow.isEnabled = true
                    Toast.makeText(requireContext(), "Microsoft 功能模組未安裝，請下載", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val bridge = SyncBridgeImpl(requireContext())
                val result = feature.sync("mstodo", bridge)
                binding.btnMsTodoSyncNow.isEnabled = true
                val summary = when (result) {
                    is SyncOutcome.Success ->
                        getString(R.string.settings_mstodo_sync_result, result.imported, result.skipped)
                    is SyncOutcome.AuthRequired -> {
                        Toast.makeText(requireContext(), "Microsoft 帳號授權已過期，請重新登入", Toast.LENGTH_SHORT).show()
                        getString(R.string.settings_onedrive_sign_in_failed)
                    }
                    is SyncOutcome.Error -> result.message
                    is SyncOutcome.NoPermission -> getString(R.string.settings_onedrive_sign_in_failed)
                }
                prefs.lastMsTodoSyncSummary = summary
                binding.tvMsTodoSyncStatus.text = summary
            }
        }

        binding.switchMsTodoAutoSync.isChecked = prefs.autoMsTodoSyncEnabled
        binding.tvAutoMsTodoSyncInterval.text =
            getString(R.string.settings_auto_mstodo_sync_interval, prefs.autoMsTodoSyncIntervalHours)
        binding.switchMsTodoAutoSync.setOnCheckedChangeListener { _, checked ->
            prefs.autoMsTodoSyncEnabled = checked
            if (checked) scheduleMsTodoSync(prefs) else cancelMsTodoSync()
        }
    }

    private fun updateMsTodoSection(prefs: AppPreferences) {
        val enabled = prefs.msTodoSyncEnabled
        binding.cardMsTodoSync.visibility = if (enabled) View.VISIBLE else View.GONE
        if (enabled) {
            binding.tvMsTodoSyncStatus.text = prefs.lastMsTodoSyncSummary.ifEmpty {
                getString(R.string.settings_mstodo_no_sync)
            }
        }
    }

    private fun scheduleMsTodoSync(prefs: AppPreferences) {
        val request = PeriodicWorkRequestBuilder<MsTodoSyncWorker>(
            prefs.autoMsTodoSyncIntervalHours.toLong(), TimeUnit.HOURS
        ).setConstraints(
            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        ).build()
        WorkManager.getInstance(requireContext()).enqueueUniquePeriodicWork(
            "mstodo_auto_sync", ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE, request
        )
    }

    private fun cancelMsTodoSync() {
        WorkManager.getInstance(requireContext()).cancelUniqueWork("mstodo_auto_sync")
    }

    private fun setupOutlookCalendarSection(prefs: AppPreferences) {
        binding.switchOutlookCalendarSync.isChecked = prefs.outlookCalendarSyncEnabled
        updateOutlookCalendarSection(prefs)

        binding.switchOutlookCalendarSync.setOnCheckedChangeListener { _, checked ->
            prefs.outlookCalendarSyncEnabled = checked
            updateOutlookCalendarSection(prefs)
        }

        binding.btnOutlookCalendarSyncNow.setOnClickListener {
            binding.btnOutlookCalendarSyncNow.isEnabled = false
            viewLifecycleOwner.lifecycleScope.launch {
                val feature = FeatureManager.getSync(requireContext(), "microsoft")
                if (feature == null) {
                    binding.btnOutlookCalendarSyncNow.isEnabled = true
                    Toast.makeText(requireContext(), "Microsoft 功能模組未安裝，請下載", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val bridge = SyncBridgeImpl(requireContext())
                val result = feature.sync("outlook_calendar", bridge)
                binding.btnOutlookCalendarSyncNow.isEnabled = true
                val summary = when (result) {
                    is SyncOutcome.Success ->
                        getString(R.string.settings_outlook_calendar_sync_result, result.imported, result.skipped)
                    is SyncOutcome.AuthRequired -> {
                        Toast.makeText(requireContext(), "Microsoft 帳號授權已過期，請重新登入", Toast.LENGTH_SHORT).show()
                        getString(R.string.settings_onedrive_sign_in_failed)
                    }
                    is SyncOutcome.Error -> result.message
                    is SyncOutcome.NoPermission -> getString(R.string.settings_onedrive_sign_in_failed)
                }
                prefs.lastOutlookCalendarSyncSummary = summary
                binding.tvOutlookCalendarSyncStatus.text = summary
            }
        }

        binding.switchOutlookCalendarAutoSync.isChecked = prefs.autoOutlookCalendarSyncEnabled
        binding.tvAutoOutlookCalendarSyncInterval.text =
            getString(R.string.settings_auto_outlook_calendar_sync_interval, prefs.autoOutlookCalendarSyncIntervalHours)
        binding.switchOutlookCalendarAutoSync.setOnCheckedChangeListener { _, checked ->
            prefs.autoOutlookCalendarSyncEnabled = checked
            if (checked) scheduleOutlookCalendarSync(prefs) else cancelOutlookCalendarSync()
        }
    }

    private fun updateOutlookCalendarSection(prefs: AppPreferences) {
        val enabled = prefs.outlookCalendarSyncEnabled
        binding.cardOutlookCalendarSync.visibility = if (enabled) View.VISIBLE else View.GONE
        if (enabled) {
            binding.tvOutlookCalendarSyncStatus.text = prefs.lastOutlookCalendarSyncSummary.ifEmpty {
                getString(R.string.settings_outlook_calendar_no_sync)
            }
        }
    }

    private fun scheduleOutlookCalendarSync(prefs: AppPreferences) {
        val request = PeriodicWorkRequestBuilder<OutlookCalendarSyncWorker>(
            prefs.autoOutlookCalendarSyncIntervalHours.toLong(), TimeUnit.HOURS
        ).setConstraints(
            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        ).build()
        WorkManager.getInstance(requireContext()).enqueueUniquePeriodicWork(
            "outlook_calendar_auto_sync", ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE, request
        )
    }

    private fun cancelOutlookCalendarSync() {
        WorkManager.getInstance(requireContext()).cancelUniqueWork("outlook_calendar_auto_sync")
    }

    private fun setupTeamsAssignmentSection(prefs: AppPreferences) {
        binding.switchTeamsAssignmentSync.isChecked = prefs.teamsAssignmentSyncEnabled
        updateTeamsAssignmentSection(prefs)

        binding.switchTeamsAssignmentSync.setOnCheckedChangeListener { _, checked ->
            prefs.teamsAssignmentSyncEnabled = checked
            updateTeamsAssignmentSection(prefs)
        }

        binding.btnTeamsAssignmentSyncNow.setOnClickListener {
            binding.btnTeamsAssignmentSyncNow.isEnabled = false
            viewLifecycleOwner.lifecycleScope.launch {
                val feature = FeatureManager.getSync(requireContext(), "microsoft")
                if (feature == null) {
                    binding.btnTeamsAssignmentSyncNow.isEnabled = true
                    Toast.makeText(requireContext(), "Microsoft 功能模組未安裝，請下載", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val bridge = SyncBridgeImpl(requireContext())
                val result = feature.sync("teams", bridge)
                binding.btnTeamsAssignmentSyncNow.isEnabled = true
                val summary = when (result) {
                    is SyncOutcome.Success ->
                        getString(R.string.settings_teams_assignment_sync_result, result.imported, result.skipped)
                    is SyncOutcome.AuthRequired -> {
                        Toast.makeText(requireContext(), "Microsoft 帳號授權已過期，請重新登入", Toast.LENGTH_SHORT).show()
                        getString(R.string.settings_teams_assignment_no_permission)
                    }
                    is SyncOutcome.Error -> result.message
                    is SyncOutcome.NoPermission ->
                        getString(R.string.settings_teams_assignment_no_permission)
                }
                prefs.lastTeamsAssignmentSyncSummary = summary
                binding.tvTeamsAssignmentSyncStatus.text = summary
            }
        }

        binding.switchTeamsAssignmentAutoSync.isChecked = prefs.autoTeamsAssignmentSyncEnabled
        binding.tvAutoTeamsAssignmentSyncInterval.text =
            getString(R.string.settings_auto_teams_assignment_sync_interval, prefs.autoTeamsAssignmentSyncIntervalHours)
        binding.switchTeamsAssignmentAutoSync.setOnCheckedChangeListener { _, checked ->
            prefs.autoTeamsAssignmentSyncEnabled = checked
            if (checked) scheduleTeamsAssignmentSync(prefs) else cancelTeamsAssignmentSync()
        }
    }

    private fun updateTeamsAssignmentSection(prefs: AppPreferences) {
        val enabled = prefs.teamsAssignmentSyncEnabled
        binding.cardTeamsAssignmentSync.visibility = if (enabled) View.VISIBLE else View.GONE
        if (enabled) {
            binding.tvTeamsAssignmentSyncStatus.text = prefs.lastTeamsAssignmentSyncSummary.ifEmpty {
                getString(R.string.settings_teams_assignment_no_sync)
            }
        }
    }

    private fun scheduleTeamsAssignmentSync(prefs: AppPreferences) {
        val request = PeriodicWorkRequestBuilder<TeamsAssignmentSyncWorker>(
            prefs.autoTeamsAssignmentSyncIntervalHours.toLong(), TimeUnit.HOURS
        ).setConstraints(
            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        ).build()
        WorkManager.getInstance(requireContext()).enqueueUniquePeriodicWork(
            "teams_assignment_auto_sync", ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE, request
        )
    }

    private fun cancelTeamsAssignmentSync() {
        WorkManager.getInstance(requireContext()).cancelUniqueWork("teams_assignment_auto_sync")
    }

    private fun setupOneNoteSection(prefs: AppPreferences) {
        binding.switchOneNoteSync.isChecked = prefs.oneNoteSyncEnabled
        updateOneNoteSection(prefs)

        binding.switchOneNoteSync.setOnCheckedChangeListener { _, checked ->
            prefs.oneNoteSyncEnabled = checked
            updateOneNoteSection(prefs)
        }

        binding.btnOneNoteSyncNow.setOnClickListener {
            binding.btnOneNoteSyncNow.isEnabled = false
            viewLifecycleOwner.lifecycleScope.launch {
                val feature = FeatureManager.getSync(requireContext(), "microsoft")
                if (feature == null) {
                    binding.btnOneNoteSyncNow.isEnabled = true
                    Toast.makeText(requireContext(), "Microsoft 功能模組未安裝，請下載", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val bridge = SyncBridgeImpl(requireContext())
                val result = feature.sync("onenote", bridge)
                binding.btnOneNoteSyncNow.isEnabled = true
                val summary = when (result) {
                    is SyncOutcome.Success ->
                        getString(R.string.settings_onenote_sync_result, result.imported, result.skipped)
                    is SyncOutcome.AuthRequired -> {
                        Toast.makeText(requireContext(), "Microsoft 帳號授權已過期，請重新登入", Toast.LENGTH_SHORT).show()
                        getString(R.string.settings_onenote_no_permission)
                    }
                    is SyncOutcome.Error -> result.message
                    is SyncOutcome.NoPermission -> getString(R.string.settings_onenote_no_permission)
                }
                prefs.lastOneNoteSyncSummary = summary
                binding.tvOneNoteSyncStatus.text = summary
            }
        }

        binding.switchOneNoteAutoSync.isChecked = prefs.autoOneNoteSyncEnabled
        binding.tvAutoOneNoteSyncInterval.text =
            getString(R.string.settings_auto_onenote_sync_interval, prefs.autoOneNoteSyncIntervalHours)
        binding.switchOneNoteAutoSync.setOnCheckedChangeListener { _, checked ->
            prefs.autoOneNoteSyncEnabled = checked
            if (checked) scheduleOneNoteSync(prefs) else cancelOneNoteSync()
        }
    }

    private fun updateOneNoteSection(prefs: AppPreferences) {
        val enabled = prefs.oneNoteSyncEnabled
        binding.cardOneNoteSync.visibility = if (enabled) View.VISIBLE else View.GONE
        if (enabled) {
            binding.tvOneNoteSyncStatus.text = prefs.lastOneNoteSyncSummary.ifEmpty {
                getString(R.string.settings_onenote_no_sync)
            }
        }
    }

    private fun scheduleOneNoteSync(prefs: AppPreferences) {
        val request = PeriodicWorkRequestBuilder<OneNoteSyncWorker>(
            prefs.autoOneNoteSyncIntervalHours.toLong(), TimeUnit.HOURS
        ).setConstraints(
            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        ).build()
        WorkManager.getInstance(requireContext()).enqueueUniquePeriodicWork(
            "onenote_auto_sync", ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE, request
        )
    }

    private fun cancelOneNoteSync() {
        WorkManager.getInstance(requireContext()).cancelUniqueWork("onenote_auto_sync")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

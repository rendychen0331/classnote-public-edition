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
import com.rendy.classnote.data.OneDriveAuthManager
import com.rendy.classnote.data.OneDriveBackupManager
import com.rendy.classnote.data.OneDriveBackupWorker
import com.rendy.classnote.databinding.SheetMicrosoftSyncBinding
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
    }

    private fun setupOneDriveSection(prefs: com.rendy.classnote.data.AppPreferences) {
        binding.switchOneDriveBackup.isChecked = prefs.oneDriveBackupEnabled
        updateOneDriveSection(prefs)

        binding.switchOneDriveBackup.setOnCheckedChangeListener { _, checked ->
            prefs.oneDriveBackupEnabled = checked
            updateOneDriveSection(prefs)
        }

        binding.btnOneDriveSignIn.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val email = OneDriveAuthManager.getAccountEmail(requireContext())
                if (email != null) {
                    OneDriveAuthManager.signOut(requireContext())
                } else {
                    val token = OneDriveAuthManager.signIn(requireActivity())
                    if (token == null) {
                        Toast.makeText(requireContext(),
                            getString(R.string.settings_onedrive_sign_in_failed), Toast.LENGTH_SHORT).show()
                    }
                }
                updateOneDriveSection(prefs)
            }
        }

        binding.btnOneDriveBackup.setOnClickListener {
            binding.btnOneDriveBackup.isEnabled = false
            Toast.makeText(requireContext(),
                getString(R.string.settings_onedrive_backup_in_progress), Toast.LENGTH_SHORT).show()
            viewLifecycleOwner.lifecycleScope.launch {
                val token = OneDriveAuthManager.acquireTokenSilent(requireContext())
                    ?: OneDriveAuthManager.signIn(requireActivity())
                if (token == null) {
                    binding.btnOneDriveBackup.isEnabled = true
                    Toast.makeText(requireContext(),
                        getString(R.string.settings_onedrive_sign_in_failed), Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val result = OneDriveBackupManager.backup(requireContext(), token)
                binding.btnOneDriveBackup.isEnabled = true
                when (result) {
                    is OneDriveBackupManager.Result.Success -> {
                        Toast.makeText(requireContext(),
                            getString(R.string.settings_onedrive_backup_success), Toast.LENGTH_SHORT).show()
                        loadOneDriveLastBackup(token)
                    }
                    is OneDriveBackupManager.Result.Error ->
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
                        val token = OneDriveAuthManager.acquireTokenSilent(requireContext())
                            ?: OneDriveAuthManager.signIn(requireActivity())
                        if (token == null) {
                            binding.btnOneDriveRestore.isEnabled = true
                            Toast.makeText(requireContext(),
                                getString(R.string.settings_onedrive_sign_in_failed), Toast.LENGTH_SHORT).show()
                            return@launch
                        }
                        val result = OneDriveBackupManager.restore(requireContext(), token)
                        binding.btnOneDriveRestore.isEnabled = true
                        when (result) {
                            is OneDriveBackupManager.Result.Success ->
                                Toast.makeText(requireContext(),
                                    getString(R.string.settings_onedrive_restore_success), Toast.LENGTH_LONG).show()
                            is OneDriveBackupManager.Result.Error ->
                                Toast.makeText(requireContext(), result.message, Toast.LENGTH_LONG).show()
                        }
                    }
                }
                .setNegativeButton(getString(R.string.cancel), null)
                .show()
        }

        binding.switchOneDriveAutoBackup.isChecked = prefs.autoOneDriveBackupEnabled
        binding.tvOneDriveAutoBackupInterval.text =
            getString(R.string.settings_auto_backup_interval_24h)

        binding.switchOneDriveAutoBackup.setOnCheckedChangeListener { _, checked ->
            prefs.autoOneDriveBackupEnabled = checked
            if (checked) scheduleOneDriveAutoBackup(prefs) else cancelOneDriveAutoBackup()
        }
    }

    private fun updateOneDriveSection(prefs: com.rendy.classnote.data.AppPreferences) {
        val enabled = prefs.oneDriveBackupEnabled
        binding.cardOneDriveAccount.visibility = if (enabled) View.VISIBLE else View.GONE
        binding.cardOneDriveActions.visibility = View.GONE
        if (!enabled) return

        viewLifecycleOwner.lifecycleScope.launch {
            val email = OneDriveAuthManager.getAccountEmail(requireContext())
            binding.tvOneDriveAccountEmail.text = email
                ?: getString(R.string.settings_onedrive_not_signed_in)
            binding.btnOneDriveSignIn.text = if (email != null)
                getString(R.string.settings_onedrive_sign_out)
            else
                getString(R.string.settings_onedrive_sign_in)

            if (email != null) {
                binding.cardOneDriveActions.visibility = View.VISIBLE
                binding.tvOneDriveLastBackup.visibility = View.VISIBLE
                val token = OneDriveAuthManager.acquireTokenSilent(requireContext())
                if (token != null) loadOneDriveLastBackup(token)
            } else {
                binding.tvOneDriveLastBackup.visibility = View.GONE
            }
        }
    }

    private fun loadOneDriveLastBackup(token: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            val time = OneDriveBackupManager.getLastBackupTime(token)
            binding.tvOneDriveLastBackup.text = if (time != null)
                getString(R.string.settings_onedrive_last_backup, time)
            else
                getString(R.string.settings_onedrive_no_backup)
        }
    }

    private fun scheduleOneDriveAutoBackup(prefs: com.rendy.classnote.data.AppPreferences) {
        val request = PeriodicWorkRequestBuilder<OneDriveBackupWorker>(
            prefs.autoOneDriveBackupIntervalHours.toLong(), TimeUnit.HOURS
        ).setConstraints(
            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        ).build()
        WorkManager.getInstance(requireContext()).enqueueUniquePeriodicWork(
            "onedrive_auto_backup", ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE, request
        )
    }

    private fun cancelOneDriveAutoBackup() {
        WorkManager.getInstance(requireContext()).cancelUniqueWork("onedrive_auto_backup")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

package com.rendy.classnote.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rendy.classnote.ClassNoteApplication
import com.rendy.classnote.data.AppPreferences
import com.rendy.classnote.data.LocalCalendarSyncManager
import com.rendy.classnote.data.LocalCalendarSyncWorker
import com.rendy.classnote.databinding.SheetLocalSyncBinding
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class LocalSyncSheet : Fragment() {

    private var _binding: SheetLocalSyncBinding? = null
    private val binding get() = _binding!!
    private lateinit var prefs: AppPreferences

    private val requestCalendarPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                prefs.localCalendarSyncEnabled = true
                updateSection()
            } else {
                binding.switchLocalCalendarSync.isChecked = false
                Toast.makeText(requireContext(), "需要日曆讀取權限才能啟用此功能", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = SheetLocalSyncBinding.inflate(inflater, container, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = AppPreferences(requireContext())

        binding.switchLocalCalendarSync.isChecked = prefs.localCalendarSyncEnabled
        updateSection()

        binding.switchLocalCalendarSync.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                if (hasCalendarPermission()) {
                    prefs.localCalendarSyncEnabled = true
                    updateSection()
                } else {
                    binding.switchLocalCalendarSync.isChecked = false
                    requestCalendarPermission.launch(Manifest.permission.READ_CALENDAR)
                }
            } else {
                prefs.localCalendarSyncEnabled = false
                cancelAutoSync()
                updateSection()
            }
        }

        binding.btnLocalCalendarSyncNow.setOnClickListener {
            binding.btnLocalCalendarSyncNow.isEnabled = false
            binding.tvLocalCalendarSyncStatus.text = "同步中..."
            viewLifecycleOwner.lifecycleScope.launch {
                val app = requireActivity().application as ClassNoteApplication
                val result = LocalCalendarSyncManager.sync(
                    requireContext(),
                    app.database.reminderDao(),
                    app.database.reminderNotificationDao()
                )
                binding.btnLocalCalendarSyncNow.isEnabled = true
                val summary = when (result) {
                    is LocalCalendarSyncManager.SyncResult.Success ->
                        "已匯入 ${result.imported} 筆，略過 ${result.skipped} 筆"
                    is LocalCalendarSyncManager.SyncResult.Error -> "同步失敗：${result.message}"
                    LocalCalendarSyncManager.SyncResult.NoPermission -> "缺少日曆讀取權限"
                }
                prefs.lastLocalCalendarSyncSummary = summary
                binding.tvLocalCalendarSyncStatus.text = summary
            }
        }

        binding.switchLocalCalendarAutoSync.isChecked = prefs.autoLocalCalendarSyncEnabled
        updateAutoSyncInterval()
        binding.rowLocalCalendarAutoSyncInterval.setOnClickListener { showIntervalDialog() }

        binding.switchLocalCalendarAutoSync.setOnCheckedChangeListener { _, checked ->
            prefs.autoLocalCalendarSyncEnabled = checked
            binding.rowLocalCalendarAutoSyncInterval.visibility = if (checked) View.VISIBLE else View.GONE
            if (checked) scheduleAutoSync() else cancelAutoSync()
        }
    }

    private fun updateSection() {
        val enabled = prefs.localCalendarSyncEnabled
        binding.cardLocalCalendarActions.visibility = if (enabled) View.VISIBLE else View.GONE
        binding.cardLocalCalendarAutoSync.visibility = if (enabled) View.VISIBLE else View.GONE
        if (enabled) {
            binding.tvLocalCalendarSyncStatus.text =
                prefs.lastLocalCalendarSyncSummary.ifEmpty { "尚未同步" }
            binding.switchLocalCalendarAutoSync.isChecked = prefs.autoLocalCalendarSyncEnabled
            binding.rowLocalCalendarAutoSyncInterval.visibility =
                if (prefs.autoLocalCalendarSyncEnabled) View.VISIBLE else View.GONE
            updateAutoSyncInterval()
        }
    }

    private fun updateAutoSyncInterval() {
        binding.tvLocalCalendarAutoSyncInterval.text = intervalLabel(prefs.autoLocalCalendarSyncIntervalHours)
    }

    private fun intervalLabel(hours: Int): String = when (hours) {
        1 -> "每 1 小時"
        3 -> "每 3 小時"
        12 -> "每 12 小時"
        24 -> "每天"
        else -> "每 6 小時"
    }

    private fun showIntervalDialog() {
        val options = intArrayOf(1, 3, 6, 12, 24)
        val labels = options.map { intervalLabel(it) }.toTypedArray()
        val current = options.indexOfFirst { it == prefs.autoLocalCalendarSyncIntervalHours }.takeIf { it >= 0 } ?: 2
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("同步頻率")
            .setSingleChoiceItems(labels, current) { dialog, which ->
                prefs.autoLocalCalendarSyncIntervalHours = options[which]
                updateAutoSyncInterval()
                if (prefs.autoLocalCalendarSyncEnabled) scheduleAutoSync()
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun scheduleAutoSync() {
        val request = PeriodicWorkRequestBuilder<LocalCalendarSyncWorker>(
            prefs.autoLocalCalendarSyncIntervalHours.toLong(), TimeUnit.HOURS
        ).build()
        WorkManager.getInstance(requireContext()).enqueueUniquePeriodicWork(
            LocalCalendarSyncWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
            request
        )
    }

    private fun cancelAutoSync() {
        WorkManager.getInstance(requireContext()).cancelUniqueWork(LocalCalendarSyncWorker.WORK_NAME)
    }

    private fun hasCalendarPermission(): Boolean =
        ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

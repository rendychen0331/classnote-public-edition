package com.rendy.classnote.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rendy.classnote.ClassNoteApplication
import com.rendy.classnote.R
import com.rendy.classnote.data.AppPreferences
import com.rendy.classnote.databinding.SheetAlarmPermBinding
import com.rendy.classnote.notification.NotificationHelper

class AlarmPermSheet : Fragment() {

    private var _binding: SheetAlarmPermBinding? = null
    private val binding get() = _binding!!

    private lateinit var prefs: AppPreferences

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = SheetAlarmPermBinding.inflate(inflater, container, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = (requireActivity().application as ClassNoteApplication).appPreferences
        setupAlarmSection()
        setupDndSwitch()
    }

    // ── 鬧鐘提醒 ─────────────────────────────────────────────────────────────

    private fun setupAlarmSection() {
        binding.switchFullScreenAlarm.isChecked = prefs.fullScreenAlarmEnabled
        binding.switchFullScreenAlarm.setOnCheckedChangeListener { _, checked ->
            prefs.fullScreenAlarmEnabled = checked
            binding.cardSnooze.alpha = if (checked) 1f else 0.4f
        }
        binding.cardSnooze.alpha = if (prefs.fullScreenAlarmEnabled) 1f else 0.4f

        updateSnoozeSummary()
        binding.cardSnooze.setOnClickListener {
            if (!prefs.fullScreenAlarmEnabled) return@setOnClickListener
            showSnoozeDialog()
        }

        setupQuietHoursSection()
    }

    private fun setupQuietHoursSection() {
        updateQuietHoursTimes()

        binding.tvQuietStart.setOnClickListener {
            val h = prefs.quietHoursStart
            android.app.TimePickerDialog(requireContext(), { _, hour, _ ->
                prefs.quietHoursStart = hour
                updateQuietHoursTimes()
            }, h, 0, true).show()
        }

        binding.tvQuietEnd.setOnClickListener {
            val h = prefs.quietHoursEnd
            android.app.TimePickerDialog(requireContext(), { _, hour, _ ->
                prefs.quietHoursEnd = hour
                updateQuietHoursTimes()
            }, h, 0, true).show()
        }

        binding.rgQuietPolicy.check(
            if (prefs.quietHoursPolicyBefore) R.id.rbQuietBefore else R.id.rbQuietAfter
        )
        binding.rgQuietPolicy.setOnCheckedChangeListener { _, checkedId ->
            prefs.quietHoursPolicyBefore = (checkedId == R.id.rbQuietBefore)
        }
    }

    private fun updateQuietHoursTimes() {
        binding.tvQuietStart.text = "%02d:00".format(prefs.quietHoursStart)
        binding.tvQuietEnd.text = "%02d:00".format(prefs.quietHoursEnd)
    }

    private fun updateSnoozeSummary() {
        val label = when (prefs.snoozeDurationMinutes) {
            5 -> getString(R.string.snooze_5min)
            15 -> getString(R.string.snooze_15min)
            30 -> getString(R.string.snooze_30min)
            else -> getString(R.string.snooze_10min)
        }
        binding.tvSnoozeValue.text = label
    }

    private fun showSnoozeDialog() {
        val options = arrayOf(
            getString(R.string.snooze_5min),
            getString(R.string.snooze_10min),
            getString(R.string.snooze_15min),
            getString(R.string.snooze_30min)
        )
        val minutes = intArrayOf(5, 10, 15, 30)
        val current = minutes.indexOfFirst { it == prefs.snoozeDurationMinutes }.coerceAtLeast(0)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.settings_snooze_title))
            .setSingleChoiceItems(options, current) { dialog, which ->
                prefs.snoozeDurationMinutes = minutes[which]
                updateSnoozeSummary()
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    // ── 勿擾模式開關 ─────────────────────────────────────────────────────────────

    private fun setupDndSwitch() {
        binding.switchBypassDnd.isChecked = prefs.bypassDndEnabled
        binding.switchBypassDnd.setOnCheckedChangeListener { _, checked ->
            prefs.bypassDndEnabled = checked
            if (checked) {
                val nm = requireContext().getSystemService(android.app.NotificationManager::class.java)
                if (!nm.isNotificationPolicyAccessGranted) {
                    startActivity(android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                }
            }
            applyDndChannel()
        }
    }

    private fun applyDndChannel() {
        val nm = requireContext().getSystemService(android.app.NotificationManager::class.java)
        val bypassDnd = prefs.bypassDndEnabled && nm.isNotificationPolicyAccessGranted
        NotificationHelper.createNotificationChannel(requireContext(), bypassDnd)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "AlarmPermSheet"
    }
}

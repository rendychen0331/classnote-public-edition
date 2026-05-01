package com.rendy.classnote.ui.settings

import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CheckedTextView
import android.widget.ListView
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rendy.classnote.ClassNoteApplication
import com.rendy.classnote.R
import com.rendy.classnote.data.AppPreferences
import com.rendy.classnote.databinding.SheetNotifListenerBinding
import com.rendy.classnote.ui.BiometricHelper
import kotlinx.coroutines.launch

class NotifListenerSheet : Fragment() {

    private var _binding: SheetNotifListenerBinding? = null
    private val binding get() = _binding!!

    private lateinit var prefs: AppPreferences
    private var skipNotifSwitchListener = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = SheetNotifListenerBinding.inflate(inflater, container, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = (requireActivity().application as ClassNoteApplication).appPreferences
        setupNotificationListenerSection()
    }

    override fun onResume() {
        super.onResume()
        updateNotifListenerStatus()
    }

    private fun setupNotificationListenerSection() {
        binding.switchNotifListener.isChecked = prefs.notificationListenerAutoAdd
        updateAiNotifySettingsVisibility()

        binding.switchNotifListener.setOnCheckedChangeListener { _, checked ->
            if (skipNotifSwitchListener) return@setOnCheckedChangeListener
            skipNotifSwitchListener = true
            binding.switchNotifListener.isChecked = !checked
            skipNotifSwitchListener = false

            BiometricHelper.authenticate(
                fragment = this,
                title = getString(R.string.biometric_title_monitor),
                subtitle = getString(R.string.biometric_subtitle_monitor),
                onSuccess = {
                    skipNotifSwitchListener = true
                    binding.switchNotifListener.isChecked = checked
                    skipNotifSwitchListener = false
                    prefs.notificationListenerAutoAdd = checked
                    updateAiNotifySettingsVisibility()
                }
            )
        }

        binding.btnNotifListenerSettings.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        binding.btnPickMonitoredApps.setOnClickListener {
            BiometricHelper.authenticate(
                fragment = this,
                title = getString(R.string.biometric_title_monitor),
                subtitle = getString(R.string.biometric_subtitle_monitor),
                onSuccess = { showAppPickerDialog() }
            )
        }
        updateMonitoredAppsSummary()

        binding.btnChannelFilter.setOnClickListener {
            BiometricHelper.authenticate(
                fragment = this,
                title = getString(R.string.biometric_title_monitor),
                subtitle = getString(R.string.biometric_subtitle_monitor),
                onSuccess = { showChannelAppPickerDialog() }
            )
        }
        updateChannelFilterSummary()

        updateDefaultRemindTimeSummary()
        binding.tvDefaultRemindTime.setOnClickListener {
            TimePickerDialog(
                requireContext(),
                { _, hour, minute ->
                    prefs.defaultRemindHour = hour
                    prefs.defaultRemindMinute = minute
                    updateDefaultRemindTimeSummary()
                },
                prefs.defaultRemindHour,
                prefs.defaultRemindMinute,
                true
            ).show()
        }
    }

    private fun updateAiNotifySettingsVisibility() {
        binding.cardAiNotifySettings.visibility =
            if (prefs.notificationListenerAutoAdd) View.VISIBLE else View.GONE
    }

    private fun updateNotifListenerStatus() {
        if (!prefs.notificationListenerAutoAdd) return
        val granted = NotificationManagerCompat.getEnabledListenerPackages(requireContext())
            .contains(requireContext().packageName)
        val grantedColor = requireContext().getColor(R.color.chip_reminder_color)
        val notGrantedColor = requireContext().getColor(R.color.chip_exam_color)
        binding.tvNotifListenerStatus.text = if (granted)
            getString(R.string.settings_ai_notify_access_granted)
        else
            getString(R.string.settings_ai_notify_access_denied)
        binding.tvNotifListenerStatus.setTextColor(if (granted) grantedColor else notGrantedColor)
    }

    private fun updateDefaultRemindTimeSummary() {
        binding.tvDefaultRemindTime.text =
            "%02d:%02d".format(prefs.defaultRemindHour, prefs.defaultRemindMinute)
    }

    private fun updateMonitoredAppsSummary() {
        val selected = prefs.monitoredPackages
        binding.tvMonitoredApps.text = if (selected.isEmpty())
            getString(R.string.settings_ai_monitored_apps_all)
        else
            getString(R.string.settings_ai_monitored_apps_count, selected.size)
    }

    private fun showAppPickerDialog() {
        val loadingDialog = MaterialAlertDialogBuilder(requireContext())
            .setMessage("載入 App 清單…")
            .setCancelable(true)
            .show()

        viewLifecycleOwner.lifecycleScope.launch {
            val pm = requireContext().packageManager
            val launchIntent = android.content.Intent(android.content.Intent.ACTION_MAIN)
                .addCategory(android.content.Intent.CATEGORY_LAUNCHER)

            val allItems = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                pm.queryIntentActivities(launchIntent, 0)
                    .map { it.activityInfo.packageName }
                    .filter { it != requireContext().packageName }
                    .distinct()
                    .map { pkg ->
                        val label = try {
                            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                        } catch (_: Exception) { pkg }
                        label to pkg
                    }
                    .sortedBy { it.first }
            }

            if (!isAdded) return@launch
            loadingDialog.dismiss()

            val checkedPkgs = prefs.monitoredPackages.toMutableSet()
            val displayedItems = allItems.toMutableList()

            val dialogView = layoutInflater.inflate(R.layout.dialog_app_picker, null)
            val etSearch = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etAppSearch)
            val listView = dialogView.findViewById<ListView>(R.id.listViewApps)

            val adapter = object : BaseAdapter() {
                override fun getCount() = displayedItems.size
                override fun getItem(pos: Int) = displayedItems[pos]
                override fun getItemId(pos: Int) = pos.toLong()
                override fun getView(pos: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                    val view = (convertView as? CheckedTextView)
                        ?: (layoutInflater.inflate(android.R.layout.simple_list_item_multiple_choice, parent, false) as CheckedTextView)
                    val (label, pkg) = displayedItems[pos]
                    view.text = label
                    view.isChecked = checkedPkgs.contains(pkg)
                    return view
                }
            }
            listView.adapter = adapter

            listView.setOnItemClickListener { _, _, pos, _ ->
                val pkg = displayedItems[pos].second
                if (!checkedPkgs.remove(pkg)) checkedPkgs.add(pkg)
                adapter.notifyDataSetChanged()
            }

            etSearch.doAfterTextChanged { text ->
                val query = text?.toString()?.trim()?.lowercase() ?: ""
                displayedItems.clear()
                displayedItems.addAll(
                    if (query.isBlank()) allItems
                    else allItems.filter { it.first.lowercase().contains(query) }
                )
                adapter.notifyDataSetChanged()
            }

            MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.settings_ai_monitored_apps_dialog_title))
                .setView(dialogView)
                .setPositiveButton(getString(R.string.save)) { _, _ ->
                    prefs.monitoredPackages = checkedPkgs
                    updateMonitoredAppsSummary()
                }
                .setNegativeButton(getString(R.string.cancel), null)
                .show()
        }
    }

    private fun updateChannelFilterSummary() {
        val configured = prefs.getMonitoredChannels().count { it.value.isNotEmpty() }
        binding.tvChannelFilter.text = if (configured == 0)
            getString(R.string.settings_ai_channel_filter_all)
        else
            getString(R.string.settings_ai_channel_filter_count, configured)
    }

    private fun showChannelAppPickerDialog() {
        val seen = prefs.getSeenChannels()
        if (seen.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.settings_ai_channel_no_seen), Toast.LENGTH_SHORT).show()
            return
        }
        val pm = requireContext().packageManager
        val monitored = prefs.getMonitoredChannels()
        val pkgs = seen.keys.sortedBy { pkg ->
            try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() } catch (_: Exception) { pkg }
        }
        val labels = pkgs.map { pkg ->
            val appLabel = try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() } catch (_: Exception) { pkg }
            val count = monitored[pkg]?.size ?: 0
            if (count == 0) appLabel else "$appLabel（$count 個頻道）"
        }.toTypedArray()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.settings_ai_channel_select_app))
            .setItems(labels) { _, which ->
                showChannelWhitelistDialog(pkgs[which], seen[pkgs[which]] ?: emptySet())
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showChannelWhitelistDialog(pkg: String, seenChannels: Set<String>) {
        val pm = requireContext().packageManager
        val appLabel = try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() } catch (_: Exception) { pkg }
        val currentWhitelist = prefs.getMonitoredChannels()[pkg] ?: emptySet()
        val channels = seenChannels.sorted()
        val checkedItems = BooleanArray(channels.size) { currentWhitelist.contains(channels[it]) }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(appLabel)
            .setMessage(getString(R.string.settings_ai_channel_filter_desc))
            .setMultiChoiceItems(channels.toTypedArray(), checkedItems) { _, which, isChecked ->
                checkedItems[which] = isChecked
            }
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val selected = channels.indices.filter { checkedItems[it] }.map { channels[it] }.toSet()
                val map = prefs.getMonitoredChannels().toMutableMap()
                if (selected.isEmpty()) map.remove(pkg) else map[pkg] = selected
                prefs.setMonitoredChannels(map)
                updateChannelFilterSummary()
            }
            .setNeutralButton(getString(R.string.settings_ai_channel_clear_btn)) { _, _ ->
                val map = prefs.getMonitoredChannels().toMutableMap()
                map.remove(pkg)
                prefs.setMonitoredChannels(map)
                updateChannelFilterSummary()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "NotifListenerSheet"
    }
}

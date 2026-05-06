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
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rendy.classnote.ClassNoteApplication
import com.rendy.classnote.R
import com.rendy.classnote.data.AppPreferences
import com.rendy.classnote.databinding.SheetAiSettingsBinding
import com.rendy.classnote.ui.BiometricHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AiSettingsSheet : Fragment() {

    private var _binding: SheetAiSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var prefs: AppPreferences
    private var skipNotifSwitchListener = false
    private var cachedAppList: List<Pair<String, String>>? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = SheetAiSettingsBinding.inflate(inflater, container, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = (requireActivity().application as ClassNoteApplication).appPreferences
        setupAiSection()
        setupNotificationListenerSection()
    }

    override fun onResume() {
        super.onResume()
        updateNotifListenerStatus()
    }

    private fun setupAiSection() {
        binding.switchAiEnabled.isChecked = prefs.aiEnabled
        updateAiKeysVisibility()

        binding.switchAiEnabled.setOnCheckedChangeListener { _, checked ->
            prefs.aiEnabled = checked
            updateAiKeysVisibility()
        }

        fun setupKeyField(
            til: com.google.android.material.textfield.TextInputLayout,
            et: com.google.android.material.textfield.TextInputEditText,
            btn: com.google.android.material.button.MaterialButton,
            getKey: () -> String,
            saveKey: (String) -> Unit,
            savedMsg: String
        ) {
            val saved = getKey()
            if (saved.isNotBlank()) et.setText(saved)
            var visible = false
            til.setEndIconOnClickListener {
                if (visible) {
                    visible = false
                    et.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                    til.setEndIconDrawable(R.drawable.ic_visibility_off)
                    et.setSelection(et.text?.length ?: 0)
                } else {
                    BiometricHelper.authenticate(
                        fragment = this,
                        title = getString(R.string.biometric_title_monitor),
                        subtitle = getString(R.string.biometric_subtitle_apikey),
                        onSuccess = {
                            visible = true
                            et.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                            til.setEndIconDrawable(R.drawable.ic_visibility)
                            et.setSelection(et.text?.length ?: 0)
                        }
                    )
                }
            }
            btn.setOnClickListener {
                BiometricHelper.authenticate(
                    fragment = this,
                    title = getString(R.string.biometric_title_monitor),
                    subtitle = getString(R.string.biometric_subtitle_apikey),
                    onSuccess = {
                        saveKey(et.text?.toString()?.trim() ?: "")
                        Toast.makeText(requireContext(), savedMsg, Toast.LENGTH_SHORT).show()
                        refreshProviderChips()
                    }
                )
            }
        }

        fun setupModelSwitch(
            switch: com.google.android.material.materialswitch.MaterialSwitch,
            keyLayout: android.view.View,
            getEnabled: () -> Boolean,
            saveEnabled: (Boolean) -> Unit,
            til: com.google.android.material.textfield.TextInputLayout,
            et: com.google.android.material.textfield.TextInputEditText,
            btn: com.google.android.material.button.MaterialButton,
            getKey: () -> String,
            saveKey: (String) -> Unit,
            savedMsg: String
        ) {
            switch.isChecked = getEnabled()
            keyLayout.visibility = if (getEnabled()) View.VISIBLE else View.GONE
            switch.setOnCheckedChangeListener { _, checked ->
                saveEnabled(checked)
                keyLayout.visibility = if (checked) View.VISIBLE else View.GONE
                refreshProviderChips()
            }
            setupKeyField(til, et, btn, getKey, saveKey, savedMsg)
        }

        setupModelSwitch(
            switch = binding.switchGeminiEnabled,
            keyLayout = binding.layoutGeminiKey,
            getEnabled = { prefs.geminiEnabled },
            saveEnabled = { prefs.geminiEnabled = it },
            til = binding.tilGeminiApiKey,
            et = binding.etGeminiApiKey,
            btn = binding.btnSaveGeminiKey,
            getKey = { prefs.geminiApiKey },
            saveKey = { prefs.geminiApiKey = it },
            savedMsg = getString(R.string.settings_ai_gemini_key_saved)
        )
        setupModelSwitch(
            switch = binding.switchMimoEnabled,
            keyLayout = binding.layoutMimoKey,
            getEnabled = { prefs.mimoEnabled },
            saveEnabled = { prefs.mimoEnabled = it },
            til = binding.tilMimoApiKey,
            et = binding.etMimoApiKey,
            btn = binding.btnSaveMimoKey,
            getKey = { prefs.mimoApiKey },
            saveKey = { prefs.mimoApiKey = it },
            savedMsg = "MiMo API Key 已儲存"
        )
        setupModelSwitch(
            switch = binding.switchClaudeEnabled,
            keyLayout = binding.layoutClaudeKey,
            getEnabled = { prefs.claudeEnabled },
            saveEnabled = { prefs.claudeEnabled = it },
            til = binding.tilClaudeApiKey,
            et = binding.etClaudeApiKey,
            btn = binding.btnSaveClaudeKey,
            getKey = { prefs.claudeApiKey },
            saveKey = { prefs.claudeApiKey = it },
            savedMsg = "Claude API Key 已儲存"
        )
        setupModelSwitch(
            switch = binding.switchOpenaiEnabled,
            keyLayout = binding.layoutOpenaiKey,
            getEnabled = { prefs.openaiEnabled },
            saveEnabled = { prefs.openaiEnabled = it },
            til = binding.tilOpenaiApiKey,
            et = binding.etOpenaiApiKey,
            btn = binding.btnSaveOpenaiKey,
            getKey = { prefs.openaiApiKey },
            saveKey = { prefs.openaiApiKey = it },
            savedMsg = "OpenAI API Key 已儲存"
        )
        setupModelSwitch(
            switch = binding.switchGroqEnabled,
            keyLayout = binding.layoutGroqKey,
            getEnabled = { prefs.groqEnabled },
            saveEnabled = { prefs.groqEnabled = it },
            til = binding.tilGroqApiKey,
            et = binding.etGroqApiKey,
            btn = binding.btnSaveGroqKey,
            getKey = { prefs.groqApiKey },
            saveKey = { prefs.groqApiKey = it },
            savedMsg = "Groq API Key 已儲存"
        )
        setupModelSwitch(
            switch = binding.switchDeepseekEnabled,
            keyLayout = binding.layoutDeepseekKey,
            getEnabled = { prefs.deepseekEnabled },
            saveEnabled = { prefs.deepseekEnabled = it },
            til = binding.tilDeepseekApiKey,
            et = binding.etDeepseekApiKey,
            btn = binding.btnSaveDeepseekKey,
            getKey = { prefs.deepseekApiKey },
            saveKey = { prefs.deepseekApiKey = it },
            savedMsg = "DeepSeek API Key 已儲存"
        )

        setupChatProviderChips()
        setupNotifProviderChips()
    }

    private fun refreshProviderChips() {
        val states = mapOf(
            "gemini" to (prefs.geminiApiKey.isNotBlank() && prefs.geminiEnabled),
            "mimo"   to (prefs.mimoApiKey.isNotBlank()   && prefs.mimoEnabled),
            "claude" to (prefs.claudeApiKey.isNotBlank() && prefs.claudeEnabled),
            "openai" to (prefs.openaiApiKey.isNotBlank() && prefs.openaiEnabled),
            "groq"      to (prefs.groqApiKey.isNotBlank()      && prefs.groqEnabled),
            "deepseek"  to (prefs.deepseekApiKey.isNotBlank()  && prefs.deepseekEnabled)
        )
        listOf(
            binding.chipChatGemini  to "gemini",
            binding.chipChatMimo    to "mimo",
            binding.chipChatClaude  to "claude",
            binding.chipChatOpenai  to "openai",
            binding.chipChatGroq      to "groq",
            binding.chipChatDeepseek  to "deepseek",
            binding.chipNotifGemini   to "gemini",
            binding.chipNotifMimo   to "mimo",
            binding.chipNotifClaude to "claude",
            binding.chipNotifOpenai    to "openai",
            binding.chipNotifGroq      to "groq",
            binding.chipNotifDeepseek  to "deepseek"
        ).forEach { (chip, provider) ->
            val active = states[provider] == true
            chip.isEnabled = active
            chip.alpha = if (active) 1f else 0.4f
        }
    }

    private fun setupChatProviderChips() {
        val chipMap = mapOf(
            binding.chipChatGemini to ("gemini" to (prefs.geminiApiKey to prefs.geminiEnabled)),
            binding.chipChatMimo   to ("mimo"   to (prefs.mimoApiKey   to prefs.mimoEnabled)),
            binding.chipChatClaude to ("claude" to (prefs.claudeApiKey to prefs.claudeEnabled)),
            binding.chipChatOpenai to ("openai" to (prefs.openaiApiKey to prefs.openaiEnabled)),
            binding.chipChatGroq      to ("groq"      to (prefs.groqApiKey      to prefs.groqEnabled)),
            binding.chipChatDeepseek  to ("deepseek"  to (prefs.deepseekApiKey  to prefs.deepseekEnabled))
        )
        chipMap.forEach { (chip, pair) ->
            val (_, keyAndEnabled) = pair
            val active = keyAndEnabled.first.isNotBlank() && keyAndEnabled.second
            chip.isEnabled = active
            chip.alpha = if (active) 1f else 0.4f
        }
        val preferred = prefs.preferredChatProvider
        val preferredChip = chipMap.entries.firstOrNull { it.value.first == preferred }?.key
        val firstAvailable = chipMap.entries.firstOrNull { it.key.isEnabled }?.key
        (preferredChip?.takeIf { it.isEnabled } ?: firstAvailable)?.isChecked = true

        binding.chipGroupChatProvider.setOnCheckedStateChangeListener { _, checkedIds ->
            val id = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            val provider = chipMap.entries.firstOrNull { it.key.id == id }?.value?.first
                ?: return@setOnCheckedStateChangeListener
            prefs.preferredChatProvider = provider
        }
    }

    private fun setupNotifProviderChips() {
        val chipMap = mapOf(
            binding.chipNotifGemini to ("gemini" to (prefs.geminiApiKey to prefs.geminiEnabled)),
            binding.chipNotifMimo   to ("mimo"   to (prefs.mimoApiKey   to prefs.mimoEnabled)),
            binding.chipNotifClaude to ("claude" to (prefs.claudeApiKey to prefs.claudeEnabled)),
            binding.chipNotifOpenai to ("openai" to (prefs.openaiApiKey to prefs.openaiEnabled)),
            binding.chipNotifGroq      to ("groq"      to (prefs.groqApiKey      to prefs.groqEnabled)),
            binding.chipNotifDeepseek  to ("deepseek"  to (prefs.deepseekApiKey  to prefs.deepseekEnabled))
        )
        chipMap.forEach { (chip, pair) ->
            val (_, keyAndEnabled) = pair
            val active = keyAndEnabled.first.isNotBlank() && keyAndEnabled.second
            chip.isEnabled = active
            chip.alpha = if (active) 1f else 0.4f
        }
        val preferred = prefs.preferredNotifProvider
        val preferredChip = chipMap.entries.firstOrNull { it.value.first == preferred }?.key
        val firstAvailable = chipMap.entries.firstOrNull { it.key.isEnabled }?.key
        (preferredChip?.takeIf { it.isEnabled } ?: firstAvailable)?.isChecked = true

        binding.chipGroupNotifProvider.setOnCheckedStateChangeListener { _, checkedIds ->
            val id = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            val provider = chipMap.entries.firstOrNull { it.key.id == id }?.value?.first
                ?: return@setOnCheckedStateChangeListener
            prefs.preferredNotifProvider = provider
        }
    }

    private fun updateAiKeysVisibility() {
        val v = if (prefs.aiEnabled) View.VISIBLE else View.GONE
        binding.cardGemini.visibility = v
        binding.cardMimo.visibility = v
        binding.cardClaude.visibility = v
        binding.cardOpenai.visibility = v
        binding.cardGroq.visibility = v
        binding.cardDeepseek.visibility = v
        binding.cardChatProvider.visibility = v
        binding.cardNotifProvider.visibility = v
    }

    // ── 通知辨識 ──

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

        binding.btnChannelBlacklist.setOnClickListener {
            BiometricHelper.authenticate(
                fragment = this,
                title = getString(R.string.biometric_title_monitor),
                subtitle = getString(R.string.biometric_subtitle_monitor),
                onSuccess = { showChannelBlacklistAppPickerDialog() }
            )
        }
        updateChannelBlacklistSummary()

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

        binding.switchSensitiveKeywords.isChecked = prefs.sensitiveKeywordsEnabled
        binding.switchSensitiveKeywords.setOnCheckedChangeListener { _, checked ->
            prefs.sensitiveKeywordsEnabled = checked
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

    private suspend fun loadAppList(): List<Pair<String, String>> {
        cachedAppList?.let { return it }
        val pm = requireContext().packageManager
        val launchIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return withContext(Dispatchers.IO) {
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
        }.also { cachedAppList = it }
    }

    private fun showAppPickerDialog() {
        val loadingDialog = cachedAppList?.let { null } ?: MaterialAlertDialogBuilder(requireContext())
            .setMessage("載入 App 清單…").setCancelable(true).show()

        viewLifecycleOwner.lifecycleScope.launch {
            val allItems = loadAppList()
            if (!isAdded) return@launch
            loadingDialog?.dismiss()

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
                displayedItems.addAll(if (query.isBlank()) allItems else allItems.filter { it.first.lowercase().contains(query) })
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

    private fun updateChannelBlacklistSummary() {
        val configured = prefs.getBlacklistedChannels().count { it.value.isNotEmpty() }
        binding.tvChannelBlacklist.text = if (configured == 0)
            getString(R.string.settings_ai_channel_blacklist_none)
        else
            getString(R.string.settings_ai_channel_blacklist_count, configured)
    }

    private fun showChannelBlacklistAppPickerDialog() {
        val loadingDialog = cachedAppList?.let { null } ?: MaterialAlertDialogBuilder(requireContext())
            .setMessage("載入 App 清單…").setCancelable(true).show()

        viewLifecycleOwner.lifecycleScope.launch {
            val allItems = loadAppList()
            if (!isAdded) return@launch
            loadingDialog?.dismiss()

            val blacklisted = prefs.getBlacklistedChannels()
            val seen = prefs.getSeenChannels()
            val allLabeled = allItems.map { (label, pkg) ->
                val blacklistCount = blacklisted[pkg]?.size ?: 0
                val seenCount = seen[pkg]?.size ?: 0
                val suffix = when {
                    blacklistCount > 0 -> "（已封鎖 $blacklistCount 個頻道）"
                    seenCount > 0      -> "（$seenCount 個頻道可選）"
                    else               -> ""
                }
                "$label$suffix" to pkg
            }.toMutableList()
            val displayedItems = allLabeled.toMutableList()

            val dialogView = layoutInflater.inflate(R.layout.dialog_app_picker, null)
            val etSearch = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etAppSearch)
            val listView = dialogView.findViewById<android.widget.ListView>(R.id.listViewApps)
            val adapter = object : android.widget.BaseAdapter() {
                override fun getCount() = displayedItems.size
                override fun getItem(pos: Int) = displayedItems[pos]
                override fun getItemId(pos: Int) = pos.toLong()
                override fun getView(pos: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                    val view = (convertView as? android.widget.TextView)
                        ?: layoutInflater.inflate(android.R.layout.simple_list_item_1, parent, false) as android.widget.TextView
                    view.text = displayedItems[pos].first
                    return view
                }
            }
            listView.adapter = adapter
            listView.setOnItemClickListener { _, _, pos, _ ->
                showChannelBlacklistDialog(displayedItems[pos].second, seen[displayedItems[pos].second] ?: emptySet())
            }
            etSearch.doAfterTextChanged { text ->
                val query = text?.toString()?.trim()?.lowercase() ?: ""
                displayedItems.clear()
                displayedItems.addAll(if (query.isBlank()) allLabeled else allLabeled.filter { it.first.lowercase().contains(query) })
                adapter.notifyDataSetChanged()
            }
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.settings_ai_channel_blacklist_select_app))
                .setView(dialogView)
                .setNegativeButton(getString(R.string.cancel), null)
                .show()
        }
    }

    private fun showChannelBlacklistDialog(pkg: String, seenChannels: Set<String>) {
        val pm = requireContext().packageManager
        val appLabel = try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() } catch (_: Exception) { pkg }
        val currentBlacklist = prefs.getBlacklistedChannels()[pkg] ?: emptySet()
        val channels = (seenChannels + currentBlacklist).sorted().toMutableList()
        val checkedItems = channels.map { currentBlacklist.contains(it) }.toBooleanArray()

        val builder = MaterialAlertDialogBuilder(requireContext()).setTitle("封鎖頻道 - $appLabel")
        if (channels.isEmpty()) {
            builder.setMessage("尚無頻道紀錄，先讓 App 發送通知後再設定")
                .setNegativeButton(getString(R.string.cancel), null).show()
        } else {
            builder.setMultiChoiceItems(channels.toTypedArray(), checkedItems) { _, which, isChecked ->
                checkedItems[which] = isChecked
            }
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val selected = channels.indices.filter { checkedItems[it] }.map { channels[it] }.toSet()
                val map = prefs.getBlacklistedChannels().toMutableMap()
                if (selected.isEmpty()) map.remove(pkg) else map[pkg] = selected
                prefs.setBlacklistedChannels(map)
                updateChannelBlacklistSummary()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
        }
    }

    private fun showChannelAppPickerDialog() {
        val loadingDialog = cachedAppList?.let { null } ?: MaterialAlertDialogBuilder(requireContext())
            .setMessage("載入 App 清單…").setCancelable(true).show()

        viewLifecycleOwner.lifecycleScope.launch {
            val allItems = loadAppList()
            if (!isAdded) return@launch
            loadingDialog?.dismiss()

            val seen = prefs.getSeenChannels()
            val monitored = prefs.getMonitoredChannels()
            val allLabeled = allItems.map { (label, pkg) ->
                val channelCount = monitored[pkg]?.size ?: 0
                val seenCount = seen[pkg]?.size ?: 0
                val suffix = when {
                    channelCount > 0 -> "（已篩選 $channelCount 個頻道）"
                    seenCount > 0    -> "（$seenCount 個頻道可選）"
                    else             -> ""
                }
                "$label$suffix" to pkg
            }.toMutableList()
            val displayedItems = allLabeled.toMutableList()

            val dialogView = layoutInflater.inflate(R.layout.dialog_app_picker, null)
            val etSearch = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etAppSearch)
            val listView = dialogView.findViewById<ListView>(R.id.listViewApps)
            val adapter = object : BaseAdapter() {
                override fun getCount() = displayedItems.size
                override fun getItem(pos: Int) = displayedItems[pos]
                override fun getItemId(pos: Int) = pos.toLong()
                override fun getView(pos: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                    val view = (convertView as? android.widget.TextView)
                        ?: layoutInflater.inflate(android.R.layout.simple_list_item_1, parent, false) as android.widget.TextView
                    view.text = displayedItems[pos].first
                    return view
                }
            }
            listView.adapter = adapter
            listView.setOnItemClickListener { _, _, pos, _ ->
                showChannelWhitelistDialog(displayedItems[pos].second, seen[displayedItems[pos].second] ?: emptySet())
            }
            etSearch.doAfterTextChanged { text ->
                val query = text?.toString()?.trim()?.lowercase() ?: ""
                displayedItems.clear()
                displayedItems.addAll(if (query.isBlank()) allLabeled else allLabeled.filter { it.first.lowercase().contains(query) })
                adapter.notifyDataSetChanged()
            }
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.settings_ai_channel_select_app))
                .setView(dialogView)
                .setNegativeButton(getString(R.string.cancel), null)
                .show()
        }
    }

    private fun showChannelWhitelistDialog(pkg: String, seenChannels: Set<String>) {
        val pm = requireContext().packageManager
        val appLabel = try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() } catch (_: Exception) { pkg }
        val currentWhitelist = prefs.getMonitoredChannels()[pkg] ?: emptySet()
        val channels = (seenChannels + currentWhitelist).sorted().toMutableList()
        val checkedItems = channels.map { currentWhitelist.contains(it) }.toBooleanArray()

        fun buildAndShow(channelList: MutableList<String>, checked: BooleanArray) {
            val builder = MaterialAlertDialogBuilder(requireContext()).setTitle(appLabel)
            if (channelList.isEmpty()) {
                builder.setMessage("尚無頻道紀錄，可點「手動新增」加入頻道名稱")
                    .setPositiveButton(getString(R.string.save), null)
                    .setNeutralButton("手動新增") { _, _ -> showManualAddChannelDialog(pkg, channelList, checked) }
                    .setNegativeButton(getString(R.string.cancel), null)
                    .show()
            } else {
                builder.setMultiChoiceItems(channelList.toTypedArray(), checked) { _, which, isChecked -> checked[which] = isChecked }
                    .setPositiveButton(getString(R.string.save)) { _, _ ->
                        val selected = channelList.indices.filter { checked[it] }.map { channelList[it] }.toSet()
                        val map = prefs.getMonitoredChannels().toMutableMap()
                        if (selected.isEmpty()) map.remove(pkg) else map[pkg] = selected
                        prefs.setMonitoredChannels(map)
                        updateChannelFilterSummary()
                    }
                    .setNeutralButton("手動新增") { _, _ -> showManualAddChannelDialog(pkg, channelList, checked) }
                    .setNegativeButton("清除紀錄") { _, _ ->
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle("清除頻道紀錄")
                            .setMessage("清除「$appLabel」的所有已記錄頻道？\n已加入白名單的頻道不受影響。")
                            .setPositiveButton("清除") { _, _ ->
                                prefs.clearSeenChannels(pkg)
                                showChannelWhitelistDialog(pkg, prefs.getSeenChannels()[pkg] ?: emptySet())
                            }
                            .setNegativeButton(getString(R.string.cancel)) { _, _ ->
                                showChannelWhitelistDialog(pkg, prefs.getSeenChannels()[pkg] ?: channelList.toSet())
                            }
                            .show()
                    }
                    .show()
            }
        }
        buildAndShow(channels, checkedItems)
    }

    private fun showManualAddChannelDialog(pkg: String, channels: MutableList<String>, checked: BooleanArray) {
        val pm = requireContext().packageManager
        val appLabel = try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() } catch (_: Exception) { pkg }
        val input = com.google.android.material.textfield.TextInputEditText(requireContext()).apply {
            hint = "頻道名稱（如：群組名稱、通知標題）"
            setPadding(48, 24, 48, 8)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("手動新增頻道 - $appLabel")
            .setView(input)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val name = input.text?.toString()?.trim() ?: ""
                if (name.isBlank()) return@setPositiveButton
                if (!channels.contains(name)) {
                    prefs.addSeenChannel(pkg, name)
                    val map = prefs.getMonitoredChannels().toMutableMap()
                    map[pkg] = (map[pkg] ?: emptySet()) + name
                    prefs.setMonitoredChannels(map)
                    updateChannelFilterSummary()
                    showChannelWhitelistDialog(pkg, prefs.getSeenChannels()[pkg] ?: setOf(name))
                } else {
                    Toast.makeText(requireContext(), "頻道已存在", Toast.LENGTH_SHORT).show()
                    showChannelWhitelistDialog(pkg, prefs.getSeenChannels()[pkg] ?: channels.toSet())
                }
            }
            .setNegativeButton(getString(R.string.cancel)) { _, _ ->
                showChannelWhitelistDialog(pkg, prefs.getSeenChannels()[pkg] ?: channels.toSet())
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "AiSettingsSheet"
    }
}

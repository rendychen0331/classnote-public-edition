package com.rendy.classnote.ui.settings

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.common.SignInButton
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rendy.classnote.ClassNoteApplication
import com.rendy.classnote.R
import com.rendy.classnote.data.AppPreferences
import com.rendy.classnote.data.CalendarSyncWorker
import com.rendy.classnote.data.ClassroomSyncWorker
import com.rendy.classnote.data.DriveBackupWorker
import com.rendy.classnote.data.FeatureManager
import com.rendy.classnote.data.GmailSyncWorker
import com.rendy.classnote.data.GoogleAuthManager
import com.rendy.classnote.data.KeepSyncWorker
import com.rendy.classnote.data.TasksSyncWorker
import com.rendy.classnote.databinding.SheetGoogleSyncBinding
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

class GoogleSyncSheet : Fragment() {

    private var _binding: SheetGoogleSyncBinding? = null
    private val binding get() = _binding!!

    private lateinit var prefs: AppPreferences

    private val reAuthLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            Toast.makeText(requireContext(), getString(R.string.settings_google_reauth_ok), Toast.LENGTH_SHORT).show()
        }
        updateDriveSection()
    }

    private val signInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val account = GoogleAuthManager.handleSignInResult(result.data)
            if (account == null) {
                Toast.makeText(requireContext(), "登入失敗", Toast.LENGTH_SHORT).show()
            }
        } else {
            GoogleAuthManager.handleSignInResult(result.data)
        }
        updateDriveSection()
    }

    private val exportSignInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val account = GoogleAuthManager.handleSignInResult(result.data)
            if (account == null) {
                Toast.makeText(requireContext(), "授權失敗", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val gmailAccountPickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val email = result.data?.getStringExtra(android.accounts.AccountManager.KEY_ACCOUNT_NAME)
        if (!email.isNullOrBlank()) {
            GoogleAuthManager.addGmailAccountEmail(requireContext(), email)
        }
        updateGmailSection()
    }

    private val classroomAccountPickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val email = result.data?.getStringExtra(android.accounts.AccountManager.KEY_ACCOUNT_NAME)
        if (!email.isNullOrBlank()) {
            GoogleAuthManager.addClassroomAccountEmail(requireContext(), email)
        }
        updateClassroomSection()
    }

    private val calendarAccountPickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val email = result.data?.getStringExtra(android.accounts.AccountManager.KEY_ACCOUNT_NAME)
        if (!email.isNullOrBlank()) {
            GoogleAuthManager.addCalendarAccountEmail(requireContext(), email)
        }
        updateCalendarSection()
    }

    private val tasksAccountPickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val email = result.data?.getStringExtra(android.accounts.AccountManager.KEY_ACCOUNT_NAME)
        if (!email.isNullOrBlank()) {
            GoogleAuthManager.addTasksAccountEmail(requireContext(), email)
        }
        updateTasksSection()
    }

    private val keepAccountPickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val email = result.data?.getStringExtra(android.accounts.AccountManager.KEY_ACCOUNT_NAME)
        if (!email.isNullOrBlank()) {
            GoogleAuthManager.addKeepAccountEmail(requireContext(), email)
        }
        updateKeepSection()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = SheetGoogleSyncBinding.inflate(inflater, container, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = (requireActivity().application as ClassNoteApplication).appPreferences
        setupGoogleSection()
        setupGmailSection()
        setupClassroomSection()
        setupCalendarSection()
        setupTasksSection()
        setupKeepSection()
    }

    // ── 帳號列動態注入 ──────────────────────────────────────────────────────────

    private fun addAccountRow(container: LinearLayout, email: String, onRemove: () -> Unit) {
        val dp = resources.displayMetrics.density
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (4 * dp).toInt() }
        }
        val emailView = TextView(requireContext()).apply {
            text = email
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val removeBtn = MaterialButton(
            requireContext(), null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle
        ).apply {
            text = "移除"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener { onRemove() }
        }
        row.addView(emailView)
        row.addView(removeBtn)
        container.addView(row)
    }

    // ── Google 帳號備份 ──────────────────────────────────────────────────────────

    private fun setupGoogleSection() {
        binding.switchDriveBackup.isChecked = prefs.driveBackupEnabled
        updateDriveSection()

        binding.switchDriveBackup.setOnCheckedChangeListener { _, checked ->
            prefs.driveBackupEnabled = checked
            updateDriveSection()
        }

        updateBackupNetworkSummary()
        binding.cardBackupNetwork.setOnClickListener { showBackupNetworkDialog() }
        setupAutoBackup()

        binding.btnGoogleSignIn.setSize(SignInButton.SIZE_WIDE)
        binding.btnGoogleSignIn.setOnClickListener {
            signInLauncher.launch(GoogleAuthManager.getSignInIntent(requireContext()))
        }

        binding.btnGoogleSignOut.setOnClickListener {
            GoogleAuthManager.signOut(requireContext()) {
                requireActivity().runOnUiThread { updateDriveSection() }
            }
        }

        binding.btnUseGmailAccount.setOnClickListener {
            signInLauncher.launch(GoogleAuthManager.getSignInIntentWithGmail(requireContext()))
        }

        binding.btnGoogleBackup.setOnClickListener {
            binding.btnGoogleBackup.isEnabled = false
            Toast.makeText(requireContext(), getString(R.string.settings_google_backup_in_progress), Toast.LENGTH_SHORT).show()
            viewLifecycleOwner.lifecycleScope.launch {
                val feature = FeatureManager.getBackup(requireContext(), "google")
                if (feature == null) {
                    binding.btnGoogleBackup.isEnabled = true
                    Toast.makeText(requireContext(), "Google 功能模組未安裝，請下載", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val bridge = SyncBridgeImpl(requireContext())
                val result = feature.backup(bridge)
                binding.btnGoogleBackup.isEnabled = true
                when (result) {
                    is BackupOutcome.Success ->
                        Toast.makeText(requireContext(), getString(R.string.settings_google_backup_success), Toast.LENGTH_SHORT).show()
                    is BackupOutcome.AuthRequired -> {
                        val backupIntent = result.intent
                        if (backupIntent != null) {
                            reAuthLauncher.launch(backupIntent)
                        } else {
                            Toast.makeText(requireContext(), "帳號授權已過期", Toast.LENGTH_SHORT).show()
                        }
                    }
                    is BackupOutcome.Error ->
                        Toast.makeText(requireContext(), result.message, Toast.LENGTH_LONG).show()
                }
            }
        }

        binding.btnGoogleRestore.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val feature = FeatureManager.getBackup(requireContext(), "google")
                if (feature == null) {
                    Toast.makeText(requireContext(), "Google 功能模組未安裝，請下載", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val bridge = SyncBridgeImpl(requireContext())
                val meta = feature.fetchMeta(bridge)
                showRestoreOptionsDialog(feature, bridge, meta)
            }
        }

        binding.btnSyncNotes.setOnClickListener {
            Toast.makeText(requireContext(), "此功能在功能模組版本暫不支援", Toast.LENGTH_SHORT).show()
        }

        binding.btnGoogleExport.setOnClickListener {
            Toast.makeText(requireContext(), "此功能在功能模組版本暫不支援", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupAutoBackup() {
        val intervalLabels = mapOf(
            6 to getString(R.string.settings_auto_backup_interval_6h),
            12 to getString(R.string.settings_auto_backup_interval_12h),
            24 to getString(R.string.settings_auto_backup_interval_24h),
            72 to getString(R.string.settings_auto_backup_interval_72h)
        )
        fun updateIntervalLabel() {
            binding.tvAutoBackupInterval.text = intervalLabels[prefs.autoBackupIntervalHours]
                ?: getString(R.string.settings_auto_backup_interval_24h)
        }

        binding.switchAutoBackup.isChecked = prefs.autoBackupEnabled
        binding.rowAutoBackupInterval.visibility =
            if (prefs.autoBackupEnabled) View.VISIBLE else View.GONE
        updateIntervalLabel()

        binding.switchAutoBackup.setOnCheckedChangeListener { _, checked ->
            prefs.autoBackupEnabled = checked
            binding.rowAutoBackupInterval.visibility = if (checked) View.VISIBLE else View.GONE
            if (checked) scheduleAutoBackup() else cancelAutoBackup()
        }

        binding.rowAutoBackupInterval.setOnClickListener {
            val keys = listOf(6, 12, 24, 72)
            val labels = keys.map { intervalLabels[it] ?: "" }.toTypedArray()
            val current = keys.indexOf(prefs.autoBackupIntervalHours).coerceAtLeast(0)
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.settings_auto_backup_interval))
                .setSingleChoiceItems(labels, current) { dialog, which ->
                    prefs.autoBackupIntervalHours = keys[which]
                    updateIntervalLabel()
                    if (prefs.autoBackupEnabled) scheduleAutoBackup()
                    dialog.dismiss()
                }
                .show()
        }
    }

    private fun scheduleAutoBackup() {
        val networkType = if (prefs.backupNetworkType == AppPreferences.NETWORK_WIFI)
            NetworkType.UNMETERED else NetworkType.CONNECTED
        val constraints = Constraints.Builder().setRequiredNetworkType(networkType).build()
        val request = PeriodicWorkRequestBuilder<DriveBackupWorker>(
            prefs.autoBackupIntervalHours.toLong(), TimeUnit.HOURS
        ).setConstraints(constraints).build()
        WorkManager.getInstance(requireContext()).enqueueUniquePeriodicWork(
            "drive_auto_backup", ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE, request
        )
    }

    private fun cancelAutoBackup() {
        WorkManager.getInstance(requireContext()).cancelUniqueWork("drive_auto_backup")
    }

    private fun updateBackupNetworkSummary() {
        val label = when (prefs.backupNetworkType) {
            AppPreferences.NETWORK_WIFI -> getString(R.string.backup_network_wifi)
            AppPreferences.NETWORK_MOBILE -> getString(R.string.backup_network_mobile)
            else -> getString(R.string.backup_network_any)
        }
        binding.tvBackupNetworkValue.text = label
    }

    private fun showBackupNetworkDialog() {
        val options = arrayOf(
            getString(R.string.backup_network_wifi),
            getString(R.string.backup_network_mobile),
            getString(R.string.backup_network_any)
        )
        val keys = arrayOf(AppPreferences.NETWORK_WIFI, AppPreferences.NETWORK_MOBILE, AppPreferences.NETWORK_ANY)
        val current = keys.indexOfFirst { it == prefs.backupNetworkType }.coerceAtLeast(0)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.backup_network_title))
            .setSingleChoiceItems(options, current) { dialog, which ->
                prefs.backupNetworkType = keys[which]
                updateBackupNetworkSummary()
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun updateDriveSection() {
        val enabled = prefs.driveBackupEnabled
        binding.cardGoogleAccount.visibility = if (enabled) View.VISIBLE else View.GONE

        val account = GoogleAuthManager.getAccount(requireContext())
        val signedIn = account != null

        binding.cardGoogleActions.visibility = if (enabled && signedIn) View.VISIBLE else View.GONE
        binding.cardBackupNetwork.visibility = if (enabled && signedIn) View.VISIBLE else View.GONE
        binding.cardAutoBackup.visibility = if (enabled && signedIn) View.VISIBLE else View.GONE

        if (!enabled) return

        binding.tvGoogleAccountEmail.text = account?.email
            ?: getString(R.string.settings_google_not_signed_in)
        binding.btnGoogleSignIn.visibility = if (signedIn) View.GONE else View.VISIBLE
        binding.btnGoogleSignOut.visibility = if (signedIn) View.VISIBLE else View.GONE
        binding.tvGoogleLastBackup.visibility = if (signedIn) View.VISIBLE else View.GONE

        binding.layoutUseGmailAccount.visibility =
            if (!signedIn && prefs.gmailSyncEnabled) View.VISIBLE else View.GONE

        if (signedIn) {
            binding.tvGoogleLastBackup.text = getString(R.string.settings_google_no_backup)
        }
    }

    // ── Gmail 作業同步 ──────────────────────────────────────────────────────────

    private fun setupGmailSection() {
        binding.switchGmailSync.isChecked = prefs.gmailSyncEnabled
        updateGmailSection()

        binding.switchGmailSync.setOnCheckedChangeListener { _, checked ->
            prefs.gmailSyncEnabled = checked
            updateGmailSection()
            updateDriveSection()
        }

        binding.btnGmailSignIn.setSize(SignInButton.SIZE_WIDE)
        binding.btnGmailSignIn.setOnClickListener {
            gmailAccountPickerLauncher.launch(GoogleAuthManager.getAccountPickerIntent())
        }

        binding.switchGmailForward.isChecked = prefs.gmailClassroomForwardEnabled
        binding.switchGmailForward.setOnCheckedChangeListener { _, checked ->
            prefs.gmailClassroomForwardEnabled = checked
        }

        setupGmailAutoSync()

        binding.btnGmailSyncNow.setOnClickListener {
            val emails = GoogleAuthManager.getGmailAccountEmails(requireContext())
            if (emails.isEmpty()) return@setOnClickListener
            binding.btnGmailSyncNow.isEnabled = false
            viewLifecycleOwner.lifecycleScope.launch {
                val feature = FeatureManager.getSync(requireContext(), "google")
                if (feature == null) {
                    binding.btnGmailSyncNow.isEnabled = true
                    Toast.makeText(requireContext(), "Google 功能模組未安裝，請下載", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val bridge = SyncBridgeImpl(requireContext())
                var totalImported = 0
                var totalSkipped = 0
                for (email in emails) {
                    when (val result = feature.sync("gmail", bridge)) {
                        is SyncOutcome.Success -> {
                            totalImported += result.imported
                            totalSkipped += result.skipped
                        }
                        is SyncOutcome.AuthRequired ->
                            Toast.makeText(requireContext(), "Google 帳號授權已過期，請重新登入", Toast.LENGTH_SHORT).show()
                        is SyncOutcome.Error ->
                            Toast.makeText(requireContext(), result.message, Toast.LENGTH_LONG).show()
                        is SyncOutcome.NoPermission ->
                            Toast.makeText(requireContext(), "Google 帳號授權已過期，請重新登入", Toast.LENGTH_SHORT).show()
                    }
                }
                binding.btnGmailSyncNow.isEnabled = true
                val summary = getString(R.string.settings_gmail_sync_result, totalImported, totalSkipped)
                prefs.lastGmailSyncSummary = summary
                binding.tvGmailSyncStatus.text = summary
            }
        }
    }

    private fun setupGmailAutoSync() {
        val intervalHours = prefs.autoGmailSyncIntervalHours
        binding.switchGmailAutoSync.isChecked = prefs.autoGmailSyncEnabled
        binding.tvAutoGmailSyncInterval.text =
            getString(R.string.settings_auto_gmail_sync_interval, intervalHours)

        binding.switchGmailAutoSync.setOnCheckedChangeListener { _, checked ->
            prefs.autoGmailSyncEnabled = checked
            if (checked) scheduleGmailSync() else cancelGmailSync()
        }
    }

    private fun scheduleGmailSync() {
        val request = PeriodicWorkRequestBuilder<GmailSyncWorker>(
            prefs.autoGmailSyncIntervalHours.toLong(), TimeUnit.HOURS
        ).setConstraints(
            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        ).build()
        WorkManager.getInstance(requireContext()).enqueueUniquePeriodicWork(
            "gmail_auto_sync", ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE, request
        )
    }

    private fun cancelGmailSync() {
        WorkManager.getInstance(requireContext()).cancelUniqueWork("gmail_auto_sync")
    }

    private fun updateGmailSection() {
        val enabled = prefs.gmailSyncEnabled
        binding.cardGmailAccount.visibility = if (enabled) View.VISIBLE else View.GONE
        binding.cardGmailSync.visibility = View.GONE
        if (!enabled) return

        val emails = GoogleAuthManager.getGmailAccountEmails(requireContext())
        binding.llGmailAccounts.removeAllViews()
        emails.forEach { email ->
            addAccountRow(binding.llGmailAccounts, email) {
                GoogleAuthManager.removeGmailAccountEmail(requireContext(), email)
                updateGmailSection()
            }
        }

        if (emails.isNotEmpty()) {
            binding.cardGmailSync.visibility = View.VISIBLE
            binding.tvGmailSyncStatus.text = prefs.lastGmailSyncSummary.ifEmpty {
                getString(R.string.settings_gmail_no_sync)
            }
        }
    }

    // ── Classroom 作業同步 ────────────────────────────────────────────────────

    private fun setupClassroomSection() {
        binding.switchClassroomSync.isChecked = prefs.classroomSyncEnabled
        updateClassroomSection()

        binding.switchClassroomSync.setOnCheckedChangeListener { _, checked ->
            prefs.classroomSyncEnabled = checked
            updateClassroomSection()
        }

        binding.btnClassroomAuth.setSize(SignInButton.SIZE_WIDE)
        binding.btnClassroomAuth.setOnClickListener {
            classroomAccountPickerLauncher.launch(GoogleAuthManager.getAccountPickerIntent())
        }

        setupClassroomAutoSync()

        binding.btnClassroomSyncNow.setOnClickListener {
            val emails = GoogleAuthManager.getClassroomAccountEmails(requireContext())
            if (emails.isEmpty()) return@setOnClickListener
            binding.btnClassroomSyncNow.isEnabled = false
            viewLifecycleOwner.lifecycleScope.launch {
                val feature = FeatureManager.getSync(requireContext(), "google")
                if (feature == null) {
                    binding.btnClassroomSyncNow.isEnabled = true
                    Toast.makeText(requireContext(), "Google 功能模組未安裝，請下載", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val bridge = SyncBridgeImpl(requireContext())
                var totalImported = 0
                var totalSkipped = 0
                for (email in emails) {
                    when (val result = feature.sync("classroom", bridge)) {
                        is SyncOutcome.Success -> {
                            totalImported += result.imported
                            totalSkipped += result.skipped
                        }
                        is SyncOutcome.AuthRequired ->
                            Toast.makeText(requireContext(), "Google 帳號授權已過期，請重新登入", Toast.LENGTH_SHORT).show()
                        is SyncOutcome.Error ->
                            Toast.makeText(requireContext(), result.message, Toast.LENGTH_LONG).show()
                        is SyncOutcome.NoPermission ->
                            Toast.makeText(requireContext(), "Google 帳號授權已過期，請重新登入", Toast.LENGTH_SHORT).show()
                    }
                }
                binding.btnClassroomSyncNow.isEnabled = true
                val summary = getString(R.string.settings_classroom_sync_result, totalImported, totalSkipped)
                prefs.lastClassroomSyncSummary = summary
                binding.tvClassroomSyncStatus.text = summary
            }
        }
    }

    private fun setupClassroomAutoSync() {
        val intervalHours = prefs.autoClassroomSyncIntervalHours
        binding.switchClassroomAutoSync.isChecked = prefs.autoClassroomSyncEnabled
        binding.tvAutoClassroomSyncInterval.text =
            getString(R.string.settings_auto_classroom_sync_interval, intervalHours)

        binding.switchClassroomAutoSync.setOnCheckedChangeListener { _, checked ->
            prefs.autoClassroomSyncEnabled = checked
            if (checked) scheduleClassroomSync() else cancelClassroomSync()
        }
    }

    private fun scheduleClassroomSync() {
        val request = PeriodicWorkRequestBuilder<ClassroomSyncWorker>(
            prefs.autoClassroomSyncIntervalHours.toLong(), TimeUnit.HOURS
        ).setConstraints(
            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        ).build()
        WorkManager.getInstance(requireContext()).enqueueUniquePeriodicWork(
            "classroom_auto_sync", ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE, request
        )
    }

    private fun cancelClassroomSync() {
        WorkManager.getInstance(requireContext()).cancelUniqueWork("classroom_auto_sync")
    }

    private fun updateClassroomSection() {
        val enabled = prefs.classroomSyncEnabled
        binding.cardClassroomAccount.visibility = if (enabled) View.VISIBLE else View.GONE
        binding.cardClassroomSync.visibility = View.GONE
        if (!enabled) return

        val emails = GoogleAuthManager.getClassroomAccountEmails(requireContext())
        binding.llClassroomAccounts.removeAllViews()
        emails.forEach { email ->
            addAccountRow(binding.llClassroomAccounts, email) {
                GoogleAuthManager.removeClassroomAccountEmail(requireContext(), email)
                updateClassroomSection()
            }
        }

        if (emails.isNotEmpty()) {
            binding.cardClassroomSync.visibility = View.VISIBLE
            binding.tvClassroomSyncStatus.text = prefs.lastClassroomSyncSummary.ifEmpty {
                getString(R.string.settings_classroom_no_sync)
            }
        }
    }

    // ── Google 日曆同步 ────────────────────────────────────────────────────────

    private fun setupCalendarSection() {
        binding.switchCalendarSync.isChecked = prefs.calendarSyncEnabled
        updateCalendarSection()

        binding.switchCalendarSync.setOnCheckedChangeListener { _, checked ->
            prefs.calendarSyncEnabled = checked
            updateCalendarSection()
        }

        binding.btnCalendarSignIn.setSize(SignInButton.SIZE_WIDE)
        binding.btnCalendarSignIn.setOnClickListener {
            calendarAccountPickerLauncher.launch(GoogleAuthManager.getAccountPickerIntent())
        }

        binding.switchCalendarAutoSync.isChecked = prefs.autoCalendarSyncEnabled
        binding.tvAutoCalendarSyncInterval.text =
            getString(R.string.settings_auto_calendar_sync_interval, prefs.autoCalendarSyncIntervalHours)

        binding.switchCalendarAutoSync.setOnCheckedChangeListener { _, checked ->
            prefs.autoCalendarSyncEnabled = checked
            if (checked) scheduleCalendarSync() else cancelCalendarSync()
        }

        binding.btnCalendarSyncNow.setOnClickListener {
            val emails = GoogleAuthManager.getCalendarAccountEmails(requireContext())
            if (emails.isEmpty()) return@setOnClickListener
            binding.btnCalendarSyncNow.isEnabled = false
            viewLifecycleOwner.lifecycleScope.launch {
                val feature = FeatureManager.getSync(requireContext(), "google")
                if (feature == null) {
                    binding.btnCalendarSyncNow.isEnabled = true
                    Toast.makeText(requireContext(), "Google 功能模組未安裝，請下載", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val bridge = SyncBridgeImpl(requireContext())
                var totalImported = 0
                var totalSkipped = 0
                for (email in emails) {
                    when (val result = feature.sync("calendar", bridge)) {
                        is SyncOutcome.Success -> {
                            totalImported += result.imported
                            totalSkipped += result.skipped
                        }
                        is SyncOutcome.AuthRequired ->
                            Toast.makeText(requireContext(), "Google 帳號授權已過期，請重新登入", Toast.LENGTH_SHORT).show()
                        is SyncOutcome.Error ->
                            Toast.makeText(requireContext(), result.message, Toast.LENGTH_LONG).show()
                        is SyncOutcome.NoPermission ->
                            Toast.makeText(requireContext(), "Google 帳號授權已過期，請重新登入", Toast.LENGTH_SHORT).show()
                    }
                }
                binding.btnCalendarSyncNow.isEnabled = true
                val summary = getString(R.string.settings_calendar_sync_result, totalImported, totalSkipped)
                prefs.lastCalendarSyncSummary = summary
                binding.tvCalendarSyncStatus.text = summary
            }
        }
    }

    private fun scheduleCalendarSync() {
        val request = PeriodicWorkRequestBuilder<CalendarSyncWorker>(
            prefs.autoCalendarSyncIntervalHours.toLong(), TimeUnit.HOURS
        ).setConstraints(
            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        ).build()
        WorkManager.getInstance(requireContext()).enqueueUniquePeriodicWork(
            "calendar_auto_sync", ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE, request
        )
    }

    private fun cancelCalendarSync() {
        WorkManager.getInstance(requireContext()).cancelUniqueWork("calendar_auto_sync")
    }

    private fun updateCalendarSection() {
        val enabled = prefs.calendarSyncEnabled
        binding.cardCalendarAccount.visibility = if (enabled) View.VISIBLE else View.GONE
        binding.cardCalendarSync.visibility = View.GONE
        if (!enabled) return

        val emails = GoogleAuthManager.getCalendarAccountEmails(requireContext())
        binding.llCalendarAccounts.removeAllViews()
        emails.forEach { email ->
            addAccountRow(binding.llCalendarAccounts, email) {
                GoogleAuthManager.removeCalendarAccountEmail(requireContext(), email)
                updateCalendarSection()
            }
        }

        if (emails.isNotEmpty()) {
            binding.cardCalendarSync.visibility = View.VISIBLE
            binding.tvCalendarSyncStatus.text = prefs.lastCalendarSyncSummary.ifEmpty {
                getString(R.string.settings_calendar_no_sync)
            }
        }
    }

    // ── Google Tasks 同步 ─────────────────────────────────────────────────────

    private fun setupTasksSection() {
        binding.switchTasksSync.isChecked = prefs.tasksSyncEnabled
        updateTasksSection()

        binding.switchTasksSync.setOnCheckedChangeListener { _, checked ->
            prefs.tasksSyncEnabled = checked
            updateTasksSection()
        }

        binding.btnTasksSignIn.setSize(SignInButton.SIZE_WIDE)
        binding.btnTasksSignIn.setOnClickListener {
            tasksAccountPickerLauncher.launch(GoogleAuthManager.getAccountPickerIntent())
        }

        binding.switchTasksAutoSync.isChecked = prefs.autoTasksSyncEnabled
        binding.tvAutoTasksSyncInterval.text =
            getString(R.string.settings_auto_tasks_sync_interval, prefs.autoTasksSyncIntervalHours)

        binding.switchTasksAutoSync.setOnCheckedChangeListener { _, checked ->
            prefs.autoTasksSyncEnabled = checked
            if (checked) scheduleTasksSync() else cancelTasksSync()
        }

        binding.btnTasksSyncNow.setOnClickListener {
            val emails = GoogleAuthManager.getTasksAccountEmails(requireContext())
            if (emails.isEmpty()) return@setOnClickListener
            binding.btnTasksSyncNow.isEnabled = false
            viewLifecycleOwner.lifecycleScope.launch {
                val feature = FeatureManager.getSync(requireContext(), "google")
                if (feature == null) {
                    binding.btnTasksSyncNow.isEnabled = true
                    Toast.makeText(requireContext(), "Google 功能模組未安裝，請下載", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val bridge = SyncBridgeImpl(requireContext())
                var totalImported = 0
                var totalSkipped = 0
                for (email in emails) {
                    when (val result = feature.sync("tasks", bridge)) {
                        is SyncOutcome.Success -> {
                            totalImported += result.imported
                            totalSkipped += result.skipped
                        }
                        is SyncOutcome.AuthRequired ->
                            Toast.makeText(requireContext(), "Google 帳號授權已過期，請重新登入", Toast.LENGTH_SHORT).show()
                        is SyncOutcome.Error ->
                            Toast.makeText(requireContext(), result.message, Toast.LENGTH_LONG).show()
                        is SyncOutcome.NoPermission ->
                            Toast.makeText(requireContext(), "Google 帳號授權已過期，請重新登入", Toast.LENGTH_SHORT).show()
                    }
                }
                binding.btnTasksSyncNow.isEnabled = true
                val summary = getString(R.string.settings_tasks_sync_result, totalImported, totalSkipped)
                prefs.lastTasksSyncSummary = summary
                binding.tvTasksSyncStatus.text = summary
            }
        }
    }

    private fun scheduleTasksSync() {
        val request = PeriodicWorkRequestBuilder<TasksSyncWorker>(
            prefs.autoTasksSyncIntervalHours.toLong(), TimeUnit.HOURS
        ).setConstraints(
            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        ).build()
        WorkManager.getInstance(requireContext()).enqueueUniquePeriodicWork(
            "tasks_auto_sync", ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE, request
        )
    }

    private fun cancelTasksSync() {
        WorkManager.getInstance(requireContext()).cancelUniqueWork("tasks_auto_sync")
    }

    private fun updateTasksSection() {
        val enabled = prefs.tasksSyncEnabled
        binding.cardTasksAccount.visibility = if (enabled) View.VISIBLE else View.GONE
        binding.cardTasksSync.visibility = View.GONE
        if (!enabled) return

        val emails = GoogleAuthManager.getTasksAccountEmails(requireContext())
        binding.llTasksAccounts.removeAllViews()
        emails.forEach { email ->
            addAccountRow(binding.llTasksAccounts, email) {
                GoogleAuthManager.removeTasksAccountEmail(requireContext(), email)
                updateTasksSection()
            }
        }

        if (emails.isNotEmpty()) {
            binding.cardTasksSync.visibility = View.VISIBLE
            binding.tvTasksSyncStatus.text = prefs.lastTasksSyncSummary.ifEmpty {
                getString(R.string.settings_tasks_no_sync)
            }
        }
    }

    // ── Google Keep 同步 ──────────────────────────────────────────────────────

    private fun setupKeepSection() {
        binding.switchKeepSync.isChecked = prefs.keepSyncEnabled
        updateKeepSection()

        binding.switchKeepSync.setOnCheckedChangeListener { _, checked ->
            prefs.keepSyncEnabled = checked
            updateKeepSection()
        }

        binding.btnKeepAuth.setOnClickListener {
            keepAccountPickerLauncher.launch(GoogleAuthManager.getAccountPickerIntent())
        }

        binding.switchKeepAutoSync.isChecked = prefs.autoKeepSyncEnabled
        binding.tvAutoKeepSyncInterval.text =
            getString(R.string.settings_auto_keep_sync_interval, prefs.autoKeepSyncIntervalHours)

        binding.switchKeepAutoSync.setOnCheckedChangeListener { _, checked ->
            prefs.autoKeepSyncEnabled = checked
            if (checked) scheduleKeepSync() else cancelKeepSync()
        }

        binding.btnKeepSyncNow.setOnClickListener {
            val emails = GoogleAuthManager.getKeepAccountEmails(requireContext())
            if (emails.isEmpty()) return@setOnClickListener
            binding.btnKeepSyncNow.isEnabled = false
            viewLifecycleOwner.lifecycleScope.launch {
                val feature = FeatureManager.getSync(requireContext(), "google")
                if (feature == null) {
                    binding.btnKeepSyncNow.isEnabled = true
                    Toast.makeText(requireContext(), "Google 功能模組未安裝，請下載", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val bridge = SyncBridgeImpl(requireContext())
                var totalImported = 0
                var totalSkipped = 0
                for (email in emails) {
                    when (val result = feature.sync("keep", bridge)) {
                        is SyncOutcome.Success -> {
                            totalImported += result.imported
                            totalSkipped += result.skipped
                        }
                        is SyncOutcome.AuthRequired ->
                            Toast.makeText(requireContext(), "Google 帳號授權已過期，請重新登入", Toast.LENGTH_SHORT).show()
                        is SyncOutcome.Error ->
                            Toast.makeText(requireContext(), result.message, Toast.LENGTH_LONG).show()
                        is SyncOutcome.NoPermission ->
                            Toast.makeText(requireContext(), "Google 帳號授權已過期，請重新登入", Toast.LENGTH_SHORT).show()
                    }
                }
                binding.btnKeepSyncNow.isEnabled = true
                val summary = getString(R.string.settings_keep_sync_result, totalImported, totalSkipped)
                prefs.lastKeepSyncSummary = summary
                binding.tvKeepSyncStatus.text = summary
            }
        }
    }

    private fun scheduleKeepSync() {
        val request = PeriodicWorkRequestBuilder<KeepSyncWorker>(
            prefs.autoKeepSyncIntervalHours.toLong(), TimeUnit.HOURS
        ).setConstraints(
            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        ).build()
        WorkManager.getInstance(requireContext()).enqueueUniquePeriodicWork(
            "keep_auto_sync", ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE, request
        )
    }

    private fun cancelKeepSync() {
        WorkManager.getInstance(requireContext()).cancelUniqueWork("keep_auto_sync")
    }

    private fun updateKeepSection() {
        val enabled = prefs.keepSyncEnabled
        binding.cardKeepAccount.visibility = if (enabled) View.VISIBLE else View.GONE
        binding.cardKeepSync.visibility = View.GONE
        if (!enabled) return

        val emails = GoogleAuthManager.getKeepAccountEmails(requireContext())
        binding.llKeepAccounts.removeAllViews()
        emails.forEach { email ->
            addAccountRow(binding.llKeepAccounts, email) {
                GoogleAuthManager.removeKeepAccountEmail(requireContext(), email)
                updateKeepSection()
            }
        }

        if (emails.isNotEmpty()) {
            binding.cardKeepSync.visibility = View.VISIBLE
            binding.tvKeepSyncStatus.text = prefs.lastKeepSyncSummary.ifEmpty {
                getString(R.string.settings_keep_no_sync)
            }
        }
    }

    private fun showRestoreOptionsDialog(
        feature: com.rendy.classnote.feature.BackupFeature,
        bridge: SyncBridgeImpl,
        meta: com.rendy.classnote.feature.BackupMeta?
    ) {
        val items = mutableListOf<String>()
        val checked = mutableListOf<Boolean>()

        items.add("筆記 & 提醒事項")
        checked.add(true)
        if (meta?.hasAiSettings == true) { items.add("AI 設定（API 金鑰）"); checked.add(true) }
        if (meta?.hasWeatherSettings == true) { items.add("天氣設定"); checked.add(true) }

        val checkedArr = checked.toBooleanArray()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("選擇要還原的項目")
            .setMultiChoiceItems(items.toTypedArray(), checkedArr) { _, which, isChecked ->
                checkedArr[which] = isChecked
            }
            .setPositiveButton("還原") { _, _ ->
                val aiIdx = if (meta?.hasAiSettings == true) 1 else -1
                val weatherIdx = when {
                    meta?.hasAiSettings == true && meta.hasWeatherSettings -> 2
                    meta?.hasAiSettings != true && meta?.hasWeatherSettings == true -> 1
                    else -> -1
                }
                val options = com.rendy.classnote.feature.RestoreOptions(
                    restoreNotes = checkedArr[0],
                    restoreAiSettings = aiIdx >= 0 && checkedArr[aiIdx],
                    restoreWeatherSettings = weatherIdx >= 0 && checkedArr[weatherIdx]
                )
                binding.btnGoogleRestore.isEnabled = false
                viewLifecycleOwner.lifecycleScope.launch {
                    val result = feature.restore(bridge, options)
                    binding.btnGoogleRestore.isEnabled = true
                    when (result) {
                        is BackupOutcome.Success -> {
                            autoDownloadMissingFeatures(result.meta.installedFeatures)
                            restartApp()
                        }
                        is BackupOutcome.AuthRequired -> {
                            val restoreIntent = result.intent
                            if (restoreIntent != null) reAuthLauncher.launch(restoreIntent)
                            else Toast.makeText(requireContext(), "帳號授權已過期", Toast.LENGTH_SHORT).show()
                        }
                        is BackupOutcome.Error ->
                            Toast.makeText(requireContext(), result.message, Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private suspend fun autoDownloadMissingFeatures(featureIds: List<String>) {
        if (featureIds.isEmpty()) return
        val ctx = requireContext()
        val appVersion = try {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).longVersionCode.toInt()
        } catch (_: Exception) { 0 }
        val manifest = com.rendy.classnote.data.FeatureDownloader.fetchManifest()
        val infoMap = manifest.associateBy { it.id }
        for (id in featureIds) {
            if (FeatureManager.isDownloaded(ctx, id)) continue
            val info = infoMap[id] ?: continue
            com.rendy.classnote.data.FeatureDownloader.download(ctx, info, appVersion)
        }
    }

    private fun restartApp() {
        val ctx = requireContext().applicationContext
        val intent = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)?.apply {
            addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(intent)
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

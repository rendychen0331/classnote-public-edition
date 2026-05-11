package com.rendy.classnote.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.rendy.classnote.R
import com.rendy.classnote.data.FeatureManager
import com.rendy.classnote.databinding.SheetBackupSyncBinding

class BackupSyncSheet : Fragment() {

    private var _binding: SheetBackupSyncBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = SheetBackupSyncBinding.inflate(inflater, container, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.cardMenuGoogle.setOnClickListener {
            findNavController().navigate(R.id.actionBackupSyncToGoogleSync)
        }
        binding.cardMenuMicrosoft.setOnClickListener {
            findNavController().navigate(R.id.actionBackupSyncToMicrosoftSync)
        }
        binding.cardMenuFeatureModules.setOnClickListener {
            findNavController().navigate(R.id.actionBackupSyncToFeatureModules)
        }
    }

    override fun onResume() {
        super.onResume()
        val ctx = requireContext()
        val google = FeatureManager.isDownloaded(ctx, "google")
        val ms = FeatureManager.isDownloaded(ctx, "microsoft")
        val installed = listOfNotNull(
            if (google) "Google" else null,
            if (ms) "Microsoft" else null
        )
        binding.tvFeatureModuleStatus.text = if (installed.isEmpty())
            "尚未下載任何功能模組"
        else
            "已安裝：${installed.joinToString("、")}"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "BackupSyncSheet"
    }
}

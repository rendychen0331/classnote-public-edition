package com.rendy.classnote.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.rendy.classnote.R
import com.rendy.classnote.databinding.FragmentSyncBinding

class SyncFragment : Fragment() {

    private var _binding: FragmentSyncBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = FragmentSyncBinding.inflate(inflater, container, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.cardSyncCloud.setOnClickListener {
            findNavController().navigate(R.id.actionSyncToBackupSync)
        }

        binding.cardSyncLocal.setOnClickListener {
            findNavController().navigate(R.id.actionSyncToLocalSync)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

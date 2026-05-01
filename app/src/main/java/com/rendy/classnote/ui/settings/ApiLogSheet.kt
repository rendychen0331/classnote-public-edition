package com.rendy.classnote.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.fragment.app.Fragment
import com.rendy.classnote.ClassNoteApplication
import com.rendy.classnote.databinding.SheetApiLogBinding
import kotlinx.coroutines.launch

class ApiLogSheet : Fragment() {

    private var _binding: SheetApiLogBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = SheetApiLogBinding.inflate(inflater, container, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadApiLogs()

        binding.btnClearApiLogs.setOnClickListener {
            val app = requireActivity().application as ClassNoteApplication
            viewLifecycleOwner.lifecycleScope.launch {
                app.database.apiLogDao().clearAll()
                Toast.makeText(requireContext(), "已清除", Toast.LENGTH_SHORT).show()
                loadApiLogs()
            }
        }
    }

    private fun loadApiLogs() {
        val app = requireActivity().application as ClassNoteApplication
        viewLifecycleOwner.lifecycleScope.launch {
            val logs = app.database.apiLogDao().getRecentLogs()
            if (logs.isEmpty()) {
                binding.rvApiLogs.visibility = View.GONE
                binding.tvApiLogsEmpty.visibility = View.VISIBLE
            } else {
                binding.tvApiLogsEmpty.visibility = View.GONE
                binding.rvApiLogs.visibility = View.VISIBLE
                binding.rvApiLogs.adapter = ApiLogAdapter(logs)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "ApiLogSheet"
    }
}

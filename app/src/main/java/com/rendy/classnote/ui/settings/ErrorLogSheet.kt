package com.rendy.classnote.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.rendy.classnote.ClassNoteApplication
import com.rendy.classnote.databinding.SheetErrorLogBinding
import kotlinx.coroutines.launch

class ErrorLogSheet : Fragment() {

    private var _binding: SheetErrorLogBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = SheetErrorLogBinding.inflate(inflater, container, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.rvErrorLogs.layoutManager = LinearLayoutManager(requireContext())
        loadLogs()

        binding.btnClearErrorLogs.setOnClickListener {
            val app = requireActivity().application as ClassNoteApplication
            viewLifecycleOwner.lifecycleScope.launch {
                app.database.errorLogDao().clearAll()
                Toast.makeText(requireContext(), "已清除", Toast.LENGTH_SHORT).show()
                loadLogs()
            }
        }
    }

    private fun loadLogs() {
        val app = requireActivity().application as ClassNoteApplication
        viewLifecycleOwner.lifecycleScope.launch {
            val logs = app.database.errorLogDao().getRecentLogs()
            if (logs.isEmpty()) {
                binding.rvErrorLogs.visibility = View.GONE
                binding.tvErrorLogsEmpty.visibility = View.VISIBLE
            } else {
                binding.tvErrorLogsEmpty.visibility = View.GONE
                binding.rvErrorLogs.visibility = View.VISIBLE
                binding.rvErrorLogs.adapter = ErrorLogAdapter(logs)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

package com.rendy.classnote.ui.reminder

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.rendy.classnote.ClassNoteApplication
import com.rendy.classnote.databinding.FragmentReminderListBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ReminderListFragment : Fragment() {

    private var _binding: FragmentReminderListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ReminderViewModel by viewModels {
        val app = requireActivity().application as ClassNoteApplication
        ReminderViewModel.Factory(app.reminderRepository, app.applicationContext)
    }

    private lateinit var adapter: ReminderAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReminderListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = ReminderAdapter(
            onComplete = { reminder -> viewModel.completeReminder(reminder.id) },
            onEdit = { reminder ->
                findNavController().navigate(
                    ReminderListFragmentDirections
                        .actionReminderListFragmentToReminderEditFragment(reminder.id)
                )
            },
            onDelete = { reminder -> viewModel.deleteReminder(reminder) }
        )

        binding.recyclerReminders.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@ReminderListFragment.adapter
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.activeReminders.collectLatest { reminders ->
                adapter.submitList(reminders)
                binding.tvEmpty.visibility =
                    if (reminders.isEmpty()) View.VISIBLE else View.GONE
            }
        }

        binding.fabAddReminder.setOnClickListener {
            findNavController().navigate(
                ReminderListFragmentDirections
                    .actionReminderListFragmentToReminderEditFragment(-1L)
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

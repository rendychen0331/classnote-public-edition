package com.rendy.classnote.ui.formula

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rendy.classnote.ClassNoteApplication
import com.rendy.classnote.R
import com.rendy.classnote.databinding.FragmentFormulaListBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class FormulaListFragment : Fragment() {

    private var _binding: FragmentFormulaListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: FormulaViewModel by viewModels {
        val app = requireActivity().application as ClassNoteApplication
        FormulaViewModel.Factory(app.formulaRepository)
    }

    private lateinit var adapter: FormulaAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFormulaListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (requireActivity().application as ClassNoteApplication).warmFormulaEditor()

        adapter = FormulaAdapter(
            onClick = { formula ->
                findNavController().navigate(
                    FormulaListFragmentDirections
                        .actionFormulaListFragmentToFormulaEditFragment(formula.id)
                )
            },
            onDelete = { formula ->
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.confirm_delete)
                    .setMessage(R.string.confirm_delete_message)
                    .setPositiveButton(R.string.delete) { _, _ -> viewModel.delete(formula) }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
        )

        binding.recyclerFormulas.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@FormulaListFragment.adapter
        }

        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = false
            override fun onQueryTextChange(newText: String?): Boolean {
                viewModel.setSearch(newText.orEmpty())
                return true
            }
        })

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.formulas.collectLatest { formulas ->
                adapter.submitList(formulas)
                binding.tvEmpty.visibility = if (formulas.isEmpty()) View.VISIBLE else View.GONE
            }
        }

        binding.fabAddFormula.setOnClickListener {
            findNavController().navigate(
                FormulaListFragmentDirections
                    .actionFormulaListFragmentToFormulaEditFragment(-1L)
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

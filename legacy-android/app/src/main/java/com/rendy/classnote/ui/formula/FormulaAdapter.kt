package com.rendy.classnote.ui.formula

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.rendy.classnote.data.local.entity.FormulaEntity
import com.rendy.classnote.databinding.ItemFormulaBinding

class FormulaAdapter(
    private val onClick: (FormulaEntity) -> Unit
) : ListAdapter<FormulaEntity, FormulaAdapter.ViewHolder>(DIFF) {

    inner class ViewHolder(private val binding: ItemFormulaBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(formula: FormulaEntity) {
            binding.tvFormulaTitle.text = formula.title
            binding.tvFormulaLatex.text = formula.latex
            binding.tvFormulaExplanation.text = formula.explanation.ifBlank { null }
                .also { binding.tvFormulaExplanation.visibility =
                    if (it == null) android.view.View.GONE else android.view.View.VISIBLE }

            if (formula.subject.isNotBlank()) {
                binding.chipSubject.text = formula.subject
                binding.chipSubject.visibility = android.view.View.VISIBLE
            } else {
                binding.chipSubject.visibility = android.view.View.GONE
            }

            binding.root.setOnClickListener { onClick(formula) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFormulaBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<FormulaEntity>() {
            override fun areItemsTheSame(a: FormulaEntity, b: FormulaEntity) = a.id == b.id
            override fun areContentsTheSame(a: FormulaEntity, b: FormulaEntity) = a == b
        }
    }
}

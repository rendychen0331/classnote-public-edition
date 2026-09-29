package com.rendy.classnote.ui.settings

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.rendy.classnote.data.local.entity.ErrorLogEntity
import com.rendy.classnote.databinding.ItemErrorLogBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ErrorLogAdapter(private val items: List<ErrorLogEntity>) :
    RecyclerView.Adapter<ErrorLogAdapter.ViewHolder>() {

    private val sdf = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault())

    inner class ViewHolder(val binding: ItemErrorLogBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemErrorLogBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.binding.tvErrorTime.text = sdf.format(Date(item.timestamp))
        holder.binding.tvErrorTag.text = item.tag
        holder.binding.tvErrorMessage.text = item.message
        if (item.stacktrace.isBlank()) {
            holder.binding.tvErrorStacktrace.visibility = android.view.View.GONE
        } else {
            holder.binding.tvErrorStacktrace.visibility = android.view.View.VISIBLE
            holder.binding.tvErrorStacktrace.text = item.stacktrace
        }
    }
}

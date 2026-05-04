package com.rendy.classnote.ui.classrecord

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.rendy.classnote.data.local.entity.ClassRecordEntity
import com.rendy.classnote.databinding.ItemClassRecordBinding
import com.rendy.classnote.databinding.ItemClassRecordHeaderBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

sealed class ClassRecordListItem {
    data class Header(val date: String, val timeLabel: String, val count: Int) : ClassRecordListItem()
    data class Record(val entity: ClassRecordEntity, val firstPhotoPath: String? = null) : ClassRecordListItem()
}

class ClassRecordAdapter(
    private val onClick: (ClassRecordEntity) -> Unit
) : ListAdapter<ClassRecordListItem, RecyclerView.ViewHolder>(DiffCallback) {

    inner class HeaderViewHolder(private val binding: ItemClassRecordHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ClassRecordListItem.Header) {
            val dowStr = runCatching {
                val d = LocalDate.parse(item.date, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                "（${d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.TRADITIONAL_CHINESE)}）"
            }.getOrDefault("")
            val timeStr = if (item.timeLabel.isNotBlank()) "  ${item.timeLabel}" else ""
            binding.tvSessionDate.text = "${item.date}$dowStr$timeStr"
            binding.tvSessionCount.text = "${item.count} 筆"
        }
    }

    inner class RecordViewHolder(private val binding: ItemClassRecordBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private var thumbJob: Job? = null

        fun bind(item: ClassRecordEntity, firstPhotoPath: String?) {
            binding.tvRecordDate.visibility = View.GONE

            binding.tvRecordTitle.text = item.title
            binding.tvRecordTitle.visibility = if (item.title.isNotBlank()) View.VISIBLE else View.GONE

            val preview = when {
                item.aiSummary.isNotBlank() -> item.aiSummary.take(80)
                item.textNote.isNotBlank() -> android.text.Html.fromHtml(item.textNote, android.text.Html.FROM_HTML_MODE_COMPACT).toString().trim().take(80)
                else -> ""
            }
            binding.tvRecordNote.text = preview
            binding.tvRecordNote.visibility = if (preview.isNotEmpty()) View.VISIBLE else View.GONE

            thumbJob?.cancel()
            if (firstPhotoPath != null) {
                binding.ivThumbnail.visibility = View.VISIBLE
                binding.ivThumbnail.setImageBitmap(null)
                val owner = itemView.findViewTreeLifecycleOwner()
                if (owner != null) {
                    thumbJob = owner.lifecycleScope.launch {
                        val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                        val bmp = withContext(Dispatchers.IO) {
                            BitmapFactory.decodeFile(firstPhotoPath, opts)
                        }
                        if (bmp != null) {
                            binding.ivThumbnail.setImageBitmap(bmp)
                        } else {
                            binding.ivThumbnail.visibility = View.GONE
                        }
                    }
                } else {
                    binding.ivThumbnail.visibility = View.GONE
                }
            } else {
                binding.ivThumbnail.visibility = View.GONE
            }

            binding.root.setOnClickListener { onClick(item) }
        }
    }

    override fun getItemViewType(position: Int) = when (getItem(position)) {
        is ClassRecordListItem.Header -> VIEW_TYPE_HEADER
        is ClassRecordListItem.Record -> VIEW_TYPE_RECORD
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
        if (viewType == VIEW_TYPE_HEADER) {
            HeaderViewHolder(ItemClassRecordHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        } else {
            RecordViewHolder(ItemClassRecordBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is ClassRecordListItem.Header -> (holder as HeaderViewHolder).bind(item)
            is ClassRecordListItem.Record -> (holder as RecordViewHolder).bind(item.entity, item.firstPhotoPath)
        }
    }

    companion object {
        internal const val VIEW_TYPE_HEADER = 0
        internal const val VIEW_TYPE_RECORD = 1

        val DiffCallback = object : DiffUtil.ItemCallback<ClassRecordListItem>() {
            override fun areItemsTheSame(old: ClassRecordListItem, new: ClassRecordListItem): Boolean =
                when {
                    old is ClassRecordListItem.Header && new is ClassRecordListItem.Header ->
                        old.date == new.date && old.timeLabel == new.timeLabel
                    old is ClassRecordListItem.Record && new is ClassRecordListItem.Record ->
                        old.entity.id == new.entity.id
                    else -> false
                }

            override fun areContentsTheSame(old: ClassRecordListItem, new: ClassRecordListItem) = old == new
        }
    }
}

package com.rendy.classnote.ui.weather

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.rendy.classnote.feature.ForecastItem
import com.rendy.classnote.databinding.ItemForecastBinding

class ForecastAdapter : ListAdapter<ForecastItem, ForecastAdapter.VH>(DIFF) {

    inner class VH(private val binding: ItemForecastBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ForecastItem) {
            binding.tvTime.text = item.timePeriod
            binding.tvDesc.text = item.description
            binding.tvTemp.text = "${item.tempMin}–${item.tempMax}°C"
            binding.tvRain.text = "☔ ${item.rainProb}%"
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemForecastBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<ForecastItem>() {
            override fun areItemsTheSame(a: ForecastItem, b: ForecastItem) =
                a.timePeriod == b.timePeriod
            override fun areContentsTheSame(a: ForecastItem, b: ForecastItem) = a == b
        }
    }
}

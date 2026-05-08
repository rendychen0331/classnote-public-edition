package com.rendy.classnote.notification

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.rendy.classnote.R

class OverlayChatAdapter(
    private val messages: MutableList<ChatMessage>
) : RecyclerView.Adapter<OverlayChatAdapter.VH>() {

    data class ChatMessage(val text: String, val isUser: Boolean)

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val card: MaterialCardView = view.findViewById(R.id.bubble_card)
        val text: TextView = view.findViewById(R.id.tv_chat_message)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_chat_message, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val msg = messages[position]
        holder.text.text = msg.text
        val params = holder.card.layoutParams as FrameLayout.LayoutParams
        if (msg.isUser) {
            params.gravity = Gravity.END
            holder.card.setCardBackgroundColor(0xFF4ECCA3.toInt())
            holder.text.setTextColor(0xFF1A1A2E.toInt())
        } else {
            params.gravity = Gravity.START
            holder.card.setCardBackgroundColor(0xFF2A2A3E.toInt())
            holder.text.setTextColor(0xFFE0E0E0.toInt())
        }
        holder.card.layoutParams = params
    }

    override fun getItemCount() = messages.size
}

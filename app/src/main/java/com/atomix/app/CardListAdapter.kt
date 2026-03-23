package com.atomix.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class CardListAdapter(private val cards: List<String>, private val onCardClick: (String, Boolean) -> Unit) : RecyclerView.Adapter<CardListAdapter.CardViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_card_detail, parent, false)
        return CardViewHolder(view)
    }

    override fun onBindViewHolder(holder: CardViewHolder, position: Int) {
        val cardUID = cards[position]
        val isPrimary = position == 0
        holder.bind(cardUID, isPrimary, onCardClick)
    }

    override fun getItemCount(): Int = cards.size

    class CardViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardUidText: TextView = itemView.findViewById(R.id.cardUidText)
        private val cardTypeText: TextView = itemView.findViewById(R.id.cardTypeText)
        private val cardStatusText: TextView = itemView.findViewById(R.id.cardStatusText)

        fun bind(cardUID: String, isPrimary: Boolean, onCardClick: (String, Boolean) -> Unit) {
            cardUidText.text = cardUID
            cardTypeText.text = if (isPrimary) "Primary Card" else "Additional Card"
            cardStatusText.text = "Active"
            
            if (isPrimary) {
                cardTypeText.setTextColor(itemView.context.getColor(android.R.color.holo_green_light))
            } else {
                cardTypeText.setTextColor(itemView.context.getColor(android.R.color.white))
            }
            
            itemView.setOnClickListener {
                onCardClick(cardUID, isPrimary)
            }
        }
    }
}
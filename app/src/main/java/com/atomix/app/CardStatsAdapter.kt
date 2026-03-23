package com.atomix.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView

class CardStatsAdapter : RecyclerView.Adapter<CardStatsAdapter.StatsViewHolder>() {

    private var stats: CardStats? = null

    fun updateStats(newStats: CardStats) {
        stats = newStats
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StatsViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_card_stat, parent, false)
        return StatsViewHolder(view)
    }

    override fun onBindViewHolder(holder: StatsViewHolder, position: Int) {
        stats?.let { holder.bind(it, position) }
    }

    override fun getItemCount() = if (stats != null) 4 else 0

    inner class StatsViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleText: TextView = itemView.findViewById(R.id.statTitle)
        private val valueText: TextView = itemView.findViewById(R.id.statValue)
        private val cardView: CardView = itemView.findViewById(R.id.statCard)

        fun bind(stats: CardStats, position: Int) {
            when (position) {
                0 -> {
                    titleText.text = "Total Users"
                    valueText.text = stats.totalUsers.toString()
                }
                1 -> {
                    titleText.text = "Total Cards"
                    valueText.text = stats.totalCards.toString()
                }
                2 -> {
                    titleText.text = "Active Users"
                    valueText.text = stats.activeUsers.toString()
                }
                3 -> {
                    titleText.text = "Avg Cards/User"
                    valueText.text = String.format("%.1f", stats.averageCardsPerUser)
                }
            }
        }
    }
}
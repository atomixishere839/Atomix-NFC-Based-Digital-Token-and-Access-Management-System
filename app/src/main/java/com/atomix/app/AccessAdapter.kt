package com.atomix.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView

class AccessAdapter(
    private val accessList: List<RoomAccessItem>,
    private val onAccessClick: (RoomAccessItem) -> Unit
) : RecyclerView.Adapter<AccessAdapter.AccessViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AccessViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_access, parent, false)
        return AccessViewHolder(view)
    }

    override fun onBindViewHolder(holder: AccessViewHolder, position: Int) {
        holder.bind(accessList[position])
    }

    override fun getItemCount(): Int = accessList.size

    inner class AccessViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardView: CardView = itemView.findViewById(R.id.accessCard)
        private val userNameText: TextView = itemView.findViewById(R.id.userNameText)
        private val cardIdText: TextView = itemView.findViewById(R.id.cardIdText)
        private val accessLevelText: TextView = itemView.findViewById(R.id.accessLevelText)
        private val statusText: TextView = itemView.findViewById(R.id.statusText)

        fun bind(access: RoomAccessItem) {
            userNameText.text = access.userName
            cardIdText.text = itemView.context.getString(R.string.card_id_format, access.cardId)
            accessLevelText.text = itemView.context.getString(R.string.access_level_format, access.accessLevel)
            statusText.text = access.status

            // Set status color
            val statusColor = when (access.status) {
                "Active" -> itemView.context.getColor(R.color.dark_success)
                "Expired" -> itemView.context.getColor(R.color.dark_error)
                else -> itemView.context.getColor(R.color.dark_text_secondary)
            }
            statusText.setTextColor(statusColor)

            cardView.setOnClickListener {
                onAccessClick(access)
            }
        }
    }
}
package com.atomix.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView

class SimpleRoomAdapter(
    private val rooms: List<SimpleRoom>,
    private val onRoomClick: (SimpleRoom) -> Unit
) : RecyclerView.Adapter<SimpleRoomAdapter.RoomViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RoomViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_room, parent, false)
        return RoomViewHolder(view)
    }

    override fun onBindViewHolder(holder: RoomViewHolder, position: Int) {
        holder.bind(rooms[position])
    }

    override fun getItemCount(): Int = rooms.size

    inner class RoomViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardView: CardView = itemView.findViewById(R.id.roomCard)
        private val roomNameText: TextView = itemView.findViewById(R.id.roomNameText)
        private val roomIdText: TextView = itemView.findViewById(R.id.roomIdText)
        private val descriptionText: TextView = itemView.findViewById(R.id.descriptionText)
        private val createdAtText: TextView = itemView.findViewById(R.id.createdAtText)
        private val occupancyText: TextView = itemView.findViewById(R.id.occupancyText)

        fun bind(room: SimpleRoom) {
            roomNameText.text = room.roomName
            roomIdText.text = "ID: ${room.roomId}"
            
            if (room.description.isNotEmpty()) {
                descriptionText.text = room.description
                descriptionText.visibility = View.VISIBLE
            } else {
                descriptionText.visibility = View.GONE
            }

            createdAtText.text = "Available for access"
            
            occupancyText.text = "Occupancy: ${room.currentOccupancy} / ${room.maxCapacity}"
            
            // Set occupancy color
            if (room.currentOccupancy >= room.maxCapacity) {
                occupancyText.setTextColor(itemView.context.getColor(android.R.color.holo_red_dark))
            } else {
                occupancyText.setTextColor(itemView.context.getColor(R.color.dark_primary))
            }

            cardView.setOnClickListener {
                onRoomClick(room)
            }
        }
    }
}
package com.atomix.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class RoomAdapter(
    private val onRoomClick: (Room) -> Unit
) : RecyclerView.Adapter<RoomAdapter.RoomViewHolder>() {

    private var rooms = listOf<Room>()

    fun updateRooms(newRooms: List<Room>) {
        rooms = newRooms
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RoomViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_room, parent, false)
        return RoomViewHolder(view)
    }

    override fun onBindViewHolder(holder: RoomViewHolder, position: Int) {
        holder.bind(rooms[position])
    }

    override fun getItemCount() = rooms.size

    inner class RoomViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameText: TextView = itemView.findViewById(R.id.roomNameText)
        private val descText: TextView = itemView.findViewById(R.id.descriptionText)
        private val occupancyText: TextView = itemView.findViewById(R.id.occupancyText)

        fun bind(room: Room) {
            nameText.text = room.name
            descText.text = room.description.ifEmpty { "No description" }
            occupancyText.text = "Occupancy: ${room.currentOccupancy} / ${room.maxCapacity}"
            
            // Highlight if room is full
            if (room.currentOccupancy >= room.maxCapacity) {
                occupancyText.setTextColor(itemView.context.getColor(android.R.color.holo_red_dark))
            } else {
                occupancyText.setTextColor(itemView.context.getColor(R.color.dark_primary))
            }

            itemView.setOnClickListener {
                onRoomClick(room)
            }
        }
    }
}
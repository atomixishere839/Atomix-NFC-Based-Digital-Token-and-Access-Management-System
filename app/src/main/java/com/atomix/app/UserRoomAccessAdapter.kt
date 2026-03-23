package com.atomix.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class UserRoomAccessAdapter(
    private val accessList: List<UserRoomAccess>,
    private val onAccessClick: (UserRoomAccess) -> Unit
) : RecyclerView.Adapter<UserRoomAccessAdapter.AccessViewHolder>() {

    private val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AccessViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_user_room_access, parent, false)
        return AccessViewHolder(view)
    }

    override fun onBindViewHolder(holder: AccessViewHolder, position: Int) {
        holder.bind(accessList[position])
    }

    override fun getItemCount() = accessList.size

    inner class AccessViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardView: CardView = itemView.findViewById(R.id.accessCard)
        private val roomNameText: TextView = itemView.findViewById(R.id.roomNameText)
        private val grantedByText: TextView = itemView.findViewById(R.id.grantedByText)
        private val grantedAtText: TextView = itemView.findViewById(R.id.grantedAtText)

        fun bind(access: UserRoomAccess) {
            roomNameText.text = access.roomName
            grantedByText.text = itemView.context.getString(R.string.granted_by_format, access.grantedBy)
            grantedAtText.text = itemView.context.getString(R.string.on_date_format, dateFormat.format(Date(access.grantedAt)))

            cardView.setOnClickListener {
                onAccessClick(access)
            }
        }
    }
}
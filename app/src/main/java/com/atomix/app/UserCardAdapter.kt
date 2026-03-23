package com.atomix.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView

class UserCardAdapter(private val onUserClick: (RegisteredUser) -> Unit) : 
    RecyclerView.Adapter<UserCardAdapter.UserCardViewHolder>() {

    private var users = listOf<RegisteredUser>()

    fun updateUsers(newUsers: List<RegisteredUser>) {
        users = newUsers
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserCardViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_user_card, parent, false)
        return UserCardViewHolder(view)
    }

    override fun onBindViewHolder(holder: UserCardViewHolder, position: Int) {
        holder.bind(users[position])
    }

    override fun getItemCount() = users.size

    inner class UserCardViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameText: TextView = itemView.findViewById(R.id.userNameText)
        private val cardCountText: TextView = itemView.findViewById(R.id.cardCountText)
        private val primaryCardText: TextView = itemView.findViewById(R.id.primaryCardText)
        private val statusText: TextView = itemView.findViewById(R.id.statusText)
        private val cardView: CardView = itemView.findViewById(R.id.userCardView)

        fun bind(user: RegisteredUser) {
            nameText.text = user.name
            cardCountText.text = "${user.getTotalCards()} Cards"
            primaryCardText.text = "Primary: ${user.primaryCardUID}"
            
            statusText.text = when {
                !user.isActive -> "DEACTIVATED"
                user.isExpired() && user.role != UserRole.ADMIN -> "EXPIRED"
                user.role == UserRole.ADMIN -> "ADMIN"
                else -> "ACTIVE"
            }
            
            val statusColor = when {
                !user.isActive -> itemView.context.getColor(android.R.color.darker_gray)
                user.isExpired() && user.role != UserRole.ADMIN -> itemView.context.getColor(android.R.color.holo_red_dark)
                user.role == UserRole.ADMIN -> itemView.context.getColor(android.R.color.holo_green_dark)
                else -> itemView.context.getColor(android.R.color.holo_blue_dark)
            }
            statusText.setTextColor(statusColor)

            cardView.setOnClickListener { onUserClick(user) }
        }
    }
}
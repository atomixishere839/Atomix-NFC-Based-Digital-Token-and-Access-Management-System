package com.atomix.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class UserAdapter(
    private val onUserClick: (RegisteredUser) -> Unit,
    private val onDeleteClick: (RegisteredUser) -> Unit
) : RecyclerView.Adapter<UserAdapter.UserViewHolder>() {

    private var users = listOf<RegisteredUser>()
    private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

    fun updateUsers(newUsers: List<RegisteredUser>) {
        users = newUsers
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_user, parent, false)
        return UserViewHolder(view)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        holder.bind(users[position])
    }

    override fun getItemCount() = users.size

    inner class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameText: TextView = itemView.findViewById(R.id.nameText)
        private val usernameText: TextView = itemView.findViewById(R.id.usernameText)
        private val cardUidText: TextView = itemView.findViewById(R.id.cardUidText)
        private val roleText: TextView = itemView.findViewById(R.id.roleText)
        private val registrationDateText: TextView = itemView.findViewById(R.id.registrationDateText)
        private val deleteUserButton: Button = itemView.findViewById(R.id.deleteUserButton)

        fun bind(user: RegisteredUser) {
            nameText.text = if (!user.isActive) "${user.name} (DEACTIVATED)" else user.name
            usernameText.text = "@${user.username}"
            cardUidText.text = "Cards: ${user.getTotalCards()} (Primary: ${user.primaryCardUID})"
            roleText.text = user.role.name
            
            // Show status based on active state and expiry
            when {
                !user.isActive -> {
                    registrationDateText.text = "DEACTIVATED"
                    registrationDateText.setTextColor(itemView.context.getColor(android.R.color.darker_gray))
                    itemView.alpha = 0.6f
                }
                user.role == UserRole.ADMIN -> {
                    registrationDateText.text = "Admin - No Expiry"
                    registrationDateText.setTextColor(itemView.context.getColor(android.R.color.holo_green_dark))
                    itemView.alpha = 1.0f
                }
                user.isExpired() -> {
                    registrationDateText.text = "EXPIRED"
                    registrationDateText.setTextColor(itemView.context.getColor(android.R.color.holo_red_dark))
                    itemView.alpha = 1.0f
                }
                else -> {
                    registrationDateText.text = "Expires in: ${user.getTimeRemaining()}"
                    registrationDateText.setTextColor(itemView.context.getColor(android.R.color.holo_orange_dark))
                    itemView.alpha = 1.0f
                }
            }
            
            itemView.setOnClickListener { onUserClick(user) }
            deleteUserButton.setOnClickListener { onDeleteClick(user) }
        }
    }
}
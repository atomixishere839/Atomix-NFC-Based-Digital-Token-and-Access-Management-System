package com.atomix.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

data class UserTransactionGroup(
    val user: RegisteredUser,
    val transactions: List<Transaction>
)

class AdminTransactionAdapter(private val userGroups: List<UserTransactionGroup>) : RecyclerView.Adapter<AdminTransactionAdapter.UserGroupViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserGroupViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_user_transaction_group, parent, false)
        return UserGroupViewHolder(view)
    }

    override fun onBindViewHolder(holder: UserGroupViewHolder, position: Int) {
        holder.bind(userGroups[position])
    }

    override fun getItemCount() = userGroups.size

    class UserGroupViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val userNameText: TextView = itemView.findViewById(R.id.userNameText)
        private val userInfoText: TextView = itemView.findViewById(R.id.userInfoText)
        private val transactionCountText: TextView = itemView.findViewById(R.id.transactionCountText)
        private val transactionsRecyclerView: RecyclerView = itemView.findViewById(R.id.transactionsRecyclerView)
        private val expandButton: TextView = itemView.findViewById(R.id.expandButton)
        
        private var isExpanded = false

        fun bind(userGroup: UserTransactionGroup) {
            val user = userGroup.user
            val transactions = userGroup.transactions
            
            userNameText.text = user.name
            userInfoText.text = "${user.username} • ${user.role.name} • Balance: ${user.balance} Atoms"
            transactionCountText.text = "${transactions.size} transactions"
            
            // Setup transactions recycler view
            val transactionAdapter = DetailedTransactionAdapter(transactions)
            transactionsRecyclerView.layoutManager = LinearLayoutManager(itemView.context)
            transactionsRecyclerView.adapter = transactionAdapter
            
            // Initially collapsed
            transactionsRecyclerView.visibility = if (isExpanded) View.VISIBLE else View.GONE
            expandButton.text = if (isExpanded) "▼" else "▶"
            
            // Toggle expand/collapse
            itemView.setOnClickListener {
                isExpanded = !isExpanded
                transactionsRecyclerView.visibility = if (isExpanded) View.VISIBLE else View.GONE
                expandButton.text = if (isExpanded) "▼" else "▶"
            }
        }
    }
}

class DetailedTransactionAdapter(private val transactions: List<Transaction>) : RecyclerView.Adapter<DetailedTransactionAdapter.DetailedTransactionViewHolder>() {

    private val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DetailedTransactionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_detailed_transaction, parent, false)
        return DetailedTransactionViewHolder(view)
    }

    override fun onBindViewHolder(holder: DetailedTransactionViewHolder, position: Int) {
        holder.bind(transactions[position])
    }

    override fun getItemCount() = transactions.size

    inner class DetailedTransactionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val typeText: TextView = itemView.findViewById(R.id.transactionTypeText)
        private val amountText: TextView = itemView.findViewById(R.id.amountText)
        private val balanceChangeText: TextView = itemView.findViewById(R.id.balanceChangeText)
        private val dateText: TextView = itemView.findViewById(R.id.dateText)
        private val performedByText: TextView = itemView.findViewById(R.id.performedByText)
        private val cardUidText: TextView = itemView.findViewById(R.id.cardUidText)
        private val descriptionText: TextView = itemView.findViewById(R.id.descriptionText)

        fun bind(transaction: Transaction) {
            typeText.text = when (transaction.type) {
                TransactionType.ADD_BALANCE -> "💰 Balance Added"
                TransactionType.SPEND_BALANCE -> "💸 Balance Spent"
                TransactionType.INITIAL_BALANCE -> "🎯 Initial Balance"
                TransactionType.ADMIN_ADJUSTMENT -> "⚙️ Admin Adjustment"
            }

            val sign = if (transaction.type == TransactionType.SPEND_BALANCE) "-" else "+"
            amountText.text = "$sign${transaction.amount} Atoms"
            
            val color = if (transaction.type == TransactionType.SPEND_BALANCE) {
                itemView.context.getColor(android.R.color.holo_red_dark)
            } else {
                itemView.context.getColor(android.R.color.holo_green_dark)
            }
            amountText.setTextColor(color)

            balanceChangeText.text = "${transaction.balanceBefore} → ${transaction.balanceAfter} Atoms"
            dateText.text = dateFormat.format(Date(transaction.timestamp))
            performedByText.text = "By: ${transaction.performedBy}"
            cardUidText.text = "Card: ${transaction.cardUID}"
            descriptionText.text = transaction.description
        }
    }
}
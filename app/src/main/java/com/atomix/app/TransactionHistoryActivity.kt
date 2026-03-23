package com.atomix.app

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class TransactionHistoryActivity : AppCompatActivity() {

    private lateinit var userManager: UserManager
    private lateinit var authManager: AuthManager
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var transactionAdapter: TransactionAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_transaction_history)

        userManager = UserManager(this)
        authManager = AuthManager(this)

        initializeViews()
        setupRecyclerView()
        loadTransactions()
    }

    private fun initializeViews() {
        recyclerView = findViewById(R.id.transactionsRecyclerView)
        emptyView = findViewById(R.id.emptyView)

        findViewById<TextView>(R.id.backButton).setOnClickListener {
            finish()
        }
    }

    private fun setupRecyclerView() {
        transactionAdapter = TransactionAdapter()
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = transactionAdapter
    }

    private fun loadTransactions() {
        CoroutineScope(Dispatchers.Main).launch {
            val currentUser = authManager.getCurrentUser()
            if (currentUser?.role == UserRole.ADMIN) {
                // Admin sees grouped transactions by user
                loadAdminTransactionView()
            } else {
                // Regular users see only their transactions
                val allUsers = userManager.getAllUsers()
                val user = allUsers.find { it.username == currentUser?.username }
                val transactions = user?.let { userManager.getUserTransactions(it.id) } ?: emptyList()
                
                if (transactions.isEmpty()) {
                    recyclerView.visibility = android.view.View.GONE
                    emptyView.visibility = android.view.View.VISIBLE
                } else {
                    recyclerView.visibility = android.view.View.VISIBLE
                    emptyView.visibility = android.view.View.GONE
                    transactionAdapter.updateTransactions(transactions)
                }
            }
        }
    }
    
    private suspend fun loadAdminTransactionView() {
        val allUsers = userManager.getAllUsers()
        val userTransactionGroups = mutableListOf<UserTransactionGroup>()
        
        allUsers.forEach { user ->
            val transactions = userManager.getUserTransactions(user.id)
            if (transactions.isNotEmpty()) {
                userTransactionGroups.add(
                    UserTransactionGroup(
                        user = user,
                        transactions = transactions
                    )
                )
            }
        }
        
        withContext(Dispatchers.Main) {
            if (userTransactionGroups.isEmpty()) {
                recyclerView.visibility = android.view.View.GONE
                emptyView.visibility = android.view.View.VISIBLE
            } else {
                recyclerView.visibility = android.view.View.VISIBLE
                emptyView.visibility = android.view.View.GONE
                
                // Use admin adapter
                val adminAdapter = AdminTransactionAdapter(userTransactionGroups)
                recyclerView.adapter = adminAdapter
            }
        }
    }
}

class TransactionAdapter : RecyclerView.Adapter<TransactionAdapter.TransactionViewHolder>() {

    private var transactions = listOf<Transaction>()
    private val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())

    fun updateTransactions(newTransactions: List<Transaction>) {
        transactions = newTransactions
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): TransactionViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_transaction, parent, false)
        return TransactionViewHolder(view)
    }

    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        holder.bind(transactions[position])
    }

    override fun getItemCount() = transactions.size

    class TransactionViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
        private val typeText: TextView = itemView.findViewById(R.id.transactionTypeText)
        private val amountText: TextView = itemView.findViewById(R.id.amountText)
        private val balanceText: TextView = itemView.findViewById(R.id.balanceText)
        private val dateText: TextView = itemView.findViewById(R.id.dateText)
        private val performedByText: TextView = itemView.findViewById(R.id.performedByText)

        fun bind(transaction: Transaction) {
            typeText.text = when (transaction.type) {
                TransactionType.ADD_BALANCE -> "Balance Added"
                TransactionType.SPEND_BALANCE -> "Balance Spent"
                TransactionType.INITIAL_BALANCE -> "Initial Balance"
                TransactionType.ADMIN_ADJUSTMENT -> "Admin Adjustment"
            }

            val sign = if (transaction.type == TransactionType.SPEND_BALANCE) "-" else "+"
            amountText.text = "$sign${transaction.amount} Atoms"
            
            val color = if (transaction.type == TransactionType.SPEND_BALANCE) {
                itemView.context.getColor(android.R.color.holo_red_dark)
            } else {
                itemView.context.getColor(android.R.color.holo_green_dark)
            }
            amountText.setTextColor(color)

            balanceText.text = "Balance: ${transaction.balanceAfter} Atoms"
            dateText.text = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(transaction.timestamp))
            performedByText.text = "By: ${transaction.performedBy}"
        }
    }
}

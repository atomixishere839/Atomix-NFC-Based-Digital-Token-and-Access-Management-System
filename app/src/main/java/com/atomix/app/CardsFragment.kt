package com.atomix.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CardsFragment : Fragment() {

    private lateinit var userManager: UserManager
    private lateinit var authManager: AuthManager
    private lateinit var cardStatsAdapter: CardStatsAdapter
    private lateinit var userCardAdapter: UserCardAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_cards, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        userManager = UserManager(requireContext())
        authManager = AuthManager(requireContext())
        
        setupViews(view)
        loadData()
    }

    private fun setupViews(view: View) {
        val statsRecyclerView: RecyclerView = view.findViewById(R.id.statsRecyclerView)
        val usersRecyclerView: RecyclerView = view.findViewById(R.id.usersRecyclerView)
        
        // Setup stats
        cardStatsAdapter = CardStatsAdapter()
        statsRecyclerView.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        statsRecyclerView.adapter = cardStatsAdapter
        
        // Setup users with cards
        userCardAdapter = UserCardAdapter { user ->
            showUserCardManagement(user)
        }
        usersRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        usersRecyclerView.adapter = userCardAdapter
    }

    private fun loadData() {
        CoroutineScope(Dispatchers.Main).launch {
            // Load stats
            val stats = userManager.getCardStats()
            cardStatsAdapter.updateStats(stats)
            
            // Load users
            val currentUser = authManager.getCurrentUser()
            val allUsers = userManager.getAllUsers()
            val filteredUsers = if (currentUser?.role == UserRole.ADMIN) {
                allUsers
            } else {
                allUsers.filter { it.username == currentUser?.username }
            }
            userCardAdapter.updateUsers(filteredUsers)
        }
    }
    
    private fun showUserCardManagement(user: RegisteredUser) {
        android.widget.Toast.makeText(requireContext(), 
            "${user.name} has ${user.getTotalCards()} cards", 
            android.widget.Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        loadData()
    }
}

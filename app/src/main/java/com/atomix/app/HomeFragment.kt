package com.atomix.app

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment

class HomeFragment : Fragment() {

    private lateinit var authManager: AuthManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        authManager = AuthManager(requireContext())
        
        setupViews(view)
        loadUserInfo()
    }

    private fun setupViews(view: View) {
        val cardNfc = view.findViewById<CardView>(R.id.cardNfc)
        val cardTokens = view.findViewById<CardView>(R.id.cardTokens)
        val cardRooms = view.findViewById<CardView>(R.id.cardRooms)
        val cardTasks = view.findViewById<CardView>(R.id.cardTasks)
        val cardLinks = view.findViewById<CardView>(R.id.cardLinks)

        cardNfc.setOnClickListener {
            startActivity(Intent(requireContext(), MainActivity::class.java))
        }

        cardTokens.setOnClickListener {
            startActivity(Intent(requireContext(), TransactionHistoryActivity::class.java))
        }

        cardRooms.setOnClickListener {
            startActivity(Intent(requireContext(), RoomAccessActivity::class.java))
        }

        cardTasks.setOnClickListener {
            // Show user management for admin
            val currentUser = authManager.getCurrentUser()
            if (currentUser?.role == UserRole.ADMIN) {
                val intent = Intent(requireContext(), UserManagementActivity::class.java)
                startActivity(intent)
            }
        }

        cardLinks.setOnClickListener {
            startActivity(Intent(requireContext(), SmartLinksActivity::class.java))
        }
    }

    private fun loadUserInfo() {
        val currentUser = authManager.getCurrentUser()
        currentUser?.let {
            view?.findViewById<TextView>(R.id.welcomeText)?.text = 
                getString(R.string.welcome_user, it.username)
            view?.findViewById<TextView>(R.id.roleText)?.text = 
                getString(R.string.role_format, it.role.name)
        }
    }
}
package com.atomix.app

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UserManagementFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var fabAddRoom: FloatingActionButton
    private lateinit var userManager: UserManager
    private lateinit var authManager: AuthManager
    private lateinit var userAdapter: UserAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_user_management, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        userManager = UserManager(requireContext())
        authManager = AuthManager(requireContext())
        
        // Check if current user is admin
        val currentUser = authManager.getCurrentUser()
        if (currentUser?.role != UserRole.ADMIN) {
            Toast.makeText(requireContext(), "Admin access required", Toast.LENGTH_SHORT).show()
            return
        }
        
        initializeViews(view)
        setupRecyclerView()
        loadUsers()
    }

    private fun initializeViews(view: View) {
        recyclerView = view.findViewById(R.id.usersRecyclerView)
        fabAddRoom = view.findViewById(R.id.fabAddRoom)
        
        fabAddRoom.setOnClickListener {
            val intent = Intent(requireContext(), CardRegistrationActivity::class.java)
            intent.putExtra("role", UserRole.USER) // Default to USER role
            startActivity(intent)
        }
    }

    private fun setupRecyclerView() {
        userAdapter = UserAdapter(
            onUserClick = { user ->
                // Show user management options
                showUserOptionsDialog(user)
            },
            onDeleteClick = { user ->
                // Show delete confirmation
                showDeleteUserDialog(user)
            }
        )
        
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = userAdapter
    }

    private fun showDeleteUserDialog(user: RegisteredUser) {
        val currentUser = authManager.getCurrentUser()
        
        // Don't allow deleting yourself
        if (currentUser?.username == user.username) {
            Toast.makeText(requireContext(), "You cannot delete your own account", Toast.LENGTH_SHORT).show()
            return
        }

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Delete User")
            .setMessage("Are you sure you want to completely delete ${user.name}? This will remove all their data, access records, and transaction history. This action cannot be undone.")
            .setPositiveButton("Delete Forever") { _, _ ->
                CoroutineScope(Dispatchers.Main).launch {
                    val success = userManager.deleteUser(user.id)
                    if (success) {
                        Toast.makeText(requireContext(), "${user.name} has been deleted", Toast.LENGTH_SHORT).show()
                        loadUsers() // Refresh list
                    } else {
                        Toast.makeText(requireContext(), "Failed to delete user", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun loadUsers() {
        CoroutineScope(Dispatchers.Main).launch {
            val currentUser = authManager.getCurrentUser()
            val allUsers = userManager.getAllUsers()
            val filteredUsers = if (currentUser?.role == UserRole.ADMIN) {
                // Admins see all users
                allUsers
            } else {
                // Regular users see only themselves
                allUsers.filter { it.username == currentUser?.username }
            }
            userAdapter.updateUsers(filteredUsers)
        }
    }
    
    private fun showUserOptionsDialog(user: RegisteredUser) {
        val options = if (user.role == UserRole.ADMIN) {
            arrayOf("View Details", "Change Password", "Manage Access", "Manage Cards")
        } else {
            val toggleText = if (user.isActive) "Deactivate User" else "Activate User"
            arrayOf("View Details", "Extend Expiry", "Change Password", "Manage Access", "Manage Cards", toggleText)
        }
        
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("${user.name} - ${user.role.name}")
            .setItems(options) { _, which ->
                when {
                    options[which] == "View Details" -> showUserDetails(user)
                    options[which] == "Extend Expiry" -> showExtendExpiryDialog(user)
                    options[which] == "Change Password" -> showChangePasswordDialog(user)
                    options[which] == "Manage Access" -> {
                        val intent = Intent(requireContext(), UserAccessManagementActivity::class.java)
                        intent.putExtra("userId", user.id)
                        intent.putExtra("userName", user.name)
                        startActivity(intent)
                    }
                    options[which] == "Manage Cards" -> {
                        val intent = Intent(requireContext(), UserCardManagementActivity::class.java)
                        intent.putExtra("userId", user.id)
                        intent.putExtra("userName", user.name)
                        startActivity(intent)
                    }
                    options[which] == "Activate User" -> toggleUserActivation(user, true)
                    options[which] == "Deactivate User" -> toggleUserActivation(user, false)
                }
            }
            .show()
    }
    
    private fun showUserDetails(user: RegisteredUser) {
        val details = buildString {
            append("Name: ${user.name}\n")
            append("Username: ${user.username}\n")
            append("Primary Card: ${user.primaryCardUID}\n")
            append("Total Cards: ${user.getAllCardUIDs().size}\n")
            append("Role: ${user.role.name}\n")
            if (user.role == UserRole.ADMIN) {
                append("Expiry: Never")
            } else {
                append("Status: ${if (user.isExpired()) "EXPIRED" else "Active"}\n")
                append("Time Remaining: ${user.getTimeRemaining()}")
            }
        }
        
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("User Details")
            .setMessage(details)
            .setPositiveButton("OK", null)
            .show()
    }
    
    private fun showExtendExpiryDialog(user: RegisteredUser) {
        val options = arrayOf("1 Hour", "6 Hours", "12 Hours", "24 Hours", "7 Days", "30 Days")
        val hours = arrayOf(1, 6, 12, 24, 168, 720)
        
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Extend Expiry for ${user.name}")
            .setItems(options) { _, which ->
                CoroutineScope(Dispatchers.Main).launch {
                    val success = userManager.extendUserExpiry(user.id, hours[which])
                    if (success) {
                        Toast.makeText(requireContext(), "Expiry extended by ${options[which]}", Toast.LENGTH_SHORT).show()
                        loadUsers() // Refresh list
                    } else {
                        Toast.makeText(requireContext(), "Failed to extend expiry", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun toggleUserActivation(user: RegisteredUser, activate: Boolean) {
        val action = if (activate) "activate" else "deactivate"
        val actionCap = if (activate) "Activate" else "Deactivate"
        
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("$actionCap User")
            .setMessage("Are you sure you want to $action ${user.name}?")
            .setPositiveButton(actionCap) { _, _ ->
                CoroutineScope(Dispatchers.Main).launch {
                    val success = if (activate) {
                        userManager.activateUser(user.id)
                    } else {
                        userManager.deactivateUser(user.id)
                    }
                    
                    if (success) {
                        Toast.makeText(requireContext(), "${user.name} has been ${action}d", Toast.LENGTH_SHORT).show()
                        loadUsers() // Refresh list
                    } else {
                        Toast.makeText(requireContext(), "Failed to $action user", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showChangePasswordDialog(user: RegisteredUser) {
        val builder = androidx.appcompat.app.AlertDialog.Builder(requireContext())
        builder.setTitle("Change Password for ${user.name}")

        val container = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 24, 48, 24)
        }

        val newPasswordInput = com.google.android.material.textfield.TextInputEditText(requireContext())
        newPasswordInput.hint = "New Password"
        newPasswordInput.inputType = android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        container.addView(newPasswordInput)

        val confirmPasswordInput = com.google.android.material.textfield.TextInputEditText(requireContext())
        confirmPasswordInput.hint = "Confirm Password"
        confirmPasswordInput.inputType = android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        container.addView(confirmPasswordInput)

        builder.setView(container)

        builder.setPositiveButton("Change") { _, _ ->
            val newPassword = newPasswordInput.text.toString().trim()
            val confirmPassword = confirmPasswordInput.text.toString().trim()

            when {
                newPassword.isEmpty() -> {
                    Toast.makeText(requireContext(), "Password cannot be empty", Toast.LENGTH_SHORT).show()
                }
                newPassword != confirmPassword -> {
                    Toast.makeText(requireContext(), "Passwords do not match", Toast.LENGTH_SHORT).show()
                }
                newPassword.length < 4 -> {
                    Toast.makeText(requireContext(), "Password must be at least 4 characters", Toast.LENGTH_SHORT).show()
                }
                else -> {
                    CoroutineScope(Dispatchers.Main).launch {
                        val success = userManager.changeUserPassword(user.username, newPassword)
                        if (success) {
                            Toast.makeText(requireContext(), "Password changed successfully", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(requireContext(), "Failed to change password", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }

        builder.setNegativeButton("Cancel", null)
        builder.show()
    }

    override fun onResume() {
        super.onResume()
        loadUsers() // Refresh when returning from other activities
    }
}

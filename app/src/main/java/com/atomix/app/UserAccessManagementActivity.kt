package com.atomix.app

import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UserAccessManagementActivity : AppCompatActivity() {

    private lateinit var userManager: UserManager
    private lateinit var authManager: AuthManager
    private lateinit var accessRecyclerView: RecyclerView
    private lateinit var grantAccessButton: Button
    private lateinit var accessAdapter: UserRoomAccessAdapter
    private val userAccessList = mutableListOf<UserRoomAccess>()
    
    private var userId: String = ""
    private var userName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_access_management)
        
        userManager = UserManager(this)
        authManager = AuthManager(this)
        
        userId = intent.getStringExtra("userId") ?: ""
        userName = intent.getStringExtra("userName") ?: ""
        
        initializeViews()
        setupRecyclerView()
        loadUserAccess()
    }
    
    private fun initializeViews() {
        val titleText: TextView = findViewById(R.id.titleText)
        accessRecyclerView = findViewById(R.id.accessRecyclerView)
        grantAccessButton = findViewById(R.id.grantAccessButton)
        
        titleText.text = "Room Access for $userName"
        
        grantAccessButton.setOnClickListener {
            showGrantAccessDialog()
        }

        findViewById<ImageView>(R.id.backButton).setOnClickListener {
            finish()
        }
    }
    
    private fun setupRecyclerView() {
        accessAdapter = UserRoomAccessAdapter(userAccessList) { access ->
            showRevokeAccessDialog(access)
        }
        accessRecyclerView.layoutManager = LinearLayoutManager(this)
        accessRecyclerView.adapter = accessAdapter
    }
    
    private fun loadUserAccess() {
        CoroutineScope(Dispatchers.Main).launch {
            userAccessList.clear()
            val userAccess = userManager.getUserAccess(userId)
            val allRooms = userManager.getAllRooms()
            
            userAccess.forEach { access ->
                val room = allRooms.find { it.id == access.roomId }
                if (room != null) {
                    userAccessList.add(UserRoomAccess(
                        roomId = room.id,
                        roomName = room.name,
                        grantedAt = access.grantedAt,
                        grantedBy = access.grantedBy
                    ))
                }
            }
            accessAdapter.notifyDataSetChanged()
        }
    }
    
    private fun showGrantAccessDialog() {
        CoroutineScope(Dispatchers.Main).launch {
            val allRooms = userManager.getAllRooms()
            val availableRooms = mutableListOf<Room>()
            for (room in allRooms) {
                if (!userManager.hasAccess(userId, room.id)) {
                    availableRooms.add(room)
                }
            }
            
            if (availableRooms.isEmpty()) {
                Toast.makeText(this@UserAccessManagementActivity, "User already has access to all rooms", Toast.LENGTH_SHORT).show()
                return@launch
            }
            
            val roomNames = availableRooms.map { it.name }.toTypedArray()
            
            AlertDialog.Builder(this@UserAccessManagementActivity)
                .setTitle("Grant Room Access")
                .setItems(roomNames) { _, which ->
                    val selectedRoom = availableRooms[which]
                    grantRoomAccess(selectedRoom)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }
    
    private fun grantRoomAccess(room: Room) {
        val currentAdmin = authManager.getCurrentUser()
        if (currentAdmin == null) {
            Toast.makeText(this, "Admin session expired", Toast.LENGTH_SHORT).show()
            return
        }
        
        CoroutineScope(Dispatchers.Main).launch {
            val success = userManager.grantAccess(
                userId = userId,
                roomId = room.id,
                grantedBy = currentAdmin.username
            )
            
            if (success) {
                Toast.makeText(this@UserAccessManagementActivity, "Access granted to ${room.name}", Toast.LENGTH_SHORT).show()
                loadUserAccess()
            } else {
                Toast.makeText(this@UserAccessManagementActivity, "Failed to grant access", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun showRevokeAccessDialog(access: UserRoomAccess) {
        AlertDialog.Builder(this)
            .setTitle("Revoke Access")
            .setMessage("Remove access to ${access.roomName}?")
            .setPositiveButton("Revoke") { _, _ ->
                CoroutineScope(Dispatchers.Main).launch {
                    val success = userManager.revokeAccess(userId, access.roomId)
                    if (success) {
                        Toast.makeText(this@UserAccessManagementActivity, "Access revoked", Toast.LENGTH_SHORT).show()
                        loadUserAccess()
                    } else {
                        Toast.makeText(this@UserAccessManagementActivity, "Failed to revoke access", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}

data class UserRoomAccess(
    val roomId: String,
    val roomName: String,
    val grantedAt: Long,
    val grantedBy: String
)

package com.atomix.app

import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.MifareClassic
import android.nfc.tech.MifareUltralight
import android.nfc.tech.NfcA
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RoomAccessManagementActivity : AppCompatActivity() {

    private lateinit var authManager: AuthManager
    private lateinit var userManager: UserManager
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: LinearLayout
    private lateinit var occupancyText: TextView
    private lateinit var accessAdapter: AccessAdapter
    private val accessList = mutableListOf<RoomAccessItem>()
    private lateinit var selectedRoom: SimpleRoom
    private var currentRoomData: Room? = null
    
    // NFC components
    private var nfcAdapter: NfcAdapter? = null
    private var pendingIntent: PendingIntent? = null
    private var intentFilters: Array<IntentFilter>? = null
    private var techLists: Array<Array<String>>? = null
    private var isWaitingForCard = false
    private var currentDialog: AlertDialog? = null
    private var cardIdInput: EditText? = null
    private var userNameInput: EditText? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_access_management)

        authManager = AuthManager(this)
        userManager = UserManager(this)
        
        // Get room data from intent
        val roomId = intent.getStringExtra("roomId") ?: ""
        val roomName = intent.getStringExtra("roomName") ?: ""
        val roomDesc = intent.getStringExtra("roomDesc") ?: ""
        val maxCapacity = intent.getIntExtra("roomMaxCapacity", 10)
        val occupancy = intent.getIntExtra("roomOccupancy", 0)
        selectedRoom = SimpleRoom(roomId, roomName, roomDesc, maxCapacity, occupancy)

        initializeViews()
        setupRecyclerView()
        setupNfc()
        loadSampleAccess()
    }

    private fun initializeViews() {
        recyclerView = findViewById(R.id.accessRecyclerView)
        emptyView = findViewById(R.id.emptyView)
        occupancyText = findViewById(R.id.occupancyText)

        findViewById<TextView>(R.id.roomTitleText).text = selectedRoom.roomName
        findViewById<TextView>(R.id.roomIdText).text = "Room ID: ${selectedRoom.roomId}"

        findViewById<ImageView>(R.id.backButton).setOnClickListener {
            finish()
        }

        findViewById<ImageView>(R.id.historyButton).setOnClickListener {
            val intent = Intent(this, RoomLogsActivity::class.java)
            intent.putExtra("roomId", selectedRoom.roomId)
            startActivity(intent)
        }

        findViewById<Button>(R.id.addAccessButton).setOnClickListener {
            showAddAccessDialog()
        }

        findViewById<Button>(R.id.terminalModeButton).setOnClickListener {
            val intent = Intent(this, RoomTerminalActivity::class.java)
            intent.putExtra("roomId", selectedRoom.roomId)
            intent.putExtra("roomName", selectedRoom.roomName)
            intent.putExtra("roomMaxCapacity", currentRoomData?.maxCapacity ?: 10)
            startActivity(intent)
        }
    }

    private fun setupRecyclerView() {
        accessAdapter = AccessAdapter(accessList) { access ->
            showAccessOptionsDialog(access)
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = accessAdapter
    }

    private fun loadSampleAccess() {
        CoroutineScope(Dispatchers.Main).launch {
            accessList.clear()
            
            // Fetch latest room data for occupancy
            val rooms = userManager.getAllRooms()
            currentRoomData = rooms.find { it.id == selectedRoom.roomId }
            updateOccupancyUI()

            val allUsers = userManager.getAllUsers()
            allUsers.forEach { user ->
                if (userManager.hasAccess(user.id, selectedRoom.roomId)) {
                    val accessLevel = "USER" 
                    val access = RoomAccessItem(user.primaryCardUID, user.name, accessLevel, "Active")
                    accessList.add(access)
                }
            }
            updateUI()
        }
    }

    private fun updateOccupancyUI() {
        currentRoomData?.let { room ->
            occupancyText.text = "Occupancy: ${room.currentOccupancy} / ${room.maxCapacity}"
            
            // Color coding based on occupancy
            when {
                room.currentOccupancy >= room.maxCapacity -> occupancyText.setTextColor(getColor(android.R.color.holo_red_dark))
                room.currentOccupancy >= room.maxCapacity * 0.8 -> occupancyText.setTextColor(getColor(android.R.color.holo_orange_dark))
                else -> occupancyText.setTextColor(getColor(android.R.color.holo_green_dark))
            }
        }
    }

    private fun updateUI() {
        if (accessList.isEmpty()) {
            recyclerView.visibility = View.GONE
            emptyView.visibility = View.VISIBLE
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyView.visibility = View.GONE
            accessAdapter.notifyDataSetChanged()
        }
    }

    private fun showAddAccessDialog() {
        CoroutineScope(Dispatchers.Main).launch {
            val allUsers = userManager.getAllUsers()
            val registeredUsers = allUsers.filter { it.isActive && (!it.isExpired() || it.role == UserRole.ADMIN) }
            
            if (registeredUsers.isEmpty()) {
                Toast.makeText(this@RoomAccessManagementActivity, "No active users found. Please register users first.", Toast.LENGTH_LONG).show()
                return@launch
            }
            
            val userNames = registeredUsers.map { "${it.name} (${it.primaryCardUID})" }.toTypedArray()
            
            AlertDialog.Builder(this@RoomAccessManagementActivity)
                .setTitle("Select User to Grant Access")
                .setItems(userNames) { _, which ->
                    val selectedUser = registeredUsers[which]
                    showAccessLevelDialog(selectedUser)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }
    
    private fun showAccessLevelDialog(user: RegisteredUser) {
        val accessLevels = arrayOf("GUEST", "EMPLOYEE", "MANAGER", "ADMIN")
        
        AlertDialog.Builder(this)
            .setTitle("Grant Access to ${user.name}")
            .setItems(accessLevels) { _, which ->
                val accessLevel = accessLevels[which]
                grantUserAccess(user, accessLevel)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun grantUserAccess(user: RegisteredUser, accessLevel: String) {
        if (!user.isActive) {
            Toast.makeText(this, "Cannot grant access - User is deactivated", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (user.isExpired() && user.role != UserRole.ADMIN) {
            Toast.makeText(this, "Cannot grant access - User is expired", Toast.LENGTH_SHORT).show()
            return
        }
        
        val currentAdmin = authManager.getCurrentUser()
        if (currentAdmin == null) {
            Toast.makeText(this, "Admin session expired", Toast.LENGTH_SHORT).show()
            return
        }
        
        CoroutineScope(Dispatchers.Main).launch {
            val success = userManager.grantAccess(
                userId = user.id,
                roomId = selectedRoom.roomId,
                grantedBy = currentAdmin.username
            )
            
            if (success) {
                val access = RoomAccessItem(user.primaryCardUID, user.name, accessLevel, "Active")
                accessList.add(access)
                updateUI()
                Toast.makeText(this@RoomAccessManagementActivity, "Access granted to ${user.name}", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@RoomAccessManagementActivity, "Failed to grant access", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun setupNfc() {
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if (nfcAdapter == null) return
        
        pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )
        
        val intentFilter = IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED)
        intentFilters = arrayOf(intentFilter)
        techLists = arrayOf(
            arrayOf(NfcA::class.java.name),
            arrayOf(MifareClassic::class.java.name),
            arrayOf(MifareUltralight::class.java.name)
        )
    }
    
    override fun onResume() {
        super.onResume()
        loadSampleAccess()
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (isWaitingForCard) {
            handleNfcIntent(intent)
        }
    }
    
    private fun handleNfcIntent(intent: Intent) {
        val tag = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        }
        if (tag != null) {
            processCardForAccess(tag)
        }
    }
    
    private fun processCardForAccess(tag: Tag) {
        val uidBytes = tag.id
        val cardId = uidBytes.joinToString("") { "%02X".format(it) }
        
        CoroutineScope(Dispatchers.Main).launch {
            val registeredUser = userManager.getUserByCardUID(cardId)
            val userName = registeredUser?.name ?: "Unknown_$cardId"
            
            cardIdInput?.setText(cardId)
            userNameInput?.setText(userName)
            
            currentDialog?.findViewById<Button>(R.id.scanCardButton)?.apply {
                text = "Scan NFC Card"
                isEnabled = true
            }
            
            isWaitingForCard = false
            Toast.makeText(this@RoomAccessManagementActivity, "Card scanned successfully!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showAccessOptionsDialog(access: RoomAccessItem) {
        val options = arrayOf("Edit Access", "Revoke Access")
        AlertDialog.Builder(this)
            .setTitle("${access.userName} (${access.cardId})")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> editAccess(access)
                    1 -> revokeAccess(access)
                }
            }
            .show()
    }

    private fun editAccess(access: RoomAccessItem) {
        val accessLevels = arrayOf("GUEST", "EMPLOYEE", "MANAGER", "ADMIN")
        val currentIndex = accessLevels.indexOf(access.accessLevel)

        AlertDialog.Builder(this)
            .setTitle("Edit Access Level for ${access.userName}")
            .setSingleChoiceItems(accessLevels, currentIndex) { dialog, which ->
                val newAccessLevel = accessLevels[which]
                access.accessLevel = newAccessLevel
                accessAdapter.notifyDataSetChanged()
                Toast.makeText(this, "Access level updated to $newAccessLevel", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun revokeAccess(access: RoomAccessItem) {
        AlertDialog.Builder(this)
            .setTitle("Revoke Access")
            .setMessage("Are you sure you want to revoke access for ${access.userName}?")
            .setPositiveButton("Revoke") { _, _ ->
                CoroutineScope(Dispatchers.Main).launch {
                    val user = userManager.getUserByCardUID(access.cardId)
                    if (user != null) {
                        val success = userManager.revokeAccess(user.id, selectedRoom.roomId)
                        if (success) {
                            accessList.remove(access)
                            updateUI()
                            Toast.makeText(this@RoomAccessManagementActivity, "Access revoked", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@RoomAccessManagementActivity, "Failed to revoke access", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        accessList.remove(access)
                        updateUI()
                        Toast.makeText(this@RoomAccessManagementActivity, "Access revoked", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}

data class RoomAccessItem(
    val cardId: String,
    val userName: String,
    var accessLevel: String,
    val status: String
)

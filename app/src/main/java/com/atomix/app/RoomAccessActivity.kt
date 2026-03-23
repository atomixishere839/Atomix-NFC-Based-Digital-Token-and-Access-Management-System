package com.atomix.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RoomAccessActivity : AppCompatActivity() {

    private lateinit var authManager: AuthManager
    private lateinit var userManager: UserManager
    private lateinit var recyclerView: RecyclerView
    private lateinit var fabAddRoom: FloatingActionButton
    private lateinit var emptyView: LinearLayout
    private lateinit var roomAdapter: SimpleRoomAdapter
    private val rooms = mutableListOf<SimpleRoom>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_room_access)

        authManager = AuthManager(this)
        userManager = UserManager(this)
        initializeViews()
        setupRecyclerView()
        loadRooms()

        // Check if user is admin
        val currentUser = authManager.getCurrentUser()
        if (currentUser?.role != UserRole.ADMIN) {
            fabAddRoom.visibility = View.GONE
            Toast.makeText(this, "View-only mode for non-admin users", Toast.LENGTH_SHORT).show()
        }
    }

    private fun initializeViews() {
        recyclerView = findViewById(R.id.roomsRecyclerView)
        fabAddRoom = findViewById(R.id.fabAddRoom)
        emptyView = findViewById(R.id.emptyView)

        fabAddRoom.setOnClickListener {
            showAddRoomDialog()
        }

        findViewById<ImageView>(R.id.backButton).setOnClickListener {
            finish()
        }
    }

    private fun setupRecyclerView() {
        roomAdapter = SimpleRoomAdapter(rooms) { room ->
            showRoomOptionsDialog(room)
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = roomAdapter
    }

    private fun loadRooms() {
        CoroutineScope(Dispatchers.Main).launch {
            rooms.clear()
            val actualRooms = userManager.getAllRooms()
            actualRooms.forEach { room ->
                rooms.add(SimpleRoom(
                    roomId = room.id, 
                    roomName = room.name, 
                    description = room.description,
                    maxCapacity = room.maxCapacity,
                    currentOccupancy = room.currentOccupancy
                ))
            }
            updateUI()
        }
    }

    private fun updateUI() {
        if (rooms.isEmpty()) {
            recyclerView.visibility = View.GONE
            emptyView.visibility = View.VISIBLE
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyView.visibility = View.GONE
            roomAdapter.notifyDataSetChanged()
        }
    }

    private fun showAddRoomDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Add New Room")

        val view = layoutInflater.inflate(R.layout.dialog_add_room, null)
        val roomIdLayout = view.findViewById<View>(R.id.roomIdLayout)
        val roomNameInput = view.findViewById<EditText>(R.id.roomNameInput)
        val descriptionInput = view.findViewById<EditText>(R.id.descriptionInput)
        val capacityInput = view.findViewById<EditText>(R.id.capacityInput)

        // Room ID is now auto-generated in cloud-first manager
        roomIdLayout.visibility = View.GONE

        builder.setView(view)

        builder.setPositiveButton("Add") { _, _ ->
            val roomName = roomNameInput.text.toString().trim()
            val description = descriptionInput.text.toString().trim()
            val capacityStr = capacityInput.text.toString().trim()
            val capacity = if (capacityStr.isEmpty()) 10 else capacityStr.toIntOrNull() ?: 10

            if (roomName.isNotEmpty()) {
                addRoom(roomName, description, capacity)
            } else {
                Toast.makeText(this, "Room Name is required", Toast.LENGTH_SHORT).show()
            }
        }

        builder.setNegativeButton("Cancel", null)
        builder.show()
    }

    private fun addRoom(roomName: String, description: String, capacity: Int) {
        CoroutineScope(Dispatchers.Main).launch {
            val savedRoomId = userManager.addRoom(roomName, description, capacity)
            loadRooms() // Re-fetch from cloud
            Toast.makeText(this@RoomAccessActivity, "Room added successfully", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showRoomOptionsDialog(room: SimpleRoom) {
        val currentUser = authManager.getCurrentUser()
        if (currentUser?.role != UserRole.ADMIN) {
            Toast.makeText(this, "Admin access required", Toast.LENGTH_SHORT).show()
            return
        }

        val options = arrayOf("Edit Room", "Manage Access", "Delete Room")
        AlertDialog.Builder(this)
            .setTitle(room.roomName)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> editRoom(room)
                    1 -> manageRoomAccess(room)
                    2 -> deleteRoom(room)
                }
            }
            .show()
    }

    private fun manageRoomAccess(room: SimpleRoom) {
        val intent = Intent(this, RoomAccessManagementActivity::class.java)
        intent.putExtra("roomId", room.roomId)
        intent.putExtra("roomName", room.roomName)
        intent.putExtra("roomDesc", room.description)
        intent.putExtra("roomMaxCapacity", room.maxCapacity)
        intent.putExtra("roomOccupancy", room.currentOccupancy)
        startActivity(intent)
    }

    private fun editRoom(room: SimpleRoom) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Edit Room")

        val view = layoutInflater.inflate(R.layout.dialog_add_room, null)
        val roomIdLayout = view.findViewById<View>(R.id.roomIdLayout)
        val roomNameInput = view.findViewById<EditText>(R.id.roomNameInput)
        val descriptionInput = view.findViewById<EditText>(R.id.descriptionInput)
        val capacityInput = view.findViewById<EditText>(R.id.capacityInput)

        roomIdLayout.visibility = View.GONE
        roomNameInput.setText(room.roomName)
        descriptionInput.setText(room.description)
        capacityInput.setText(room.maxCapacity.toString())

        builder.setView(view)

        builder.setPositiveButton("Update") { _, _ ->
            val newRoomName = roomNameInput.text.toString().trim()
            val newDescription = descriptionInput.text.toString().trim()
            val capacityStr = capacityInput.text.toString().trim()
            val capacity = if (capacityStr.isEmpty()) room.maxCapacity else capacityStr.toIntOrNull() ?: room.maxCapacity

            if (newRoomName.isNotEmpty()) {
                updateRoom(room, newRoomName, newDescription, capacity)
            } else {
                Toast.makeText(this, "Room name cannot be empty", Toast.LENGTH_SHORT).show()
            }
        }

        builder.setNegativeButton("Cancel", null)
        builder.show()
    }
    
    private fun updateRoom(room: SimpleRoom, newName: String, newDescription: String, capacity: Int) {
        CoroutineScope(Dispatchers.Main).launch {
            val success = userManager.updateRoom(room.roomId, newName, newDescription, capacity)
            if (success) {
                loadRooms()
                Toast.makeText(this@RoomAccessActivity, "Room updated successfully", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@RoomAccessActivity, "Failed to update room", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun deleteRoom(room: SimpleRoom) {
        AlertDialog.Builder(this)
            .setTitle("Delete Room")
            .setMessage("Are you sure you want to delete ${room.roomName}?")
            .setPositiveButton("Delete") { _, _ ->
                CoroutineScope(Dispatchers.Main).launch {
                    val success = userManager.deleteRoom(room.roomId)
                    if (success) {
                        loadRooms()
                        Toast.makeText(this@RoomAccessActivity, "Room deleted", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@RoomAccessActivity, "Failed to delete room", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}

data class SimpleRoom(
    val roomId: String,
    val roomName: String,
    val description: String,
    val maxCapacity: Int = 10,
    val currentOccupancy: Int = 0
)

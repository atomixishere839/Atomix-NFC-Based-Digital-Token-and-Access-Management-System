package com.atomix.app

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RoomManagementActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var addRoomButton: Button
    private lateinit var userManager: UserManager
    private lateinit var roomAdapter: RoomAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_room_management)
        
        userManager = UserManager(this)
        
        initializeViews()
        setupRecyclerView()
        loadRooms()
    }

    private fun initializeViews() {
        recyclerView = findViewById(R.id.roomsRecyclerView)
        addRoomButton = findViewById(R.id.addRoomButton)
        
        addRoomButton.setOnClickListener { showAddRoomDialog() }
    }

    private fun setupRecyclerView() {
        roomAdapter = RoomAdapter { room ->
            showEditRoomDialog(room)
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = roomAdapter
    }

    private fun showEditRoomDialog(room: Room) {
        val options = arrayOf("Manage Access", "Edit Room Details", "Delete Room")
        AlertDialog.Builder(this)
            .setTitle(room.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        val intent = android.content.Intent(this, RoomAccessManagementActivity::class.java)
                        intent.putExtra("roomId", room.id)
                        intent.putExtra("roomName", room.name)
                        intent.putExtra("roomDesc", room.description)
                        startActivity(intent)
                    }
                    1 -> showRoomDetailsEditDialog(room)
                    2 -> showDeleteConfirmation(room)
                }
            }
            .show()
    }

    private fun showRoomDetailsEditDialog(room: Room) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Edit Room Details")

        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            val padding = (24 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding / 2, padding, padding / 2)
        }

        val lp = android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = (8 * resources.displayMetrics.density).toInt()
            bottomMargin = (8 * resources.displayMetrics.density).toInt()
        }

        val nameInput = TextInputEditText(this).apply {
            hint = "Room Name"
            setText(room.name)
            layoutParams = lp
        }
        container.addView(nameInput)

        val descInput = TextInputEditText(this).apply {
            hint = "Description"
            setText(room.description)
            layoutParams = lp
        }
        container.addView(descInput)

        val capacityInput = TextInputEditText(this).apply {
            hint = "Room Max Capacity (Occupancy)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(room.maxCapacity.toString())
            layoutParams = lp
        }
        container.addView(capacityInput)

        builder.setView(container)

        builder.setPositiveButton("Update") { _, _ ->
            val name = nameInput.text.toString().trim()
            val desc = descInput.text.toString().trim()
            val capacityStr = capacityInput.text.toString().trim()
            val capacity = if (capacityStr.isEmpty()) room.maxCapacity else capacityStr.toIntOrNull() ?: room.maxCapacity
            
            if (name.isNotEmpty()) {
                CoroutineScope(Dispatchers.Main).launch {
                    userManager.updateRoom(room.id, name, desc, capacity)
                    loadRooms()
                    Toast.makeText(this@RoomManagementActivity, "Room updated successfully", Toast.LENGTH_SHORT).show()
                }
            }
        }

        builder.setNegativeButton("Cancel", null)
        builder.show()
    }

    private fun showDeleteConfirmation(room: Room) {
        AlertDialog.Builder(this)
            .setTitle("Delete Room")
            .setMessage("Are you sure you want to delete ${room.name}?")
            .setPositiveButton("Delete") { _, _ ->
                CoroutineScope(Dispatchers.Main).launch {
                    userManager.deleteRoom(room.id)
                    loadRooms()
                    Toast.makeText(this@RoomManagementActivity, "Room deleted", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun loadRooms() {
        CoroutineScope(Dispatchers.Main).launch {
            val rooms = userManager.getAllRooms()
            roomAdapter.updateRooms(rooms)
        }
    }

    private fun showAddRoomDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Add New Room")

        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            val padding = (24 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding / 2, padding, padding / 2)
        }

        val lp = android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = (8 * resources.displayMetrics.density).toInt()
            bottomMargin = (8 * resources.displayMetrics.density).toInt()
        }

        val nameInput = TextInputEditText(this).apply {
            hint = "Room Name"
            layoutParams = lp
        }
        container.addView(nameInput)

        val descInput = TextInputEditText(this).apply {
            hint = "Description"
            layoutParams = lp
        }
        container.addView(descInput)

        val capacityInput = TextInputEditText(this).apply {
            hint = "Room Max Capacity (Occupancy)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText("10") // Default value shown
            layoutParams = lp
        }
        container.addView(capacityInput)

        builder.setView(container)

        builder.setPositiveButton("Add") { _, _ ->
            val name = nameInput.text.toString().trim()
            val desc = descInput.text.toString().trim()
            val capacityStr = capacityInput.text.toString().trim()
            val capacity = if (capacityStr.isEmpty()) 10 else capacityStr.toIntOrNull() ?: 10
            
            if (name.isNotEmpty()) {
                CoroutineScope(Dispatchers.Main).launch {
                    userManager.addRoom(name, desc, capacity)
                    loadRooms()
                    Toast.makeText(this@RoomManagementActivity, "Room added successfully", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this@RoomManagementActivity, "Room name is required", Toast.LENGTH_SHORT).show()
            }
        }

        builder.setNegativeButton("Cancel", null)
        builder.show()
    }
}

package com.atomix.app

import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class RoomLogsActivity : AppCompatActivity() {

    private lateinit var userManager: UserManager
    private lateinit var recyclerView: RecyclerView
    private lateinit var logAdapter: AccessLogAdapter
    private var roomId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_room_logs)

        userManager = UserManager(this)
        roomId = intent.getStringExtra("roomId")

        findViewById<ImageView>(R.id.backButton).setOnClickListener {
            finish()
        }

        setupRecyclerView()
        loadLogs()
    }

    private fun setupRecyclerView() {
        recyclerView = findViewById(R.id.logsRecyclerView)
        logAdapter = AccessLogAdapter(emptyList())
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = logAdapter
    }

    private fun loadLogs() {
        CoroutineScope(Dispatchers.Main).launch {
            val logs = userManager.getAccessLogs(roomId)
            logAdapter.updateLogs(logs)
        }
    }
}
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
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RoomTerminalActivity : AppCompatActivity() {

    private lateinit var userManager: UserManager
    private lateinit var roomId: String
    private lateinit var roomName: String
    private var roomMaxCapacity: Int = 10
    
    private lateinit var roomNameText: TextView
    private lateinit var occupancyText: TextView
    private lateinit var statusText: TextView
    private lateinit var userText: TextView
    private lateinit var nfcIcon: ImageView
    
    private var nfcAdapter: NfcAdapter? = null
    private var pendingIntent: PendingIntent? = null
    private var intentFilters: Array<IntentFilter>? = null
    private var techLists: Array<Array<String>>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_room_terminal)

        userManager = UserManager(this)
        
        roomId = intent.getStringExtra("roomId") ?: ""
        roomName = intent.getStringExtra("roomName") ?: "Unknown Room"
        roomMaxCapacity = intent.getIntExtra("roomMaxCapacity", 10)

        initializeViews()
        setupNfc()
        refreshOccupancy()
    }

    private fun initializeViews() {
        roomNameText = findViewById(R.id.terminalRoomName)
        occupancyText = findViewById(R.id.terminalOccupancy)
        statusText = findViewById(R.id.terminalStatusText)
        userText = findViewById(R.id.terminalUserText)
        nfcIcon = findViewById(R.id.nfcIcon)
        
        roomNameText.text = roomName
        
        findViewById<Button>(R.id.exitTerminalButton).setOnClickListener {
            finish()
        }
    }

    private fun refreshOccupancy() {
        CoroutineScope(Dispatchers.Main).launch {
            val occupancy = userManager.getRoomOccupancy(roomId)
            occupancyText.text = "Occupancy: $occupancy / $roomMaxCapacity"
            
            when {
                occupancy >= roomMaxCapacity -> occupancyText.setTextColor(getColor(android.R.color.holo_red_dark))
                occupancy >= roomMaxCapacity * 0.8 -> occupancyText.setTextColor(getColor(android.R.color.holo_orange_dark))
                else -> occupancyText.setTextColor(getColor(android.R.color.holo_green_dark))
            }
        }
    }

    private fun setupNfc() {
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if (nfcAdapter == null) {
            Toast.makeText(this, "NFC not available", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        
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
        nfcAdapter?.enableForegroundDispatch(this, pendingIntent, intentFilters, techLists)
        refreshOccupancy()
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableForegroundDispatch(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNfcIntent(intent)
    }

    private fun handleNfcIntent(intent: Intent) {
        val tag = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        }
        if (tag != null) {
            processCardTap(tag)
        }
    }

    private fun processCardTap(tag: Tag) {
        val uidBytes = tag.id
        val cardId = uidBytes.joinToString("") { "%02X".format(it) }
        
        CoroutineScope(Dispatchers.Main).launch {
            val user = userManager.getUserByCardUID(cardId)
            
            if (user == null) {
                showAccessDenied("UNREGISTERED CARD")
                return@launch
            }
            
            if (!user.isActive) {
                showAccessDenied("USER DEACTIVATED")
                return@launch
            }
            
            if (user.isExpired() && user.role != UserRole.ADMIN) {
                showAccessDenied("ACCESS EXPIRED")
                return@launch
            }
            
            // Check if user has permission for this room
            val hasPermission = userManager.hasAccess(user.id, roomId)
            if (!hasPermission) {
                showAccessDenied("NO PERMISSION FOR THIS ROOM")
                return@launch
            }
            
            // Toggle Access (ENTRY/EXIT)
            val newStatus = userManager.toggleRoomAccess(user.id, roomId)
            
            if (newStatus != null) {
                showAccessGranted(user.name, newStatus)
                refreshOccupancy()
            } else {
                showAccessDenied("ROOM FULL / ERROR")
            }
        }
    }

    private fun showAccessGranted(userName: String, type: AccessLogType) {
        statusText.text = if (type == AccessLogType.ENTRY) "✅ WELCOME!" else "✅ GOODBYE!"
        statusText.setTextColor(getColor(android.R.color.holo_green_light))
        userText.text = userName
        userText.visibility = View.VISIBLE
        nfcIcon.setColorFilter(getColor(android.R.color.holo_green_light))
        
        resetStatusDelayed()
    }

    private fun showAccessDenied(reason: String) {
        statusText.text = "❌ ACCESS DENIED"
        statusText.setTextColor(getColor(android.R.color.holo_red_light))
        userText.text = reason
        userText.visibility = View.VISIBLE
        nfcIcon.setColorFilter(getColor(android.R.color.holo_red_light))
        
        resetStatusDelayed()
    }

    private fun resetStatusDelayed() {
        Handler(Looper.getMainLooper()).postDelayed({
            statusText.text = "PLEASE SCAN YOUR CARD"
            statusText.setTextColor(getColor(R.color.dark_text_primary))
            userText.visibility = View.GONE
            nfcIcon.setColorFilter(getColor(R.color.dark_text_secondary))
        }, 3000)
    }
}
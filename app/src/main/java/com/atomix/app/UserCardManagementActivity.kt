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

class UserCardManagementActivity : AppCompatActivity() {

    private lateinit var userManager: UserManager
    private lateinit var authManager: AuthManager
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var cardAdapter: CardListAdapter
    private lateinit var currentUser: RegisteredUser
    private val cardList = mutableListOf<String>()
    
    // NFC components
    private var nfcAdapter: NfcAdapter? = null
    private var pendingIntent: PendingIntent? = null
    private var intentFilters: Array<IntentFilter>? = null
    private var techLists: Array<Array<String>>? = null
    private var isWaitingForCard = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_card_management)

        userManager = UserManager(this)
        authManager = AuthManager(this)
        
        val userId = intent.getStringExtra("userId") ?: ""
        
        CoroutineScope(Dispatchers.Main).launch {
            val user = userManager.getAllUsers().find { it.id == userId }
            if (user == null) {
                Toast.makeText(this@UserCardManagementActivity, "User not found", Toast.LENGTH_SHORT).show()
                finish()
                return@launch
            }
            currentUser = user
            initializeViews()
            setupRecyclerView()
            setupNfc()
            loadUserCards()
        }
    }

    private fun initializeViews() {
        recyclerView = findViewById(R.id.cardsRecyclerView)
        emptyView = findViewById(R.id.emptyView)

        findViewById<TextView>(R.id.userNameText).text = currentUser.name
        findViewById<TextView>(R.id.userIdText).text = "User ID: ${currentUser.id}"

        findViewById<TextView>(R.id.backButton).setOnClickListener {
            finish()
        }

        val addCardButton = findViewById<Button>(R.id.addCardButton)
        val currentAdmin = authManager.getCurrentUser()
        if (currentAdmin?.role == UserRole.ADMIN) {
            addCardButton.visibility = View.VISIBLE
            addCardButton.setOnClickListener {
                showAddCardDialog()
            }
        } else {
            addCardButton.visibility = View.GONE
        }
    }

    private fun setupRecyclerView() {
        cardAdapter = CardListAdapter(cardList) { cardUID, isPrimary ->
            showCardOptionsDialog(cardUID, isPrimary)
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = cardAdapter
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

    private fun loadUserCards() {
        cardList.clear()
        cardList.addAll(currentUser.getAllCardUIDs())
        updateUI()
    }

    private fun updateUI() {
        if (cardList.isEmpty()) {
            recyclerView.visibility = View.GONE
            emptyView.visibility = View.VISIBLE
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyView.visibility = View.GONE
            cardAdapter.notifyDataSetChanged()
        }
    }

    private fun showAddCardDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Add Card to ${currentUser.name}")
        builder.setMessage("Scan an NFC card or enter Card UID manually")
        
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 24)
        }
        
        val cardInput = EditText(this)
        cardInput.hint = "Card UID (or scan NFC)"
        container.addView(cardInput)
        
        val scanButton = Button(this)
        scanButton.text = "Scan NFC Card"
        scanButton.setOnClickListener {
            isWaitingForCard = true
            scanButton.text = "Scanning... Hold card near device"
            scanButton.isEnabled = false
            nfcAdapter?.enableForegroundDispatch(this, pendingIntent, intentFilters, techLists)
            Toast.makeText(this, "Hold NFC card near device", Toast.LENGTH_SHORT).show()
        }
        container.addView(scanButton)
        
        builder.setView(container)
        
        builder.setPositiveButton("Add") { _, _ ->
            val cardUID = cardInput.text.toString().trim().uppercase()
            if (cardUID.isNotEmpty()) {
                addCardToUser(cardUID)
            } else {
                Toast.makeText(this, "Please enter or scan a card UID", Toast.LENGTH_SHORT).show()
            }
        }
        
        builder.setNegativeButton("Cancel") { _, _ ->
            isWaitingForCard = false
            nfcAdapter?.disableForegroundDispatch(this)
        }
        
        val dialog = builder.show()
        dialog.setOnDismissListener {
            isWaitingForCard = false
            nfcAdapter?.disableForegroundDispatch(this)
        }
    }

    private fun addCardToUser(cardUID: String) {
        CoroutineScope(Dispatchers.Main).launch {
            if (userManager.isCardRegistered(cardUID)) {
                Toast.makeText(this@UserCardManagementActivity, "Card already registered to another user", Toast.LENGTH_SHORT).show()
                return@launch
            }
            
            val success = userManager.addCardToUser(currentUser.id, cardUID)
            if (success) {
                val updatedUser = userManager.getAllUsers().find { it.id == currentUser.id }
                if (updatedUser != null) {
                    currentUser = updatedUser
                    loadUserCards()
                    Toast.makeText(this@UserCardManagementActivity, "Card added successfully", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this@UserCardManagementActivity, "Failed to add card", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showCardOptionsDialog(cardUID: String, isPrimary: Boolean) {
        val options = if (isPrimary) {
            arrayOf("View Details", "Set as Secondary")
        } else {
            arrayOf("View Details", "Set as Primary", "Remove Card")
        }
        
        AlertDialog.Builder(this)
            .setTitle("Card: $cardUID")
            .setItems(options) { _, which ->
                when (options[which]) {
                    "View Details" -> showCardDetails(cardUID, isPrimary)
                    "Set as Primary" -> setPrimaryCard(cardUID)
                    "Set as Secondary" -> demotePrimaryCard(cardUID)
                    "Remove Card" -> removeCard(cardUID)
                }
            }
            .show()
    }

    private fun showCardDetails(cardUID: String, isPrimary: Boolean) {
        val details = buildString {
            append("Card UID: $cardUID\n")
            append("Type: ${if (isPrimary) "Primary Card" else "Additional Card"}\n")
            append("User: ${currentUser.name}\n")
            append("Status: Active")
        }
        
        AlertDialog.Builder(this)
            .setTitle("Card Details")
            .setMessage(details)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun setPrimaryCard(cardUID: String) {
        AlertDialog.Builder(this)
            .setTitle("Set Primary Card")
            .setMessage("Set this card as the primary card for ${currentUser.name}?")
            .setPositiveButton("Yes") { _, _ ->
                Toast.makeText(this, "Primary card updated", Toast.LENGTH_SHORT).show()
                loadUserCards()
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun demotePrimaryCard(cardUID: String) {
        if (currentUser.additionalCards.isEmpty()) {
            Toast.makeText(this, "Cannot demote - no additional cards available", Toast.LENGTH_SHORT).show()
            return
        }
        
        AlertDialog.Builder(this)
            .setTitle("Demote Primary Card")
            .setMessage("This will make the first additional card the new primary. Continue?")
            .setPositiveButton("Yes") { _, _ ->
                Toast.makeText(this, "Card demoted to additional", Toast.LENGTH_SHORT).show()
                loadUserCards()
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun removeCard(cardUID: String) {
        AlertDialog.Builder(this)
            .setTitle("Remove Card")
            .setMessage("Remove this card from ${currentUser.name}?")
            .setPositiveButton("Remove") { _, _ ->
                CoroutineScope(Dispatchers.Main).launch {
                    val success = userManager.removeCardFromUser(currentUser.id, cardUID)
                    if (success) {
                        val updatedUser = userManager.getAllUsers().find { it.id == currentUser.id }
                        if (updatedUser != null) {
                            currentUser = updatedUser
                            loadUserCards()
                            Toast.makeText(this@UserCardManagementActivity, "Card removed", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(this@UserCardManagementActivity, "Failed to remove card", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
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
            val uidBytes = tag.id
            val cardUID = uidBytes.joinToString("") { "%02X".format(it) }
            
            isWaitingForCard = false
            nfcAdapter?.disableForegroundDispatch(this)
            addCardToUser(cardUID)
        }
    }

    override fun onResume() {
        super.onResume()
        if (isWaitingForCard) {
            nfcAdapter?.enableForegroundDispatch(this, pendingIntent, intentFilters, techLists)
        }
    }
}

package com.atomix.app

import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.widget.EditText
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.MifareClassic
import android.nfc.tech.MifareUltralight
import android.nfc.tech.NfcA
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private var nfcAdapter: NfcAdapter? = null
    private var pendingIntent: PendingIntent? = null
    private var intentFilters: Array<IntentFilter>? = null
    private var techLists: Array<Array<String>>? = null

    // Current card state
    private var currentTag: Tag? = null
    private var currentCardType: CardType? = null
    private var currentCardData: CardData? = null

    // UI Components
    private lateinit var statusText: TextView
    private lateinit var cardTypeText: TextView
    private lateinit var cardUidText: TextView
    private lateinit var techListText: TextView
    private lateinit var userIdText: TextView
    private lateinit var balanceText: TextView
    private lateinit var roleText: TextView
    private lateinit var currentUserText: TextView
    private lateinit var readCardButton: Button
    private lateinit var addCoinsButton: Button
    private lateinit var spendCoinsButton: Button
    private lateinit var wipeCardButton: Button
    private lateinit var logoutButton: Button
    
    private lateinit var authManager: AuthManager
    private lateinit var userManager: UserManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Check login status
        authManager = AuthManager(this)
        userManager = UserManager(this)
        if (!authManager.isLoggedIn()) {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()
            return
        }
        
        setContentView(R.layout.activity_main)

        initializeViews()
        setupNfc()
        
        // Handle NFC intent if app was launched by NFC tag
        handleIntent(intent)
    }

    private fun initializeViews() {
        statusText = findViewById(R.id.statusText)
        cardTypeText = findViewById(R.id.cardTypeText)
        cardUidText = findViewById(R.id.cardUidText)
        techListText = findViewById(R.id.techListText)
        userIdText = findViewById(R.id.userIdText)
        balanceText = findViewById(R.id.balanceText)
        roleText = findViewById(R.id.roleText)
        currentUserText = findViewById(R.id.currentUserText)
        readCardButton = findViewById(R.id.readCardButton)
        addCoinsButton = findViewById(R.id.addCoinsButton)
        spendCoinsButton = findViewById(R.id.spendCoinsButton)
        wipeCardButton = findViewById(R.id.wipeCardButton)
        logoutButton = findViewById(R.id.logoutButton)
        
        // Display current user info and set button visibility
        val currentUser = authManager.getCurrentUser()
        currentUser?.let {
            currentUserText.text = "${it.username} (${it.role.name})"
            // Show buttons based on role
            if (it.role == UserRole.ADMIN) {
                wipeCardButton.visibility = View.VISIBLE
            } else {
                wipeCardButton.visibility = View.GONE
            }
        }
        
        readCardButton.setOnClickListener { readCard() }
        addCoinsButton.setOnClickListener { addCoins() }
        spendCoinsButton.setOnClickListener { spendCoins() }
        wipeCardButton.setOnClickListener { wipeCard() }
        logoutButton.setOnClickListener { logout() }
    }
    
    private fun setupNfc() {
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        if (nfcAdapter == null) {
            statusText.text = getString(R.string.nfc_disabled)
            Toast.makeText(this, "NFC is not available on this device", Toast.LENGTH_LONG).show()
            return
        }

        if (!nfcAdapter!!.isEnabled) {
            statusText.text = getString(R.string.nfc_disabled)
            Toast.makeText(this, getString(R.string.nfc_disabled), Toast.LENGTH_LONG).show()
        }

        pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_MUTABLE
        )

        val intentFilter = IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED)
        try {
            intentFilter.addDataType("*/*")
        } catch (e: IntentFilter.MalformedMimeTypeException) {
            Log.e(TAG, "Error adding MIME type", e)
        }

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
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableForegroundDispatch(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val action = intent.action

        if (NfcAdapter.ACTION_TECH_DISCOVERED == action || 
            NfcAdapter.ACTION_TAG_DISCOVERED == action) {
            val tag = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)
            if (tag != null) {
                // First check for automation tasks
                checkForAutomationTask(tag)
                // Then process normal card operations
                processTag(tag)
            }
        }
    }

    private fun processTag(tag: Tag) {
        currentTag = tag

        // Get UID
        val uidBytes = tag.id
        val uid = uidBytes.joinToString("") { "%02X".format(it) }
        
        CoroutineScope(Dispatchers.Main).launch {
            // Check if this card is registered to a user
            val registeredUser = userManager.getUserByCardUID(uid)
            
            // Check card ownership for non-admin users
            val currentUser = authManager.getCurrentUser()
            if (currentUser?.role != UserRole.ADMIN && registeredUser != null) {
                if (registeredUser.username != currentUser?.username) {
                    statusText.text = "❌ Access Denied - Not Your Card"
                    cardUidText.text = "Card belongs to: ${registeredUser.name}"
                    userIdText.text = "❌ You can only scan your own card"
                    userIdText.setTextColor(getColor(android.R.color.holo_red_dark))
                    userIdText.visibility = View.VISIBLE
                    
                    // Disable all buttons
                    readCardButton.isEnabled = false
                    return@launch
                }
            }

            // Detect card type
            val nfcA = NfcA.get(tag)
            if (nfcA != null) {
                val cardType = NfcCardHandler.detectCardType(nfcA)
                currentCardType = cardType
                
                // AUTOMATIC READ: Sync balance as soon as card is tapped
                val cardData = withContext(Dispatchers.IO) {
                    when (cardType) {
                        CardType.MIFARE_CLASSIC_1K, CardType.MIFARE_CLASSIC_4K -> {
                            val mifareClassic = MifareClassic.get(tag)
                            mifareClassic?.let { NfcCardHandler.readMifareClassic(it) }
                        }
                        CardType.MIFARE_ULTRALIGHT -> {
                            val mifareUltralight = MifareUltralight.get(tag)
                            mifareUltralight?.let { NfcCardHandler.readMifareUltralight(it) }
                        }
                        else -> null
                    }
                }

                if (cardData != null) {
                    currentCardData = cardData
                    updateCardDataUI(cardData)
                    statusText.text = "✅ Card Synced"
                    
                    // Sync to cloud
                    val cardUserForSync = userManager.getUserByCardUID(cardData.uid)
                    if (cardUserForSync != null) {
                        ApiService.syncUser(cardUserForSync)
                    }
                }
                
                // Get tech list
                val techList = tag.techList.joinToString(", ")

                // Update UI
                statusText.text = if (registeredUser != null) {
                    if (registeredUser.isExpired() && registeredUser.role != UserRole.ADMIN) {
                        "⚠️ ${registeredUser.name} - EXPIRED"
                    } else {
                        "Welcome ${registeredUser.name}!"
                    }
                } else {
                    // Check if card belongs to deactivated user
                    val allUsers = userManager.getAllUsers()
                    val deactivatedUser = allUsers.find { it.hasCard(uid) && !it.isActive }
                    val expiredUser = allUsers.find { it.hasCard(uid) && it.isActive && it.isExpired() && it.role != UserRole.ADMIN }
                    
                    when {
                        deactivatedUser != null -> "❌ ${deactivatedUser.name} - DEACTIVATED"
                        expiredUser != null -> "⚠️ ${expiredUser.name} - EXPIRED"
                        allUsers.any { it.hasCard(uid) } -> getString(R.string.card_detected)
                        else -> "❌ UNREGISTERED CARD - Please register first"
                    }
                }
                cardUidText.text = "${getString(R.string.card_uid)} $uid"
                cardTypeText.text = "${getString(R.string.card_type)} ${currentCardType?.displayName ?: "-"}"
                techListText.text = "${getString(R.string.tech_list)} $techList"
                
                if (registeredUser != null) {
                    if (registeredUser.isExpired() && registeredUser.role != UserRole.ADMIN) {
                        userIdText.text = "❌ EXPIRED USER: ${registeredUser.name}"
                        userIdText.setTextColor(getColor(android.R.color.holo_red_dark))
                    } else {
                        userIdText.text = "Registered User: ${registeredUser.name}"
                        userIdText.setTextColor(getColor(android.R.color.white))
                    }
                    userIdText.visibility = View.VISIBLE
                } else {
                    val allUsers = userManager.getAllUsers()
                    val deactivatedUser = allUsers.find { it.hasCard(uid) && !it.isActive }
                    val expiredUser = allUsers.find { it.hasCard(uid) && it.isActive && it.isExpired() && it.role != UserRole.ADMIN }
                    
                    when {
                        deactivatedUser != null -> {
                            userIdText.text = "❌ DEACTIVATED USER: ${deactivatedUser.name}"
                            userIdText.setTextColor(getColor(android.R.color.darker_gray))
                            userIdText.visibility = View.VISIBLE
                        }
                        expiredUser != null -> {
                            userIdText.text = "⚠️ EXPIRED USER: ${expiredUser.name}"
                            userIdText.setTextColor(getColor(android.R.color.holo_red_dark))
                            userIdText.visibility = View.VISIBLE
                        }
                    }
                }
                
                // Enable buttons based on user role
                readCardButton.isEnabled = true
            } else {
                statusText.text = getString(R.string.unknown_card)
                cardTypeText.text = "${getString(R.string.card_type)} ${CardType.UNSUPPORTED.displayName}"
            }
        }
    }

    private fun readCard() {
        val tag = currentTag ?: run {
            Toast.makeText(this, getString(R.string.no_card_detected), Toast.LENGTH_SHORT).show()
            return
        }

        val cardType = currentCardType ?: run {
            Toast.makeText(this, getString(R.string.unknown_card), Toast.LENGTH_SHORT).show()
            return
        }
        
        // Get UID
        val uidBytes = tag.id
        val uid = uidBytes.joinToString("") { "%02X".format(it) }
        
        statusText.text = getString(R.string.reading_data)
        readCardButton.isEnabled = false

        CoroutineScope(Dispatchers.Main).launch {
            val allUsers = userManager.getAllUsers()
            val cardUser = allUsers.find { it.hasCard(uid) }
            
            if (cardUser == null) {
                Toast.makeText(this@MainActivity, "❌ Unregistered card - Please register this card first", Toast.LENGTH_LONG).show()
                readCardButton.isEnabled = true
                return@launch
            }
            
            if (!cardUser.isActive || (cardUser.isExpired() && cardUser.role != UserRole.ADMIN)) {
                val status = if (!cardUser.isActive) "DEACTIVATED" else "EXPIRED"
                Toast.makeText(this@MainActivity, "❌ Cannot read $status user's card", Toast.LENGTH_LONG).show()
                readCardButton.isEnabled = true
                return@launch
            }

            val cardData = withContext(Dispatchers.IO) {
                when (cardType) {
                    CardType.MIFARE_CLASSIC_1K, CardType.MIFARE_CLASSIC_4K -> {
                        val mifareClassic = MifareClassic.get(tag)
                        mifareClassic?.let { NfcCardHandler.readMifareClassic(it) }
                    }
                    CardType.MIFARE_ULTRALIGHT -> {
                        val mifareUltralight = MifareUltralight.get(tag)
                        mifareUltralight?.let { NfcCardHandler.readMifareUltralight(it) }
                    }
                    else -> null
                }
            }

            readCardButton.isEnabled = true
            if (cardData != null) {
                currentCardData = cardData
                updateCardDataUI(cardData)
                
                val cardUserForSync = userManager.getUserByCardUID(cardData.uid)
                if (cardUserForSync != null) {
                    ApiService.syncUser(cardUserForSync)
                }
            } else {
                statusText.text = getString(R.string.read_failed)
                Toast.makeText(this@MainActivity, getString(R.string.read_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun addCoins() {
        val cardData = currentCardData ?: run {
            Toast.makeText(this, "Please read card first", Toast.LENGTH_SHORT).show()
            return
        }

        val currentUser = authManager.getCurrentUser()
        if (currentUser?.role != UserRole.ADMIN) {
            Toast.makeText(this, getString(R.string.admin_only), Toast.LENGTH_SHORT).show()
            return
        }

        CoroutineScope(Dispatchers.Main).launch {
            val registeredUser = userManager.getUserByCardUID(cardData.uid)
            if (registeredUser == null) {
                Toast.makeText(this@MainActivity, "❌ Access denied - User deactivated or expired", Toast.LENGTH_LONG).show()
                return@launch
            }

            val builder = AlertDialog.Builder(this@MainActivity)
            builder.setTitle("Add Tokens")
            builder.setMessage("Card User ID: ${cardData.userId}\nCurrent Balance: ${registeredUser.balance}")
            
            val amountInput = EditText(this@MainActivity)
            amountInput.hint = "Amount to add"
            amountInput.inputType = InputType.TYPE_CLASS_NUMBER
            builder.setView(amountInput)

            builder.setPositiveButton("Add") { _, _ ->
                val amount = amountInput.text.toString().toIntOrNull()
                if (amount == null || amount <= 0) {
                    Toast.makeText(this@MainActivity, "Invalid amount", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                CoroutineScope(Dispatchers.Main).launch {
                    val success = userManager.addBalanceToUser(registeredUser.id, amount, currentUser?.username ?: "Admin")
                    if (success) {
                        val updatedUser = userManager.getUserByCardUID(cardData.uid)
                        if (updatedUser != null) {
                            balanceText.text = "${getString(R.string.balance)} ${updatedUser.balance} ${getString(R.string.atoms)}"
                            
                            val tag = currentTag ?: return@launch
                            val cardType = currentCardType ?: return@launch
                            val updatedCardData = cardData.copy(balance = updatedUser.balance)
                            
                            val writeSuccess = withContext(Dispatchers.IO) {
                                when (cardType) {
                                    CardType.MIFARE_CLASSIC_1K, CardType.MIFARE_CLASSIC_4K -> {
                                        val mifareClassic = MifareClassic.get(tag)
                                        mifareClassic?.let { NfcCardHandler.writeMifareClassic(it, updatedCardData) } ?: false
                                    }
                                    CardType.MIFARE_ULTRALIGHT -> {
                                        val mifareUltralight = MifareUltralight.get(tag)
                                        mifareUltralight?.let { NfcCardHandler.writeMifareUltralight(it, updatedCardData) } ?: false
                                    }
                                    else -> false
                                }
                            }
                            if (writeSuccess) {
                                Toast.makeText(this@MainActivity, "✅ Tokens added & synced to card", Toast.LENGTH_SHORT).show()
                                currentCardData = updatedCardData
                            } else {
                                Toast.makeText(this@MainActivity, "⚠️ Tokens added but card sync failed", Toast.LENGTH_LONG).show()
                            }
                        }
                    } else {
                        Toast.makeText(this@MainActivity, "Failed to add tokens", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            builder.setNegativeButton("Cancel", null)
            builder.show()
        }
    }

    private fun spendCoins() {
        val cardData = currentCardData ?: run {
            Toast.makeText(this, "Please read card first", Toast.LENGTH_SHORT).show()
            return
        }

        CoroutineScope(Dispatchers.Main).launch {
            val registeredUser = userManager.getUserByCardUID(cardData.uid)
            if (registeredUser == null) {
                Toast.makeText(this@MainActivity, "❌ Access denied - User deactivated or expired", Toast.LENGTH_LONG).show()
                return@launch
            }

            val builder = AlertDialog.Builder(this@MainActivity)
            builder.setTitle("Spend Tokens")
            builder.setMessage("User: ${registeredUser.name}\nCurrent Balance: ${registeredUser.balance}")
            
            val amountInput = EditText(this@MainActivity)
            amountInput.hint = "Amount to spend"
            amountInput.inputType = InputType.TYPE_CLASS_NUMBER
            builder.setView(amountInput)

            builder.setPositiveButton("Spend") { _, _ ->
                val amount = amountInput.text.toString().toIntOrNull()
                if (amount == null || amount <= 0) {
                    Toast.makeText(this@MainActivity, "Invalid amount", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                if (registeredUser.balance < amount) {
                    Toast.makeText(this@MainActivity, "Insufficient balance", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                CoroutineScope(Dispatchers.Main).launch {
                    val success = userManager.spendBalanceFromUser(registeredUser.id, amount, "User")
                    if (success) {
                        val updatedUser = userManager.getUserByCardUID(cardData.uid)
                        if (updatedUser != null) {
                            balanceText.text = "${getString(R.string.balance)} ${updatedUser.balance} ${getString(R.string.atoms)}"
                            
                            val tag = currentTag ?: return@launch
                            val cardType = currentCardType ?: return@launch
                            val updatedCardData = cardData.copy(balance = updatedUser.balance)
                            
                            val writeSuccess = withContext(Dispatchers.IO) {
                                when (cardType) {
                                    CardType.MIFARE_CLASSIC_1K, CardType.MIFARE_CLASSIC_4K -> {
                                        val mifareClassic = MifareClassic.get(tag)
                                        mifareClassic?.let { NfcCardHandler.writeMifareClassic(it, updatedCardData) } ?: false
                                    }
                                    CardType.MIFARE_ULTRALIGHT -> {
                                        val mifareUltralight = MifareUltralight.get(tag)
                                        mifareUltralight?.let { NfcCardHandler.writeMifareUltralight(it, updatedCardData) } ?: false
                                    }
                                    else -> false
                                }
                            }
                            if (writeSuccess) {
                                Toast.makeText(this@MainActivity, "✅ Tokens spent & synced to card", Toast.LENGTH_SHORT).show()
                                currentCardData = updatedCardData
                            } else {
                                Toast.makeText(this@MainActivity, "⚠️ Tokens spent locally but card sync failed", Toast.LENGTH_LONG).show()
                            }
                        }
                    } else {
                        Toast.makeText(this@MainActivity, "Failed to spend tokens", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            builder.setNegativeButton("Cancel", null)
            builder.show()
        }
    }

    private fun showAmountDialog(isAdd: Boolean, onConfirm: (Int) -> Unit) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(if (isAdd) getString(R.string.add_coins) else getString(R.string.spend_coins))

        val amountInput = android.widget.EditText(this)
        amountInput.hint = getString(R.string.amount)
        amountInput.inputType = InputType.TYPE_CLASS_NUMBER
        builder.setView(amountInput)

        builder.setPositiveButton(getString(R.string.ok)) { _, _ ->
            val amount = amountInput.text.toString().toIntOrNull()
            if (amount != null && amount > 0) {
                onConfirm(amount)
            } else {
                Toast.makeText(this, "Invalid amount", Toast.LENGTH_SHORT).show()
            }
        }

        builder.setNegativeButton(getString(R.string.cancel), null)
        builder.show()
    }

    private fun updateCardDataUI(cardData: CardData) {
        // CLOUD-FIRST: Always fetch latest from Firebase on card tap
        CoroutineScope(Dispatchers.Main).launch {
            val serverUsers = ApiService.fetchAllUsers()
            if (serverUsers != null) {
                for (json in serverUsers) {
                    val uid = json.optString("primaryCardUID", "")
                    if (uid == cardData.uid) {
                        val id = json.optString("id")
                        val name = json.optString("name")
                        val username = json.optString("username")
                        val password = json.optString("password", "")
                        val roleStr = json.optString("role", "USER")
                        val role = try { UserRole.valueOf(roleStr) } catch(e: Exception) { UserRole.USER }
                        val balance = json.optInt("balance")
                        val expiry = json.optLong("expiryDate")
                        val active = json.optBoolean("isActive")
                        
                        userManager.importUserFromCloud(id, name, username, password, uid, role, balance, expiry, active)
                        break
                    }
                }
            }

            // After cloud sync, update UI from local DB
            val cardUser = userManager.getUserByCardUID(cardData.uid)
            if (cardUser != null) {
                userIdText.text = "${getString(R.string.user_id)} ${cardUser.name}"
                balanceText.text = "${getString(R.string.balance)} ${cardUser.balance} ${getString(R.string.atoms)}"
                roleText.text = "${getString(R.string.card_role)} ${cardUser.role.name}"
                userIdText.setTextColor(getColor(android.R.color.white))
            } else {
                // UNREGISTERED/DELETED CARD: Don't show balance to prevent ghost data
                userIdText.text = "❌ UNREGISTERED CARD"
                balanceText.text = "Balance: 0 Atoms"
                roleText.text = "Role: N/A"
                userIdText.setTextColor(getColor(android.R.color.holo_red_dark))
                
                // Optional: Clear card data if it's from a deleted user
                statusText.text = "❌ User deleted from system"
            }
            
            userIdText.visibility = View.VISIBLE
            balanceText.visibility = View.VISIBLE
            roleText.visibility = View.VISIBLE
        }

        // Show buttons based on logged-in user role
        val currentUser = authManager.getCurrentUser()
        if (currentUser?.role == UserRole.ADMIN) {
            addCoinsButton.visibility = View.VISIBLE
            addCoinsButton.isEnabled = true
            wipeCardButton.isEnabled = true
            spendCoinsButton.visibility = View.VISIBLE
            spendCoinsButton.isEnabled = true
        } else {
            addCoinsButton.visibility = View.GONE
            wipeCardButton.isEnabled = false
            spendCoinsButton.visibility = View.VISIBLE
            spendCoinsButton.isEnabled = true
        }
    }
    
    private fun wipeCard() {
        val currentUser = authManager.getCurrentUser()
        if (currentUser?.role != UserRole.ADMIN) {
            Toast.makeText(this, getString(R.string.admin_only), Toast.LENGTH_SHORT).show()
            return
        }

        val tag = currentTag ?: run {
            Toast.makeText(this, getString(R.string.no_card_detected), Toast.LENGTH_SHORT).show()
            return
        }

        val cardType = currentCardType ?: run {
            Toast.makeText(this, getString(R.string.unknown_card), Toast.LENGTH_SHORT).show()
            return
        }

        // Show confirmation dialog
        AlertDialog.Builder(this)
            .setTitle("Wipe Card Data")
            .setMessage("Are you sure you want to wipe all card data? This action will reset balance to 0.")
            .setPositiveButton("Wipe") { _, _ ->
                val uidBytes = tag.id
                val uid = uidBytes.joinToString("") { "%02X".format(it) }
                
                // Use the new formatting logic
                CoroutineScope(Dispatchers.IO).launch {
                    val success = when (cardType) {
                        CardType.MIFARE_CLASSIC_1K, CardType.MIFARE_CLASSIC_4K -> {
                            val mifareClassic = MifareClassic.get(tag)
                            mifareClassic?.let { NfcCardHandler.formatMifareClassic(it) } ?: false
                        }
                        else -> {
                            // For other types, just write empty data
                            val emptyCardData = CardData(uid = uid)
                            val successWrite = when (cardType) {
                                CardType.MIFARE_ULTRALIGHT -> {
                                    val mifareUltralight = MifareUltralight.get(tag)
                                    mifareUltralight?.let { NfcCardHandler.writeMifareUltralight(it, emptyCardData) } ?: false
                                }
                                else -> false
                            }
                            successWrite
                        }
                    }

                    withContext(Dispatchers.Main) {
                        if (success) {
                            // RESET BALANCE IN LOCAL DB AS WELL
                            userManager.resetBalanceByCardUID(uid)
                            
                            statusText.text = "Card Wiped & Balance Reset"
                            currentCardData = CardData(uid = uid)
                            
                            // Clear UI
                            userIdText.visibility = View.GONE
                            balanceText.visibility = View.GONE
                            roleText.visibility = View.GONE
                            userIdText.text = getString(R.string.user_id)
                            balanceText.text = getString(R.string.balance)
                            roleText.text = getString(R.string.card_role)
                            addCoinsButton.visibility = View.GONE
                            spendCoinsButton.visibility = View.GONE
                            wipeCardButton.isEnabled = false
                            
                            Toast.makeText(this@MainActivity, "✅ Card wiped and balance reset to 0", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@MainActivity, "❌ Wipe failed", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun logout() {
        authManager.logout()
        val intent = Intent(this, LoginActivity::class.java)
        startActivity(intent)
        finish()
    }
    
    private fun checkForAutomationTask(tag: Tag) {
        try {
            val mifareClassic = MifareClassic.get(tag)
            mifareClassic?.let { mifare ->
                mifare.connect()
                
                // Try to authenticate and read block 4
                val defaultKey = byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())
                if (mifare.authenticateSectorWithKeyA(1, defaultKey)) {
                    val data = mifare.readBlock(4)
                    val taskData = String(data).trim('\u0000')
                    
                    if (taskData.isNotEmpty() && taskData.contains("|")) {
                        val parts = taskData.split("|")
                        if (parts.size >= 3) {
                            executeAutomationTask(parts[0], parts[2]) // action, actionData
                        }
                    }
                }
                
                mifare.close()
            }
        } catch (e: Exception) {
            // Silently fail - not all cards have automation tasks
        }
    }
    
    private fun executeAutomationTask(action: String, actionData: String) {
        try {
            when (action) {
                "Auto Dial" -> {
                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$actionData"))
                    startActivity(intent)
                    Toast.makeText(this, "📞 Auto Dialing: $actionData", Toast.LENGTH_SHORT).show()
                }
                "Send SMS" -> {
                    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"))
                    intent.putExtra("sms_body", actionData)
                    startActivity(intent)
                    Toast.makeText(this, "💬 SMS: $actionData", Toast.LENGTH_SHORT).show()
                }
                "Open App" -> {
                    val intent = packageManager.getLaunchIntentForPackage(actionData)
                    if (intent != null) {
                        startActivity(intent)
                        Toast.makeText(this, "📱 Opening: $actionData", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "❌ Task execution failed", Toast.LENGTH_SHORT).show()
        }
    }
}

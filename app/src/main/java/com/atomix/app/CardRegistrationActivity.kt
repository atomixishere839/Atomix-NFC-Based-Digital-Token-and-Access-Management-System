package com.atomix.app

import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CardRegistrationActivity : AppCompatActivity() {

    private var nfcAdapter: NfcAdapter? = null
    private var pendingIntent: PendingIntent? = null
    private var intentFilters: Array<IntentFilter>? = null
    private var techLists: Array<Array<String>>? = null
    
    private lateinit var nameEditText: TextInputEditText
    private lateinit var usernameEditText: TextInputEditText
    private lateinit var passwordEditText: TextInputEditText
    private lateinit var scanStatusText: TextView
    private lateinit var cardUidText: TextView
    private lateinit var registerButton: Button
    
    private lateinit var userManager: UserManager
    private lateinit var authManager: AuthManager
    
    private var scannedCardUID: String? = null
    private var selectedRole: UserRole = UserRole.USER

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_card_registration)
        
        userManager = UserManager(this)
        authManager = AuthManager(this)
        
        initializeViews()
        setupNfc()
        
        selectedRole = intent.getSerializableExtra("role") as? UserRole ?: UserRole.USER
    }

    private fun initializeViews() {
        nameEditText = findViewById(R.id.nameEditText)
        usernameEditText = findViewById(R.id.usernameEditText)
        passwordEditText = findViewById(R.id.passwordEditText)
        scanStatusText = findViewById(R.id.scanStatusText)
        cardUidText = findViewById(R.id.cardUidText)
        registerButton = findViewById(R.id.registerButton)
        
        scanStatusText.text = "Please scan your NFC card"
        registerButton.isEnabled = false
        
        registerButton.setOnClickListener { performRegistration() }
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
            PendingIntent.FLAG_MUTABLE
        )
        
        val intentFilter = IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED)
        intentFilters = arrayOf(intentFilter)
        techLists = arrayOf(arrayOf("android.nfc.tech.NfcA"))
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
        handleNfcIntent(intent)
    }

    private fun handleNfcIntent(intent: Intent) {
        val tag = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)
        if (tag != null) {
            val uidBytes = tag.id
            val uid = uidBytes.joinToString("") { "%02X".format(it) }
            
            CoroutineScope(Dispatchers.Main).launch {
                // Check if card is already registered
                val existingUser = userManager.getUserByCardUID(uid)
                if (existingUser != null) {
                    Toast.makeText(this@CardRegistrationActivity, "Card already registered to: ${existingUser.name}", Toast.LENGTH_LONG).show()
                    return@launch
                }
                
                scannedCardUID = uid
                scanStatusText.text = "Card scanned successfully!"
                cardUidText.text = "Card UID: $uid"
                registerButton.isEnabled = true
            }
        }
    }

    private fun performRegistration() {
        val name = nameEditText.text.toString().trim()
        val username = usernameEditText.text.toString().trim()
        val password = passwordEditText.text.toString().trim()
        val cardUID = scannedCardUID
        
        Log.d("REGISTRATION_DEBUG", "Registering user: $username with password: $password")
        
        if (name.isEmpty() || username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (cardUID == null) {
            Toast.makeText(this, "Please scan your NFC card first", Toast.LENGTH_SHORT).show()
            return
        }
        
        registerButton.isEnabled = false
        CoroutineScope(Dispatchers.Main).launch {
            // Check if username exists
            if (userManager.isUsernameExists(username)) {
                Toast.makeText(this@CardRegistrationActivity, "Username already exists", Toast.LENGTH_SHORT).show()
                registerButton.isEnabled = true
                return@launch
            }
            
            // Register user with card binding and password
            val userSuccess = userManager.registerUser(name, username, password, cardUID, selectedRole)
            if (!userSuccess) {
                Toast.makeText(this@CardRegistrationActivity, "Registration failed", Toast.LENGTH_SHORT).show()
                registerButton.isEnabled = true
                return@launch
            }
            
            Toast.makeText(this@CardRegistrationActivity, "Registration successful! Card permanently linked to $name", Toast.LENGTH_LONG).show()
            
            // Navigate back to login
            val intent = Intent(this@CardRegistrationActivity, LoginActivity::class.java)
            startActivity(intent)
            finish()
        }
    }
}
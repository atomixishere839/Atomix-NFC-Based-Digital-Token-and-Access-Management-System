package com.atomix.app

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.MifareClassic
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*

class BackupSyncActivity : AppCompatActivity() {

    private lateinit var userManager: UserManager
    private lateinit var authManager: AuthManager
    private val EXPORT_REQUEST_CODE = 1001
    private val IMPORT_REQUEST_CODE = 1002
    private var currentPassword: String = ""
    private var exportPassword: String = ""
    private var isSharing: Boolean = false
    
    // NFC components
    private var nfcAdapter: NfcAdapter? = null
    private var pendingIntent: PendingIntent? = null
    private var isWaitingForMasterCardWrite = false
    private var isWaitingForMasterCardRead = false
    private var masterCardDataToWrite: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_backup_sync)

        userManager = UserManager(this)
        authManager = AuthManager(this)

        initializeViews()
        setupNfc()
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
    }

    override fun onResume() {
        super.onResume()
        nfcAdapter?.enableForegroundDispatch(this, pendingIntent, null, null)
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
        } ?: return
        
        val mifareClassic = MifareClassic.get(tag) ?: return

        if (isWaitingForMasterCardWrite) {
            performMasterCardWrite(mifareClassic)
        } else if (isWaitingForMasterCardRead) {
            performMasterCardRead(mifareClassic)
        }
    }

    private fun initializeViews() {
        findViewById<TextView>(R.id.backButton).setOnClickListener {
            finish()
        }

        findViewById<Button>(R.id.exportDataButton).setOnClickListener {
            isSharing = false
            showPasswordDialog(true)
        }

        findViewById<Button>(R.id.shareDataButton).setOnClickListener {
            isSharing = true
            showPasswordDialog(true)
        }

        findViewById<Button>(R.id.importDataButton).setOnClickListener {
            showPasswordDialog(false)
        }

        findViewById<Button>(R.id.viewStatsButton).setOnClickListener {
            showDataStats()
        }

        findViewById<Button>(R.id.createMasterCardButton).setOnClickListener {
            prepareMasterCardWrite()
        }

        findViewById<Button>(R.id.importMasterCardButton).setOnClickListener {
            prepareMasterCardRead()
        }
        
        // Add Force Cloud Sync button
        val cloudSyncBtn = Button(this).apply {
            text = "Force Cloud Sync"
            backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#FF5722"))
            setTextColor(android.graphics.Color.WHITE)
        }
        findViewById<LinearLayout>(R.id.mainLayout).addView(cloudSyncBtn)
        cloudSyncBtn.setOnClickListener {
            forceCloudSync()
        }
    }

    private fun forceCloudSync() {
        val progressDialog = AlertDialog.Builder(this)
            .setTitle("Cloud Sync")
            .setMessage("Syncing data with cloud...")
            .setCancelable(false)
            .create()
        progressDialog.show()

        CoroutineScope(Dispatchers.Main).launch {
            val serverUsers = ApiService.fetchAllUsers()
            if (serverUsers != null) {
                var syncCount = 0
                for (json in serverUsers) {
                    val uid = json.optString("primaryCardUID", "")
                    if (uid.isNotEmpty()) {
                        val id = json.optString("id", "")
                        val name = json.optString("name", "Unknown")
                        val username = json.optString("username", "user_${uid}")
                        val password = json.optString("password", "")
                        val roleStr = json.optString("role", "USER")
                        val balance = json.optInt("balance", 0)
                        val expiry = json.optLong("expiryDate", 0L)
                        val active = json.optBoolean("isActive", true)
                        
                        val role = try { UserRole.valueOf(roleStr) } catch(e: Exception) { UserRole.USER }
                        
                        // Use new import method to sync everything at once
                        if (id.isNotEmpty()) {
                            userManager.importUserFromCloud(id, name, username, password, uid, role, balance, expiry, active)
                            syncCount++
                        }
                    }
                }
                progressDialog.dismiss()
                if (syncCount > 0) {
                    Toast.makeText(this@BackupSyncActivity, "✅ Cloud Sync Complete: $syncCount users updated", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this@BackupSyncActivity, "ℹ️ Cloud is empty or already in sync", Toast.LENGTH_LONG).show()
                }
            } else {
                progressDialog.dismiss()
                Toast.makeText(this@BackupSyncActivity, "❌ Cloud Sync Failed: Check internet or API limit", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun prepareMasterCardWrite() {
        CoroutineScope(Dispatchers.Main).launch {
            val users = userManager.getAllUsers()
            if (users.isEmpty()) {
                Toast.makeText(this@BackupSyncActivity, "No users to export", Toast.LENGTH_SHORT).show()
                return@launch
            }

            // Create a compact string of users
            // Format: name|username|UID|role|balance|expiry|isActive
            val sb = StringBuilder()
            for (user in users) {
                val userStr = "${user.name}|${user.username}|${user.primaryCardUID}|${user.role.name}|${user.balance}|${user.expiryDate}|${if (user.isActive) 1 else 0}\n"
                if (sb.length + userStr.length > 700) break // MIFARE 1K limit approx 720 bytes
                sb.append(userStr)
            }

            masterCardDataToWrite = sb.toString()
            isWaitingForMasterCardWrite = true
            isWaitingForMasterCardRead = false
            
            AlertDialog.Builder(this@BackupSyncActivity)
                .setTitle("Create Master Card")
                .setMessage("Please tap a blank MIFARE Classic 1K card to write ${users.size} users.")
                .setNegativeButton("Cancel") { _, _ -> isWaitingForMasterCardWrite = false }
                .show()
        }
    }

    private fun performMasterCardWrite(mifareClassic: MifareClassic) {
        isWaitingForMasterCardWrite = false
        val success = NfcCardHandler.writeMasterSyncCard(mifareClassic, masterCardDataToWrite)
        
        runOnUiThread {
            if (success) {
                Toast.makeText(this, "✅ Master Card created successfully!", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "❌ Write failed. Try again.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun prepareMasterCardRead() {
        isWaitingForMasterCardRead = true
        isWaitingForMasterCardWrite = false
        
        AlertDialog.Builder(this)
            .setTitle("Import from Master Card")
            .setMessage("Please tap the Master Sync Card to import users.")
            .setNegativeButton("Cancel") { _, _ -> isWaitingForMasterCardRead = false }
            .show()
    }

    private fun performMasterCardRead(mifareClassic: MifareClassic) {
        isWaitingForMasterCardRead = false
        val data = NfcCardHandler.readMasterSyncCard(mifareClassic)
        
        runOnUiThread {
            if (data != null && data.isNotEmpty()) {
                processMasterCardData(data)
            } else {
                Toast.makeText(this, "❌ Read failed or card empty.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun processMasterCardData(data: String) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                // REMOVE ALL NULL CHARACTERS from the entire data string
                val cleanData = data.replace("\u0000", "").trim()
                val lines = cleanData.split("\n")
                var importedCount = 0
                var updatedCount = 0
                
                Log.d("BackupSync", "Read data from Master Card: $cleanData")
                
                for (line in lines) {
                    val trimmedLine = line.trim()
                    if (trimmedLine.isEmpty()) continue
                    
                    val parts = trimmedLine.split("|")
                    if (parts.size >= 7) {
                        val name = parts[0]
                        val username = parts[1]
                        val uid = parts[2]
                        val roleStr = parts[3]
                        val balance = parts[4].toIntOrNull() ?: 0
                        val expiry = parts[5].toLongOrNull() ?: 0L
                        val active = parts[6] == "1"
                        
                        try {
                            val role = UserRole.valueOf(roleStr)
                            
                            if (!userManager.isCardRegistered(uid) && !userManager.isUsernameExists(username)) {
                                userManager.registerUser(name, username, "", uid, role)
                                userManager.updateUserDetails(uid, balance, expiry, active)
                                importedCount++
                            } else if (userManager.isCardRegistered(uid)) {
                                // Update existing user balance/status from master card
                                userManager.updateUserDetails(uid, balance, expiry, active)
                                updatedCount++
                            }
                        } catch (e: Exception) {
                            Log.e("BackupSync", "Error parsing user role: $roleStr", e)
                        }
                    }
                }
                
                val message = StringBuilder("Sync Complete:\n")
                if (importedCount > 0) message.append("- $importedCount new users added\n")
                if (updatedCount > 0) message.append("- $updatedCount users updated\n")
                if (importedCount == 0 && updatedCount == 0) message.append("- No changes detected")
                
                AlertDialog.Builder(this@BackupSyncActivity)
                    .setTitle("Master Card Sync")
                    .setMessage(message.toString())
                    .setPositiveButton("OK", null)
                    .show()
                    
            } catch (e: Exception) {
                Log.e("BackupSync", "Import error", e)
                Toast.makeText(this@BackupSyncActivity, "❌ Import error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showPasswordDialog(isExport: Boolean) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(if (isExport) "Set Encryption Password" else "Enter Decryption Password")
        builder.setMessage(if (isExport) "This password will be required to import the data" else "Enter the password used during export")
        
        val passwordInput = android.widget.EditText(this)
        passwordInput.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        passwordInput.hint = "Password"
        builder.setView(passwordInput)
        
        builder.setPositiveButton(if (isExport) "Continue" else "Import") { _, _ ->
            val password = passwordInput.text.toString()
            if (password.length < 4) {
                Toast.makeText(this, "Password must be at least 4 characters", Toast.LENGTH_SHORT).show()
                return@setPositiveButton
            }
            
            if (isExport) {
                if (isSharing) {
                    shareData(password)
                } else {
                    exportData(password)
                }
            } else {
                importData(password)
            }
        }
        
        builder.setNegativeButton("Cancel", null)
        builder.show()
    }

    private fun shareData(password: String) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val encryptedData = userManager.exportEncryptedData(password)
                if (encryptedData.isEmpty()) {
                    Toast.makeText(this@BackupSyncActivity, "Sharing failed: Empty data", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                
                val fileName = "atomix_backup_${SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())}.aes"
                
                // Create temporary file in cache
                val cacheFile = File(cacheDir, fileName)
                FileOutputStream(cacheFile).use { it.write(encryptedData.toByteArray()) }
                
                // Get URI using FileProvider
                val contentUri = FileProvider.getUriForFile(
                    this@BackupSyncActivity,
                    "${packageName}.fileprovider",
                    cacheFile
                )
                
                // Create share intent
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/octet-stream"
                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                
                startActivity(Intent.createChooser(shareIntent, "Share Backup to Another Phone"))
                
            } catch (e: Exception) {
                Toast.makeText(this@BackupSyncActivity, "Sharing failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun exportData(password: String) {
        exportPassword = password
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_TITLE, "atomix_backup_${SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())}.aes")
        }
        startActivityForResult(intent, EXPORT_REQUEST_CODE)
    }

    private fun importData(password: String) {
        currentPassword = password
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/octet-stream"
        }
        startActivityForResult(intent, IMPORT_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK && data?.data != null) {
            when (requestCode) {
                EXPORT_REQUEST_CODE -> {
                    val uri = data.data!!
                    CoroutineScope(Dispatchers.Main).launch {
                        try {
                            val encryptedData = userManager.exportEncryptedData(exportPassword)
                            contentResolver.openOutputStream(uri)?.use { 
                                it.write(encryptedData.toByteArray())
                            }
                            Toast.makeText(this@BackupSyncActivity, "Data exported successfully", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(this@BackupSyncActivity, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                IMPORT_REQUEST_CODE -> {
                    val uri = data.data!!
                    CoroutineScope(Dispatchers.Main).launch {
                        try {
                            contentResolver.openInputStream(uri)?.use { inputStream ->
                                val reader = BufferedReader(InputStreamReader(inputStream))
                                val encryptedData = reader.readText()
                                
                                val plainData = EncryptionUtil.decrypt(encryptedData, currentPassword)
                                if (plainData != null) {
                                    if (userManager.importAllData(plainData)) {
                                        Toast.makeText(this@BackupSyncActivity, "Data imported successfully", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(this@BackupSyncActivity, "Import failed: Invalid data format", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    Toast.makeText(this@BackupSyncActivity, "Import failed: Invalid password", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } catch (e: Exception) {
                            Toast.makeText(this@BackupSyncActivity, "Import failed: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    private fun showDataStats() {
        CoroutineScope(Dispatchers.Main).launch {
            val usersCount = userManager.getAllUsers().size
            val roomsCount = userManager.getAllRooms().size
            val transactionsCount = userManager.getAllTransactions().size
            
            AlertDialog.Builder(this@BackupSyncActivity)
                .setTitle("System Statistics")
                .setMessage("Users: $usersCount\nRooms: $roomsCount\nTransactions: $transactionsCount")
                .setPositiveButton("OK", null)
                .show()
        }
    }
}

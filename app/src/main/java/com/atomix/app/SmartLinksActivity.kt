package com.atomix.app

import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.google.android.material.textfield.TextInputEditText

class SmartLinksActivity : AppCompatActivity() {

    private lateinit var urlInput: TextInputEditText
    private lateinit var statusText: TextView
    private lateinit var instructionText: TextView
    private lateinit var currentUrlText: TextView
    private lateinit var nfcIcon: ImageView
    private lateinit var startWriteButton: Button
    private lateinit var formatCardButton: Button
    private lateinit var writeStatusCard: CardView

    private var nfcAdapter: NfcAdapter? = null
    private var pendingIntent: PendingIntent? = null
    private var isWritingMode = false
    private var isFormattingMode = false
    private var targetUrl = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_smart_links)

        initializeViews()
        setupNfc()
        setupPresets()
    }

    private fun initializeViews() {
        urlInput = findViewById(R.id.urlInput)
        statusText = findViewById(R.id.statusText)
        instructionText = findViewById(R.id.instructionText)
        currentUrlText = findViewById(R.id.currentUrlText)
        nfcIcon = findViewById(R.id.nfcIcon)
        startWriteButton = findViewById(R.id.startWriteButton)
        formatCardButton = findViewById(R.id.formatCardButton)
        writeStatusCard = findViewById(R.id.writeStatusCard)

        findViewById<ImageView>(R.id.backButton).setOnClickListener {
            finish()
        }

        startWriteButton.setOnClickListener {
            val url = urlInput.text.toString().trim()
            if (url.isNotEmpty()) {
                val formattedUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    "https://$url"
                } else {
                    url
                }
                startWritingProcess(formattedUrl)
            } else {
                Toast.makeText(this, "URL cannot be empty", Toast.LENGTH_SHORT).show()
            }
        }

        formatCardButton.setOnClickListener {
            startFormattingProcess()
        }
    }

    private fun setupPresets() {
        findViewById<Button>(R.id.btnInstagram).setOnClickListener {
            urlInput.setText("https://instagram.com/")
            urlInput.setSelection(urlInput.text?.length ?: 0)
        }
        findViewById<Button>(R.id.btnLinkedin).setOnClickListener {
            urlInput.setText("https://linkedin.com/in/")
            urlInput.setSelection(urlInput.text?.length ?: 0)
        }
        findViewById<Button>(R.id.btnWebsite).setOnClickListener {
            urlInput.setText("https://")
            urlInput.setSelection(urlInput.text?.length ?: 0)
        }
    }

    private fun setupNfc() {
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if (nfcAdapter == null) {
            Toast.makeText(this, "NFC is not available on this device", Toast.LENGTH_LONG).show()
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
    }

    private fun startFormattingProcess() {
        isFormattingMode = true
        isWritingMode = false
        startWriteButton.isEnabled = false
        formatCardButton.isEnabled = false
        formatCardButton.text = "Waiting for card..."
        
        statusText.text = "READY TO FORMAT"
        statusText.setTextColor(getColor(R.color.dark_error))
        instructionText.text = "Tap card to erase all data"
        nfcIcon.setColorFilter(getColor(R.color.dark_error))
        
        Toast.makeText(this, "Tap card to clear data", Toast.LENGTH_SHORT).show()
    }

    private fun startWritingProcess(url: String) {
        targetUrl = url
        isWritingMode = true
        isFormattingMode = false
        startWriteButton.isEnabled = false
        formatCardButton.isEnabled = false
        startWriteButton.text = "Waiting for NFC Card..."
        
        statusText.text = "READY TO WRITE"
        statusText.setTextColor(getColor(android.R.color.holo_blue_dark))
        instructionText.text = "Place your card against the back of your phone"
        nfcIcon.setColorFilter(getColor(android.R.color.holo_blue_dark))
        
        Toast.makeText(this, "Please tap your NFC card now", Toast.LENGTH_SHORT).show()
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
        if (NfcAdapter.ACTION_TAG_DISCOVERED == intent.action || 
            NfcAdapter.ACTION_TECH_DISCOVERED == intent.action || 
            NfcAdapter.ACTION_NDEF_DISCOVERED == intent.action) {
            
            val tag = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)
            if (tag != null) {
                when {
                    isWritingMode -> writeUrlToTag(tag, targetUrl)
                    isFormattingMode -> formatTag(tag)
                    else -> readUrlFromTag(tag)
                }
            }
        }
    }

    private fun formatTag(tag: Tag) {
        try {
            val ndef = Ndef.get(tag)
            val formatable = NdefFormatable.get(tag)
            
            // 1. If it's already NDEF, we overwrite with an empty message to "clear" it
            if (ndef != null) {
                ndef.connect()
                // Empty message is one way to clear, or we can try formatting if it's NXP
                val emptyMessage = NdefMessage(arrayOf(NdefRecord(NdefRecord.TNF_EMPTY, null, null, null)))
                ndef.writeNdefMessage(emptyMessage)
                ndef.close()
                showFormatSuccess()
            } 
            // 2. If it's not NDEF, we try to format it
            else if (formatable != null) {
                formatable.connect()
                val emptyMessage = NdefMessage(arrayOf(NdefRecord(NdefRecord.TNF_EMPTY, null, null, null)))
                formatable.format(emptyMessage)
                formatable.close()
                showFormatSuccess()
            } else {
                Toast.makeText(this, "Tag cannot be formatted", Toast.LENGTH_LONG).show()
                resetWritingMode()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Format failed: ${e.message}", Toast.LENGTH_LONG).show()
            resetWritingMode()
        }
    }

    private fun showFormatSuccess() {
        statusText.text = getString(R.string.card_formatted)
        statusText.setTextColor(getColor(android.R.color.holo_green_dark))
        instructionText.text = getString(R.string.ndef_cleared)
        nfcIcon.setColorFilter(getColor(android.R.color.holo_green_dark))
        Toast.makeText(this, getString(R.string.format_success), Toast.LENGTH_SHORT).show()
        resetWritingMode()
    }

    private fun readUrlFromTag(tag: Tag) {
        try {
            val ndef = Ndef.get(tag)
            if (ndef != null) {
                ndef.connect()
                val ndefMessage = ndef.ndefMessage
                if (ndefMessage != null) {
                    val records = ndefMessage.records
                    for (record in records) {
                        if (record.tnf == NdefRecord.TNF_WELL_KNOWN && java.util.Arrays.equals(record.type, NdefRecord.RTD_URI)) {
                            val uri = record.toUri().toString()
                            currentUrlText.text = getString(R.string.current_url, uri)
                            currentUrlText.visibility = View.VISIBLE
                            
                            // Optional: Fill input with current URL if empty
                            if (urlInput.text.isNullOrEmpty()) {
                                urlInput.setText(uri)
                            }
                            break
                        }
                    }
                }
                ndef.close()
            }
        } catch (e: Exception) {
            // Silently fail for reading if it's not a URI tag
        }
    }

    private fun writeUrlToTag(tag: Tag, url: String) {
        val record = NdefRecord.createUri(url)
        val message = NdefMessage(arrayOf(record))

        try {
            val ndef = Ndef.get(tag)
            if (ndef != null) {
                ndef.connect()
                if (!ndef.isWritable) {
                    Toast.makeText(this, "This tag is read-only", Toast.LENGTH_LONG).show()
                    ndef.close()
                    resetWritingMode()
                    return
                }

                if (ndef.maxSize < message.toByteArray().size) {
                    Toast.makeText(this, "Tag capacity is too small", Toast.LENGTH_LONG).show()
                    ndef.close()
                    resetWritingMode()
                    return
                }

                ndef.writeNdefMessage(message)
                ndef.close()
                showSuccess(url)
            } else {
                // Try to format the tag as NDEF if it's not already
                val formatable = NdefFormatable.get(tag)
                if (formatable != null) {
                    try {
                        formatable.connect()
                        formatable.format(message)
                        formatable.close()
                        showSuccess(url)
                    } catch (e: java.io.IOException) {
                        e.printStackTrace()
                        Toast.makeText(this, "Connection lost! Keep the card steady against the phone.", Toast.LENGTH_LONG).show()
                        resetWritingMode()
                    } catch (e: Exception) {
                        e.printStackTrace()
                        val errorMsg = e.message ?: e.toString()
                        if (errorMsg.contains("IO", ignoreCase = true)) {
                            Toast.makeText(this, "Hardware error: This phone might not support formatting this specific card type (Mifare Classic).", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(this, "Formatting failed: $errorMsg", Toast.LENGTH_LONG).show()
                        }
                        resetWritingMode()
                    }
                } else {
                    // Specific check for Mifare Classic tags that might not be detected as NdefFormatable
                    Toast.makeText(this, "Tag not NDEF compatible. If it's a Mifare Classic, it might need factory formatting.", Toast.LENGTH_LONG).show()
                    resetWritingMode()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            val errorMsg = e.message ?: e.toString()
            Toast.makeText(this, "Error writing: $errorMsg", Toast.LENGTH_LONG).show()
            resetWritingMode()
        }
    }

    private fun showSuccess(url: String) {
        // Success UI
        statusText.text = "SUCCESSFULLY WRITTEN!"
        statusText.setTextColor(getColor(android.R.color.holo_green_dark))
        instructionText.text = "URL: $url is now on the card."
        nfcIcon.setColorFilter(getColor(android.R.color.holo_green_dark))
        
        Toast.makeText(this, "URL written successfully!", Toast.LENGTH_SHORT).show()
        resetWritingMode()
    }

    private fun resetWritingMode() {
        isWritingMode = false
        isFormattingMode = false
        startWriteButton.isEnabled = true
        formatCardButton.isEnabled = true
        startWriteButton.text = "Start Writing Process"
        formatCardButton.text = "Format / Clear Card"
        
        if (statusText.text != "SUCCESSFULLY WRITTEN!" && statusText.text != "CARD FORMATTED!") {
            statusText.text = "READY TO WRITE"
            statusText.setTextColor(getColor(R.color.dark_text_primary))
            instructionText.text = "Please scan your NFC card now"
            nfcIcon.setColorFilter(getColor(R.color.dark_text_secondary))
        }
    }
}
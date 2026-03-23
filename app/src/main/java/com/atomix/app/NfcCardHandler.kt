package com.atomix.app

import android.nfc.tech.MifareClassic
import android.nfc.tech.MifareUltralight
import android.nfc.tech.NfcA
import android.util.Log

class NfcCardHandler {

    companion object {
        private const val TAG = "NfcCardHandler"
        
        // Default MIFARE keys
        private val DEFAULT_KEY_A = byteArrayOf(
            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 
            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()
        )
        private val DEFAULT_KEY_B = byteArrayOf(
            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 
            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()
        )
        
        // Common MIFARE keys for formatting
        private val COMMON_KEYS = arrayOf(
            byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()),
            byteArrayOf(0xA0.toByte(), 0xA1.toByte(), 0xA2.toByte(), 0xA3.toByte(), 0xA4.toByte(), 0xA5.toByte()),
            byteArrayOf(0xD3.toByte(), 0xF7.toByte(), 0xD3.toByte(), 0xF7.toByte(), 0xD3.toByte(), 0xF7.toByte()),
            byteArrayOf(0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte()),
            byteArrayOf(0x4D.toByte(), 0x3A.toByte(), 0x99.toByte(), 0xC3.toByte(), 0x51.toByte(), 0xDD.toByte()),
            byteArrayOf(0x1A.toByte(), 0x98.toByte(), 0x2C.toByte(), 0x7E.toByte(), 0x45.toByte(), 0x9A.toByte()),
            byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte(), 0xDD.toByte(), 0xEE.toByte(), 0xFF.toByte()),
            byteArrayOf(0x01.toByte(), 0x02.toByte(), 0x03.toByte(), 0x04.toByte(), 0x05.toByte(), 0x06.toByte())
        )
        
        // Use Sector 1 for data storage
        private const val SECTOR = 1
        private const val DATA_BLOCK_1 = 4 // First block in Sector 1
        private const val DATA_BLOCK_2 = 5 // Second block in Sector 1
        
        fun detectCardType(nfcA: NfcA): CardType {
            return try {
                val atqa = nfcA.atqa
                val sak = nfcA.sak.toInt()
                
                // MIFARE Classic detection
                if (sak == 0x08 || sak == 0x88) {
                    // Check if it's 1K or 4K by attempting to connect
                    // For simplicity, we'll try to determine by size
                    // 1K = 16 sectors, 4K = 40 sectors
                    CardType.MIFARE_CLASSIC_1K // Default assumption, can be refined
                } else if (sak == 0x00 && (atqa[0].toInt() and 0x04) == 0) {
                    CardType.MIFARE_ULTRALIGHT
                } else {
                    CardType.UNSUPPORTED
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error detecting card type", e)
                CardType.UNSUPPORTED
            }
        }
        
        fun readMifareClassic(mifareClassic: MifareClassic): CardData? {
            return try {
                mifareClassic.connect()
                
                // Try to authenticate sector with key A
                var authenticated = mifareClassic.authenticateSectorWithKeyA(SECTOR, DEFAULT_KEY_A)
                
                // If failed, try other common keys
                if (!authenticated) {
                    for (key in COMMON_KEYS) {
                        if (mifareClassic.authenticateSectorWithKeyA(SECTOR, key)) {
                            authenticated = true
                            break
                        }
                    }
                }
                
                if (!authenticated) {
                    Log.e(TAG, "Authentication failed for all common keys")
                    return null
                }
                
                // Read two blocks for more storage (16 + 16 = 32 bytes)
                val block1Data = mifareClassic.readBlock(DATA_BLOCK_1)
                val block2Data = mifareClassic.readBlock(DATA_BLOCK_2)
                
                // Combine block data
                val combinedData = ByteArray(32)
                System.arraycopy(block1Data, 0, combinedData, 0, 16)
                System.arraycopy(block2Data, 0, combinedData, 16, 16)
                
                // Convert to string and trim null characters
                val dataString = String(combinedData, charset("UTF-8")).trim { it <= ' ' || it == '\u0000' }
                
                // Get UID
                val uidBytes = mifareClassic.tag.id
                val uid = uidBytes.joinToString("") { "%02X".format(it) }
                
                // Parse data
                if (dataString.isNotEmpty() && dataString.contains("|")) {
                    CardData.fromReadFormat(dataString, uid)
                } else {
                    CardData(uid = uid)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error reading MIFARE Classic", e)
                null
            } finally {
                try {
                    mifareClassic.close()
                } catch (e: Exception) {
                    Log.e(TAG, "Error closing MIFARE Classic", e)
                }
            }
        }
        
        fun writeMifareClassic(mifareClassic: MifareClassic, cardData: CardData): Boolean {
            return try {
                mifareClassic.connect()
                
                // Try to authenticate sector with key A
                var authenticated = mifareClassic.authenticateSectorWithKeyA(SECTOR, DEFAULT_KEY_A)
                
                // If failed, try other common keys
                if (!authenticated) {
                    for (key in COMMON_KEYS) {
                        if (mifareClassic.authenticateSectorWithKeyA(SECTOR, key)) {
                            authenticated = true
                            break
                        }
                    }
                }
                
                if (!authenticated) {
                    Log.e(TAG, "Authentication failed for write")
                    return false
                }
                
                // Prepare data string
                val dataString = cardData.toWriteFormat()
                val dataBytes = dataString.toByteArray(charset("UTF-8"))
                
                // Ensure we don't exceed 32 bytes (2 blocks)
                val totalBytes = ByteArray(32)
                System.arraycopy(dataBytes, 0, totalBytes, 0, minOf(dataBytes.size, 32))
                
                // Split into two blocks
                val block1 = ByteArray(16)
                val block2 = ByteArray(16)
                System.arraycopy(totalBytes, 0, block1, 0, 16)
                System.arraycopy(totalBytes, 16, block2, 0, 16)
                
                // Write data blocks
                mifareClassic.writeBlock(DATA_BLOCK_1, block1)
                mifareClassic.writeBlock(DATA_BLOCK_2, block2)
                
                Log.d(TAG, "Write successful to 2 blocks")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error writing MIFARE Classic", e)
                false
            } finally {
                try {
                    mifareClassic.close()
                } catch (e: Exception) {
                    Log.e(TAG, "Error closing MIFARE Classic", e)
                }
            }
        }
        
        fun formatMifareClassic(mifareClassic: MifareClassic): Boolean {
            return try {
                mifareClassic.connect()
                
                // Try to find a working key for the sector
                var workingKey: ByteArray? = null
                for (key in COMMON_KEYS) {
                    if (mifareClassic.authenticateSectorWithKeyA(SECTOR, key)) {
                        workingKey = key
                        break
                    }
                }
                
                if (workingKey == null) {
                    Log.e(TAG, "Format failed: No working key found")
                    return false
                }
                
                // Clear data blocks in the sector
                val emptyBlock = ByteArray(16)
                mifareClassic.writeBlock(DATA_BLOCK_1, emptyBlock)
                mifareClassic.writeBlock(DATA_BLOCK_2, emptyBlock)
                
                // Reset keys to default if they were different (Optional, but good for standardization)
                // This requires writing to the sector trailer (block 7)
                // Sector 1 trailer is block 7. 
                // Trailer format: Key A (6 bytes) | Access Bits (4 bytes) | Key B (6 bytes)
                // Default: FFFFFFFFFFFF | FF078069 | FFFFFFFFFFFF
                
                if (!workingKey.contentEquals(DEFAULT_KEY_A)) {
                    val trailerBlock = mifareClassic.sectorToBlock(SECTOR) + 3
                    val trailerData = ByteArray(16)
                    System.arraycopy(DEFAULT_KEY_A, 0, trailerData, 0, 6)
                    // Default access bits (Transport configuration)
                    trailerData[6] = 0xFF.toByte()
                    trailerData[7] = 0x07.toByte()
                    trailerData[8] = 0x80.toByte()
                    trailerData[9] = 0x69.toByte()
                    System.arraycopy(DEFAULT_KEY_B, 0, trailerData, 10, 6)
                    
                    mifareClassic.writeBlock(trailerBlock, trailerData)
                }
                
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error formatting MIFARE Classic", e)
                false
            } finally {
                try {
                    mifareClassic.close()
                } catch (e: Exception) {
                    Log.e(TAG, "Error closing MIFARE Classic", e)
                }
             }
         }
         
         fun readMasterSyncCard(mifareClassic: MifareClassic): String? {
            return try {
                mifareClassic.connect()
                val totalBytes = ByteArray(15 * 3 * 16) // 15 sectors, 3 data blocks each
                var byteOffset = 0
                
                for (sector in 1..15) {
                    var authenticated = mifareClassic.authenticateSectorWithKeyA(sector, DEFAULT_KEY_A)
                    if (!authenticated) {
                        for (key in COMMON_KEYS) {
                            if (mifareClassic.authenticateSectorWithKeyA(sector, key)) {
                                authenticated = true
                                break
                            }
                        }
                    }
                    
                    if (authenticated) {
                        val firstBlock = mifareClassic.sectorToBlock(sector)
                        for (blockOffset in 0..2) {
                            val blockData = mifareClassic.readBlock(firstBlock + blockOffset)
                            System.arraycopy(blockData, 0, totalBytes, byteOffset, 16)
                            byteOffset += 16
                        }
                    } else {
                        byteOffset += 16 * 3
                    }
                }
                
                String(totalBytes, charset("UTF-8")).trim { it <= ' ' || it == '\u0000' }
            } catch (e: Exception) {
                Log.e(TAG, "Error reading Master Card", e)
                null
            } finally {
                try { mifareClassic.close() } catch (e: Exception) {}
            }
        }
        
        fun writeMasterSyncCard(mifareClassic: MifareClassic, data: String): Boolean {
            return try {
                mifareClassic.connect()
                val dataBytes = data.toByteArray(charset("UTF-8"))
                val totalStorageSize = 15 * 3 * 16
                
                // Prepare a fixed-size buffer filled with zeros
                val buffer = ByteArray(totalStorageSize)
                System.arraycopy(dataBytes, 0, buffer, 0, minOf(dataBytes.size, totalStorageSize))
                
                var byteOffset = 0
                for (sector in 1..15) {
                    var authenticated = mifareClassic.authenticateSectorWithKeyA(sector, DEFAULT_KEY_A)
                    if (!authenticated) {
                        for (key in COMMON_KEYS) {
                            if (mifareClassic.authenticateSectorWithKeyA(sector, key)) {
                                authenticated = true
                                break
                            }
                        }
                    }
                    
                    if (authenticated) {
                        val firstBlock = mifareClassic.sectorToBlock(sector)
                        for (blockOffset in 0..2) {
                            val blockData = ByteArray(16)
                            System.arraycopy(buffer, byteOffset, blockData, 0, 16)
                            mifareClassic.writeBlock(firstBlock + blockOffset, blockData)
                            byteOffset += 16
                        }
                    } else {
                        byteOffset += 16 * 3
                        Log.w(TAG, "Skipping write for sector $sector (auth failed)")
                    }
                }
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error writing Master Card", e)
                false
            } finally {
                try { mifareClassic.close() } catch (e: Exception) {}
            }
        }
         
         fun readMifareUltralight(mifareUltralight: MifareUltralight): CardData? {
            return try {
                mifareUltralight.connect()
                
                // Read page 4-7 (user data area)
                val pageData = ByteArray(16)
                var offset = 0
                for (page in 4..7) {
                    val pageBytes = mifareUltralight.readPages(page)
                    System.arraycopy(pageBytes, 0, pageData, offset, 4)
                    offset += 4
                }
                
                // Convert to string
                val dataString = String(pageData, charset("UTF-8")).trim { it <= ' ' }
                
                // Get UID
                val uidBytes = mifareUltralight.tag.id
                val uid = uidBytes.joinToString("") { "%02X".format(it) }
                
                // Parse data
                if (dataString.isNotEmpty() && dataString.contains("|")) {
                    CardData.fromReadFormat(dataString, uid)
                } else {
                    CardData(uid = uid)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error reading MIFARE Ultralight", e)
                null
            } finally {
                try {
                    mifareUltralight.close()
                } catch (e: Exception) {
                    Log.e(TAG, "Error closing MIFARE Ultralight", e)
                }
            }
        }
        
        fun writeMifareUltralight(mifareUltralight: MifareUltralight, cardData: CardData): Boolean {
            return try {
                mifareUltralight.connect()
                
                // Prepare data
                val dataString = cardData.toWriteFormat()
                val dataBytes = dataString.toByteArray(charset("UTF-8"))
                
                // Write to pages 4-7 (user data area)
                var offset = 0
                for (page in 4..7) {
                    val pageData = ByteArray(4)
                    val length = minOf(4, dataBytes.size - offset)
                    if (length > 0) {
                        System.arraycopy(dataBytes, offset, pageData, 0, length)
                        mifareUltralight.writePage(page, pageData)
                    }
                    offset += 4
                    if (offset >= dataBytes.size) break
                }
                
                Log.d(TAG, "Write successful")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error writing MIFARE Ultralight", e)
                false
            } finally {
                try {
                    mifareUltralight.close()
                } catch (e: Exception) {
                    Log.e(TAG, "Error closing MIFARE Ultralight", e)
                }
            }
        }
    }
}

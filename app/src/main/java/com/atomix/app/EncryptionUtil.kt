package com.atomix.app

import android.util.Base64
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object EncryptionUtil {
    
    private const val ALGORITHM = "AES/CBC/PKCS5Padding"
    private const val KEY_ALGORITHM = "AES"
    
    fun encrypt(data: String, password: String): String {
        val key = generateKey(password)
        val cipher = Cipher.getInstance(ALGORITHM)
        val iv = ByteArray(16) { 0 } // Simple IV for demo
        cipher.init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(iv))
        val encrypted = cipher.doFinal(data.toByteArray())
        return Base64.encodeToString(encrypted, Base64.DEFAULT)
    }
    
    fun decrypt(encryptedData: String, password: String): String {
        val key = generateKey(password)
        val cipher = Cipher.getInstance(ALGORITHM)
        val iv = ByteArray(16) { 0 }
        cipher.init(Cipher.DECRYPT_MODE, key, IvParameterSpec(iv))
        val decoded = Base64.decode(encryptedData, Base64.DEFAULT)
        val decrypted = cipher.doFinal(decoded)
        return String(decrypted)
    }
    
    private fun generateKey(password: String): SecretKeySpec {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(password.toByteArray())
        return SecretKeySpec(hash, KEY_ALGORITHM)
    }
}
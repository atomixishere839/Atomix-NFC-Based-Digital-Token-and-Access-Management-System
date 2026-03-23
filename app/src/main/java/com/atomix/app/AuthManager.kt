package com.atomix.app

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AuthManager(private val context: Context) {
    
    private val userManager = UserManager(context)
    
    companion object {
        private const val PREFS_NAME = "AtomixPrefs"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_USERNAME = "username"
        private const val KEY_USER_ROLE = "user_role"
        private const val KEY_DEFAULT_TOKENS = "default_tokens"
        
        // Default credentials (kept for initial setup/failsafe)
        private const val DEFAULT_ADMIN_USERNAME = "admin"
        private const val DEFAULT_ADMIN_PASSWORD = "admin123"
    }
    
    private val prefs: SharedPreferences = 
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    suspend fun login(username: String, password: String): UserCredentials? {
        return withContext(Dispatchers.IO) {
            // 1. Check default admin
            if (username == DEFAULT_ADMIN_USERNAME && password == DEFAULT_ADMIN_PASSWORD) {
                saveLoginState(username, UserRole.ADMIN)
                return@withContext UserCredentials(username, password, UserRole.ADMIN)
            }
            
            // 2. Check cloud users
            val allUsers = userManager.getAllUsers()
            Log.d("LOGIN_DEBUG", "Checking login for: '$username'. Password provided: '$password'. Found ${allUsers.size} users in cloud.")
            
            val user = allUsers.find { it.username.trim().equals(username.trim(), ignoreCase = true) }
            
            if (user != null) {
                Log.d("LOGIN_DEBUG", "User found: '${user.username}'. Stored password: '${user.password}'")
                if (user.password.trim() == password.trim()) {
                    Log.d("LOGIN_DEBUG", "Login success for: ${user.name}")
                    saveLoginState(username, user.role)
                    return@withContext UserCredentials(username, password, user.role)
                } else {
                    Log.e("LOGIN_DEBUG", "Password mismatch for: $username. Stored: '${user.password}', Provided: '$password'")
                }
            } else {
                Log.e("LOGIN_DEBUG", "User not found in cloud: $username")
            }
            
            null
        }
    }
    
    suspend fun register(username: String, password: String, role: UserRole): Boolean {
        // Registration is now handled via UserManager.registerUser which includes password
        return true 
    }
    
    fun isLoggedIn(): Boolean {
        return prefs.getBoolean(KEY_IS_LOGGED_IN, false)
    }
    
    fun getCurrentUser(): UserCredentials? {
        if (!isLoggedIn()) return null
        
        val username = prefs.getString(KEY_USERNAME, null) ?: return null
        val roleStr = prefs.getString(KEY_USER_ROLE, null) ?: return null
        
        return try {
            val role = UserRole.valueOf(roleStr)
            UserCredentials(username, "", role)
        } catch (e: Exception) {
            null
        }
    }
    
    fun logout() {
        prefs.edit().apply {
            putBoolean(KEY_IS_LOGGED_IN, false)
            remove(KEY_USERNAME)
            remove(KEY_USER_ROLE)
            apply()
        }
    }
    
    private fun saveLoginState(username: String, role: UserRole) {
        prefs.edit().apply {
            putBoolean(KEY_IS_LOGGED_IN, true)
            putString(KEY_USERNAME, username)
            putString(KEY_USER_ROLE, role.name)
            apply()
        }
    }
    
    fun setDefaultTokens(amount: Int) {
        prefs.edit().putInt(KEY_DEFAULT_TOKENS, amount).apply()
    }
    
    fun getDefaultTokens(): Int {
        return prefs.getInt(KEY_DEFAULT_TOKENS, 500)
    }
}

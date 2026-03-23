package com.atomix.app

data class RegisteredUser(
    val id: String,
    val name: String,
    val username: String,
    val password: String = "",
    val primaryCardUID: String,
    val additionalCards: List<String> = emptyList(),
    val role: UserRole,
    val balance: Int = 500,
    val registrationDate: Long = System.currentTimeMillis(),
    val expiryDate: Long = System.currentTimeMillis() + (24 * 60 * 60 * 1000),
    val isActive: Boolean = true
) {
    fun isExpired(): Boolean {
        return System.currentTimeMillis() > expiryDate
    }
    
    fun getTimeRemaining(): String {
        val remaining = expiryDate - System.currentTimeMillis()
        if (remaining <= 0) return "EXPIRED"
        
        val hours = remaining / (60 * 60 * 1000)
        val minutes = (remaining % (60 * 60 * 1000)) / (60 * 1000)
        return "${hours}h ${minutes}m"
    }
    
    fun getAllCardUIDs(): List<String> {
        return listOf(primaryCardUID) + additionalCards
    }
    
    fun hasCard(cardUID: String): Boolean {
        return getAllCardUIDs().contains(cardUID)
    }
    
    fun getTotalCards(): Int {
        return getAllCardUIDs().size
    }
}
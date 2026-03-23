package com.atomix.app

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

class UserManager(private val context: Context) {
    
    // Cloud-First Manager: Prioritizes ApiService calls
    
    // --- User Management ---
    suspend fun registerUser(name: String, username: String, password: String, cardUID: String, role: UserRole): Boolean {
        val expiryTime = if (role == UserRole.ADMIN) Long.MAX_VALUE else System.currentTimeMillis() + (24 * 60 * 60 * 1000)
        
        val user = RegisteredUser(
            id = "user_${System.currentTimeMillis()}_${(1000..9999).random()}",
            name = name,
            username = username,
            password = password,
            primaryCardUID = cardUID,
            role = role,
            expiryDate = expiryTime
        )
        
        val success = ApiService.syncUser(user)
        if (success) {
            saveUserToLocal(user)
        }
        return success
    }

    suspend fun activateUser(userId: String): Boolean {
        val user = fetchUserById(userId) ?: return false
        val updatedUser = user.copy(isActive = true)
        val success = ApiService.syncUser(updatedUser)
        if (success) saveUserToLocal(updatedUser)
        return success
    }

    suspend fun deactivateUser(userId: String): Boolean {
        val user = fetchUserById(userId) ?: return false
        val updatedUser = user.copy(isActive = false)
        val success = ApiService.syncUser(updatedUser)
        if (success) saveUserToLocal(updatedUser)
        return success
    }

    suspend fun extendUserExpiry(userId: String, additionalHours: Int): Boolean {
        val user = fetchUserById(userId) ?: return false
        val newExpiryTime = user.expiryDate + (additionalHours * 60 * 60 * 1000L)
        val updatedUser = user.copy(expiryDate = newExpiryTime)
        val success = ApiService.syncUser(updatedUser)
        if (success) saveUserToLocal(updatedUser)
        return success
    }

    suspend fun changeUserPassword(username: String, newPassword: String): Boolean {
        val all = getAllUsers()
        val user = all.find { it.username == username } ?: return false
        val updatedUser = user.copy(password = newPassword)
        val success = ApiService.syncUser(updatedUser)
        if (success) saveUserToLocal(updatedUser)
        return success
    }

    suspend fun getAllUsers(): List<RegisteredUser> {
        val serverUsers = ApiService.fetchAllUsers()
        val users = mutableListOf<RegisteredUser>()
        val serverUserIds = mutableSetOf<String>()
        
        for (json in serverUsers) {
            val user = parseUser(json)
            if (user != null) {
                users.add(user)
                serverUserIds.add(user.id)
                saveUserToLocal(user) // Sync local cache
            }
        }
        
        // CLEANUP LOCAL CACHE: Remove users that are no longer on server
        cleanupLocalCache(serverUserIds)
        
        return users
    }

    private fun cleanupLocalCache(serverUserIds: Set<String>) {
        val prefs = context.getSharedPreferences("AtomixUserPrefs", Context.MODE_PRIVATE)
        val usersJson = prefs.getString("registered_users", "[]") ?: "[]"
        val localArray = JSONArray(usersJson)
        val cleanedList = mutableListOf<JSONObject>()
        
        for (i in 0 until localArray.length()) {
            val obj = localArray.getJSONObject(i)
            if (serverUserIds.contains(obj.getString("id"))) {
                cleanedList.add(obj)
            }
        }
        prefs.edit().putString("registered_users", JSONArray(cleanedList).toString()).apply()
    }

    suspend fun getUserByCardUID(cardUID: String): RegisteredUser? {
        val allUsers = getAllUsers()
        return allUsers.find { it.hasCard(cardUID) }
    }

    suspend fun updateUserDetails(uid: String, balance: Int, expiry: Long, active: Boolean): Boolean {
        val user = getUserByCardUID(uid) ?: return false
        val updatedUser = user.copy(balance = balance, expiryDate = expiry, isActive = active)
        val success = ApiService.syncUser(updatedUser)
        if (success) saveUserToLocal(updatedUser)
        return success
    }

    suspend fun deleteUser(userId: String): Boolean {
        // 1. Delete associated access records
        val userAccess = getUserAccess(userId)
        userAccess.forEach { access ->
            ApiService.deleteAccess(userId, access.roomId)
        }
        
        // 2. Delete user node
        val success = ApiService.deleteUser(userId)
        if (success) removeUserFromLocal(userId)
        return success
    }

    // --- Balance Management ---
    suspend fun addBalanceToUser(userId: String, amount: Int, performedBy: String = "System"): Boolean {
        val user = fetchUserById(userId) ?: return false
        val balanceBefore = user.balance
        val balanceAfter = balanceBefore + amount
        val updatedUser = user.copy(balance = balanceAfter)
        
        val success = ApiService.syncUser(updatedUser)
        if (success) {
            saveUserToLocal(updatedUser)
            recordTransaction(userId, user.primaryCardUID, TransactionType.ADD_BALANCE, amount, balanceBefore, balanceAfter, performedBy, "Balance added")
        }
        return success
    }

    suspend fun spendBalanceFromUser(userId: String, amount: Int, performedBy: String = "User"): Boolean {
        val user = fetchUserById(userId) ?: return false
        if (user.balance < amount) return false
        
        val balanceBefore = user.balance
        val balanceAfter = balanceBefore - amount
        val updatedUser = user.copy(balance = balanceAfter)
        
        val success = ApiService.syncUser(updatedUser)
        if (success) {
            saveUserToLocal(updatedUser)
            recordTransaction(userId, user.primaryCardUID, TransactionType.SPEND_BALANCE, amount, balanceBefore, balanceAfter, performedBy, "Balance spent")
        }
        return success
    }

    // --- Room Access Management ---
    suspend fun grantAccess(userId: String, roomId: String, grantedBy: String, eventId: String? = null, expiresAt: Long? = null, startMin: Int = 0, endMin: Int = 1440): Boolean {
        val access = UserAccess(
            userId = userId, 
            roomId = roomId, 
            eventId = eventId, 
            grantedBy = grantedBy, 
            expiresAt = expiresAt,
            allowedStartTime = startMin,
            allowedEndTime = endMin
        )
        return ApiService.syncAccess(access)
    }

    suspend fun hasAccess(userId: String, roomId: String): Boolean {
        val allAccess = ApiService.fetchAllAccess()
        val now = System.currentTimeMillis()
        
        // Current minutes from midnight
        val cal = java.util.Calendar.getInstance()
        val currentMin = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)

        return allAccess.any { json ->
            val matchesUser = json.optString("userId") == userId && json.optString("roomId") == roomId
            if (!matchesUser) return@any false

            val expiresAt = json.optLong("expiresAt", 0L)
            val isExpired = expiresAt != 0L && expiresAt <= now
            if (isExpired) return@any false

            val startMin = json.optInt("allowedStartTime", 0)
            val endMin = json.optInt("allowedEndTime", 1440)
            
            // Time check
            val isWithinTime = currentMin in startMin..endMin
            
            isWithinTime
        }
    }

    suspend fun getUserAccess(userId: String): List<UserAccess> {
        val allAccess = ApiService.fetchAllAccess()
        val filteredAccess = allAccess.filter { it.optString("userId") == userId }
        
        return filteredAccess.mapNotNull { json ->
            try {
                UserAccess(
                    userId = json.getString("userId"),
                    roomId = json.getString("roomId"),
                    eventId = json.optString("eventId"),
                    grantedBy = json.getString("grantedBy"),
                    grantedAt = json.getLong("grantedAt"),
                    expiresAt = json.optLong("expiresAt").takeIf { it != 0L },
                    allowedStartTime = json.optInt("allowedStartTime", 0),
                    allowedEndTime = json.optInt("allowedEndTime", 1440)
                )
            } catch (e: Exception) { null }
        }
    }

    suspend fun revokeAccess(userId: String, roomId: String): Boolean {
        return ApiService.deleteAccess(userId, roomId)
    }

    suspend fun addRoom(name: String, description: String, maxCapacity: Int = 10): String {
        val room = Room(
            id = "room_${System.currentTimeMillis()}",
            name = name,
            description = description,
            maxCapacity = maxCapacity
        )
        ApiService.syncRoom(room)
        return room.id
    }

    suspend fun getAllRooms(): List<Room> {
        val serverRooms = ApiService.fetchAllRooms()
        return serverRooms.mapNotNull { parseRoom(it) }
    }

    suspend fun updateRoom(roomId: String, name: String, description: String, maxCapacity: Int = 10): Boolean {
        val all = getAllRooms()
        val room = all.find { it.id == roomId } ?: return false
        val updatedRoom = room.copy(name = name, description = description, maxCapacity = maxCapacity)
        return ApiService.syncRoom(updatedRoom)
    }

    suspend fun deleteRoom(roomId: String): Boolean {
        return ApiService.deleteRoom(roomId)
    }

    // --- Transaction History ---
    suspend fun getAllTransactions(): List<Transaction> {
        val serverTxns = ApiService.fetchAllTransactions()
        return serverTxns.mapNotNull { parseTransaction(it) }.sortedByDescending { it.timestamp }
    }

    suspend fun getUserTransactions(userId: String): List<Transaction> {
        val all = getAllTransactions()
        return all.filter { it.userId == userId }
    }

    private suspend fun recordTransaction(userId: String, cardUID: String, type: TransactionType, amount: Int, balanceBefore: Int, balanceAfter: Int, performedBy: String, description: String) {
        val transaction = Transaction(
            id = "txn_${System.currentTimeMillis()}",
            userId = userId,
            cardUID = cardUID,
            type = type,
            amount = amount,
            balanceBefore = balanceBefore,
            balanceAfter = balanceAfter,
            performedBy = performedBy,
            description = description
        )
        ApiService.syncTransaction(transaction)
    }

    // --- Helpers ---
    private suspend fun fetchUserById(userId: String): RegisteredUser? {
        val all = getAllUsers()
        return all.find { it.id == userId }
    }

    private fun parseUser(json: JSONObject): RegisteredUser? {
        return try {
            RegisteredUser(
                id = json.getString("id"),
                name = json.getString("name"),
                username = json.getString("username"),
                password = json.optString("password", ""),
                primaryCardUID = json.getString("primaryCardUID"),
                role = UserRole.valueOf(json.getString("role")),
                balance = json.getInt("balance"),
                expiryDate = json.getLong("expiryDate"),
                isActive = json.getBoolean("isActive"),
                registrationDate = json.optLong("registrationDate", System.currentTimeMillis())
            )
        } catch (e: Exception) { null }
    }

    private fun parseRoom(json: JSONObject): Room? {
        return try {
            Room(
                id = json.getString("id"),
                name = json.getString("name"),
                description = json.getString("description"),
                isActive = json.getBoolean("isActive"),
                maxCapacity = json.optInt("maxCapacity", 10),
                currentOccupancy = json.optInt("currentOccupancy", 0)
            )
        } catch (e: Exception) { null }
    }

    suspend fun toggleRoomAccess(userId: String, roomId: String): AccessLogType? {
        val user = fetchUserById(userId) ?: return null
        val room = getAllRooms().find { it.id == roomId } ?: return null
        
        // 1. Get current status
        val statusJson = ApiService.fetchUserRoomStatus(userId, roomId)
        val currentStatus = if (statusJson != null) {
            AccessLogType.valueOf(statusJson.getString("status"))
        } else {
            AccessLogType.EXIT // Assume they were outside
        }
        
        // 2. Determine new status
        val newStatus = if (currentStatus == AccessLogType.EXIT) AccessLogType.ENTRY else AccessLogType.EXIT
        
        // 3. Capacity check for ENTRY
        if (newStatus == AccessLogType.ENTRY && room.currentOccupancy >= room.maxCapacity) {
            Log.e("ROOM_ACCESS", "Room ${room.name} is full!")
            return null // Room full
        }
        
        // 4. Update user status in cloud
        val statusSync = ApiService.syncUserRoomStatus(UserRoomStatus(userId, roomId, newStatus))
        if (!statusSync) return null
        
        // 5. Update room occupancy
        val newOccupancy = if (newStatus == AccessLogType.ENTRY) room.currentOccupancy + 1 else Math.max(0, room.currentOccupancy - 1)
        val updatedRoom = room.copy(currentOccupancy = newOccupancy)
        ApiService.syncRoom(updatedRoom)
        
        // 6. Record log
        val log = AccessLog(
            id = "log_${System.currentTimeMillis()}",
            userId = userId,
            roomId = roomId,
            userName = user.name,
            type = newStatus
        )
        ApiService.syncAccessLog(log)
        
        return newStatus
    }

    suspend fun getRoomOccupancy(roomId: String): Int {
        val rooms = getAllRooms()
        return rooms.find { it.id == roomId }?.currentOccupancy ?: 0
    }

    suspend fun getAccessLogs(roomId: String? = null): List<AccessLog> {
        val serverLogs = ApiService.fetchAllAccessLogs()
        val logs = serverLogs.mapNotNull { json ->
            try {
                AccessLog(
                    id = json.getString("id"),
                    userId = json.getString("userId"),
                    roomId = json.getString("roomId"),
                    userName = json.getString("userName"),
                    timestamp = json.getLong("timestamp"),
                    type = AccessLogType.valueOf(json.getString("type"))
                )
            } catch (e: Exception) { null }
        }
        
        return if (roomId != null) {
            logs.filter { it.roomId == roomId }.sortedByDescending { it.timestamp }
        } else {
            logs.sortedByDescending { it.timestamp }
        }
    }

    private fun parseTransaction(json: JSONObject): Transaction? {
        return try {
            Transaction(
                id = json.getString("id"),
                userId = json.getString("userId"),
                cardUID = json.getString("cardUID"),
                type = TransactionType.valueOf(json.getString("type")),
                amount = json.getInt("amount"),
                balanceBefore = json.getInt("balanceBefore"),
                balanceAfter = json.getInt("balanceAfter"),
                timestamp = json.getLong("timestamp"),
                performedBy = json.getString("performedBy"),
                description = json.getString("description")
            )
        } catch (e: Exception) { null }
    }

    // Local caching (optional but good for performance)
    private fun saveUserToLocal(user: RegisteredUser) {
        val prefs = context.getSharedPreferences("AtomixUserPrefs", Context.MODE_PRIVATE)
        val usersJson = prefs.getString("registered_users", "[]") ?: "[]"
        val array = JSONArray(usersJson)
        val list = mutableListOf<JSONObject>()
        var found = false
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            if (obj.getString("id") == user.id) {
                list.add(userToJson(user))
                found = true
            } else {
                list.add(obj)
            }
        }
        if (!found) list.add(userToJson(user))
        prefs.edit().putString("registered_users", JSONArray(list).toString()).apply()
    }

    private fun removeUserFromLocal(userId: String) {
        val prefs = context.getSharedPreferences("AtomixUserPrefs", Context.MODE_PRIVATE)
        val usersJson = prefs.getString("registered_users", "[]") ?: "[]"
        val array = JSONArray(usersJson)
        val list = mutableListOf<JSONObject>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            if (obj.getString("id") != userId) list.add(obj)
        }
        prefs.edit().putString("registered_users", JSONArray(list).toString()).apply()
    }

    private fun userToJson(user: RegisteredUser): JSONObject {
        return JSONObject().apply {
            put("id", user.id); put("name", user.name); put("username", user.username)
            put("password", user.password)
            put("primaryCardUID", user.primaryCardUID); put("role", user.role.name)
            put("balance", user.balance); put("expiryDate", user.expiryDate)
            put("isActive", user.isActive); put("registrationDate", user.registrationDate)
        }
    }

    suspend fun resetBalanceByCardUID(uid: String): Boolean {
        val user = getUserByCardUID(uid) ?: return false
        val updatedUser = user.copy(balance = 0)
        val success = ApiService.syncUser(updatedUser)
        if (success) saveUserToLocal(updatedUser)
        return success
    }

    suspend fun addCardToUser(userId: String, cardUID: String): Boolean {
        val user = fetchUserById(userId) ?: return false
        if (user.primaryCardUID == cardUID || user.additionalCards.contains(cardUID)) return true
        
        val updatedCards = user.additionalCards.toMutableList()
        updatedCards.add(cardUID)
        val updatedUser = user.copy(additionalCards = updatedCards)
        
        val success = ApiService.syncUser(updatedUser)
        if (success) saveUserToLocal(updatedUser)
        return success
    }

    suspend fun removeCardFromUser(userId: String, cardUID: String): Boolean {
        val user = fetchUserById(userId) ?: return false
        if (user.primaryCardUID == cardUID) return false // Cannot remove primary card
        
        val updatedCards = user.additionalCards.toMutableList()
        if (updatedCards.remove(cardUID)) {
            val updatedUser = user.copy(additionalCards = updatedCards)
            val success = ApiService.syncUser(updatedUser)
            if (success) saveUserToLocal(updatedUser)
            return success
        }
        return false
    }

    suspend fun getCardStats(): CardStats {
        val users = getAllUsers()
        var totalCards = 0
        var activeUsers = 0
        var expiredUsers = 0
        
        users.forEach { user ->
            totalCards += user.getAllCardUIDs().size
            if (user.isActive && !user.isExpired()) {
                activeUsers++
            } else if (user.isExpired()) {
                expiredUsers++
            }
        }
        
        val avg = if (users.isNotEmpty()) totalCards.toFloat() / users.size else 0f
        
        return CardStats(
            totalUsers = users.size,
            totalCards = totalCards,
            activeUsers = activeUsers,
            expiredUsers = expiredUsers,
            averageCardsPerUser = avg
        )
    }

    // --- Backup/Export stubs (re-routed to cloud sync where possible) ---
    suspend fun exportEncryptedData(password: String): String {
        val users = getAllUsers()
        val rooms = getAllRooms()
        val txns = getAllTransactions()
        
        val json = JSONObject().apply {
            val usersArray = JSONArray()
            users.forEach { usersArray.put(userToJson(it)) }
            put("users", usersArray)
            
            val roomsArray = JSONArray()
            rooms.forEach { room -> 
                roomsArray.put(JSONObject().apply {
                    put("id", room.id); put("name", room.name)
                    put("description", room.description); put("isActive", room.isActive)
                })
            }
            put("rooms", roomsArray)
        }
        
        return EncryptionUtil.encrypt(json.toString(), password) ?: ""
    }

    suspend fun importAllData(plainData: String): Boolean {
        return try {
            val json = JSONObject(plainData)
            val usersArray = json.optJSONArray("users")
            if (usersArray != null) {
                for (i in 0 until usersArray.length()) {
                    val userObj = usersArray.getJSONObject(i)
                    val user = parseUser(userObj)
                    if (user != null) ApiService.syncUser(user)
                }
            }
            true
        } catch (e: Exception) { false }
    }

    // --- Backward compatibility stubs (to prevent UI crashes) ---
    suspend fun isCardRegistered(uid: String): Boolean {
        return getUserByCardUID(uid) != null
    }

    suspend fun isUsernameExists(username: String): Boolean {
        val all = getAllUsers()
        return all.any { it.username == username }
    }

    suspend fun importUserFromCloud(id: String, name: String, username: String, password: String, cardUID: String, role: UserRole, balance: Int, expiryDate: Long, isActive: Boolean): Boolean {
        val user = RegisteredUser(
            id = id,
            name = name,
            username = username,
            password = password,
            primaryCardUID = cardUID,
            role = role,
            balance = balance,
            expiryDate = expiryDate,
            isActive = isActive
        )
        val success = ApiService.syncUser(user)
        if (success) saveUserToLocal(user)
        return success
    }

    suspend fun syncBalanceFromCard(id: String, balance: Int): Boolean {
        val user = fetchUserById(id) ?: return false
        val updatedUser = user.copy(balance = balance)
        val success = ApiService.syncUser(updatedUser)
        if (success) saveUserToLocal(updatedUser)
        return success
    }

    fun getUserBalance(id: String): Int { return 0 }
}

package com.atomix.app

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ApiService {
    
    companion object {
        private const val TAG = "ApiService"
        private const val FIREBASE_URL = "https://atomix-70dfb-default-rtdb.firebaseio.com/"
        private val JSON = "application/json; charset=utf-8".toMediaType()
        
        private val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()

        // General Helper for Firebase REST
        private suspend fun performPut(path: String, json: String): Boolean {
            return withContext(Dispatchers.IO) {
                try {
                    val body = json.toRequestBody(JSON)
                    val request = Request.Builder().url("$FIREBASE_URL$path.json").put(body).build()
                    client.newCall(request).execute().isSuccessful
                } catch (e: Exception) {
                    Log.e(TAG, "PUT Error on $path", e)
                    false
                }
            }
        }

        private suspend fun performGet(path: String): String? {
            return withContext(Dispatchers.IO) {
                try {
                    val request = Request.Builder().url("$FIREBASE_URL$path.json").get().build()
                    val response = client.newCall(request).execute()
                    if (response.isSuccessful) response.body?.string() else null
                } catch (e: Exception) {
                    Log.e(TAG, "GET Error on $path", e)
                    null
                }
            }
        }

        private suspend fun performDelete(path: String): Boolean {
            return withContext(Dispatchers.IO) {
                try {
                    val request = Request.Builder().url("$FIREBASE_URL$path.json").delete().build()
                    client.newCall(request).execute().isSuccessful
                } catch (e: Exception) {
                    Log.e(TAG, "DELETE Error on $path", e)
                    false
                }
            }
        }

        // --- User API ---
        suspend fun syncUser(user: RegisteredUser): Boolean {
            val json = JSONObject().apply {
                put("id", user.id)
                put("name", user.name)
                put("username", user.username)
                put("password", user.password) // SYNC PASSWORD
                put("primaryCardUID", user.primaryCardUID)
                put("balance", user.balance)
                put("role", user.role.name)
                put("expiryDate", user.expiryDate)
                put("isActive", user.isActive)
                put("registrationDate", user.registrationDate)
                put("lastUpdated", System.currentTimeMillis())
            }
            return performPut("users/${user.id}", json.toString())
        }

        suspend fun fetchAllUsers(): List<JSONObject> {
            val result = performGet("users") ?: return emptyList()
            if (result == "null") return emptyList()
            val jsonObject = JSONObject(result)
            val list = mutableListOf<JSONObject>()
            val keys = jsonObject.keys()
            while (keys.hasNext()) {
                list.add(jsonObject.getJSONObject(keys.next()))
            }
            return list
        }

        suspend fun deleteUser(userId: String): Boolean = performDelete("users/$userId")

        // --- Room API ---
        suspend fun syncRoom(room: Room): Boolean {
            val json = JSONObject().apply {
                put("id", room.id)
                put("name", room.name)
                put("description", room.description)
                put("isActive", room.isActive)
                put("maxCapacity", room.maxCapacity)
                put("currentOccupancy", room.currentOccupancy)
            }
            return performPut("rooms/${room.id}", json.toString())
        }

        suspend fun syncAccessLog(log: AccessLog): Boolean {
            val json = JSONObject().apply {
                put("id", log.id)
                put("userId", log.userId)
                put("roomId", log.roomId)
                put("userName", log.userName)
                put("timestamp", log.timestamp)
                put("type", log.type.name)
            }
            return performPut("access_logs/${log.id}", json.toString())
        }

        suspend fun fetchAllAccessLogs(): List<JSONObject> {
            val result = performGet("access_logs") ?: return emptyList()
            if (result == "null") return emptyList()
            val jsonObject = JSONObject(result)
            val list = mutableListOf<JSONObject>()
            val keys = jsonObject.keys()
            while (keys.hasNext()) {
                list.add(jsonObject.getJSONObject(keys.next()))
            }
            return list
        }

        suspend fun syncUserRoomStatus(status: UserRoomStatus): Boolean {
            val json = JSONObject().apply {
                put("userId", status.userId)
                put("roomId", status.roomId)
                put("status", status.status.name)
                put("lastTapTime", status.lastTapTime)
            }
            return performPut("user_room_status/${status.userId}_${status.roomId}", json.toString())
        }

        suspend fun fetchUserRoomStatus(userId: String, roomId: String): JSONObject? {
            val result = performGet("user_room_status/${userId}_${roomId}") ?: return null
            if (result == "null") return null
            return JSONObject(result)
        }

        suspend fun fetchAllRooms(): List<JSONObject> {
            val result = performGet("rooms") ?: return emptyList()
            if (result == "null") return emptyList()
            val jsonObject = JSONObject(result)
            val list = mutableListOf<JSONObject>()
            val keys = jsonObject.keys()
            while (keys.hasNext()) {
                list.add(jsonObject.getJSONObject(keys.next()))
            }
            return list
        }

        suspend fun deleteRoom(roomId: String): Boolean = performDelete("rooms/$roomId")

        // --- Access API ---
        suspend fun syncAccess(access: UserAccess): Boolean {
            val json = JSONObject().apply {
                put("userId", access.userId)
                put("roomId", access.roomId)
                put("grantedBy", access.grantedBy)
                put("grantedAt", access.grantedAt)
                put("expiresAt", access.expiresAt ?: 0L)
                put("eventId", access.eventId ?: "")
                put("allowedStartTime", access.allowedStartTime)
                put("allowedEndTime", access.allowedEndTime)
            }
            // Use a combined key for access: userId_roomId
            return performPut("access/${access.userId}_${access.roomId}", json.toString())
        }

        suspend fun fetchAllAccess(): List<JSONObject> {
            val result = performGet("access") ?: return emptyList()
            if (result == "null") return emptyList()
            val jsonObject = JSONObject(result)
            val list = mutableListOf<JSONObject>()
            val keys = jsonObject.keys()
            while (keys.hasNext()) {
                list.add(jsonObject.getJSONObject(keys.next()))
            }
            return list
        }

        suspend fun deleteAccess(userId: String, roomId: String): Boolean = performDelete("access/${userId}_${roomId}")

        // --- Transaction API ---
        suspend fun syncTransaction(transaction: Transaction): Boolean {
            val json = JSONObject().apply {
                put("id", transaction.id)
                put("userId", transaction.userId)
                put("cardUID", transaction.cardUID)
                put("type", transaction.type.name)
                put("amount", transaction.amount)
                put("balanceBefore", transaction.balanceBefore)
                put("balanceAfter", transaction.balanceAfter)
                put("timestamp", transaction.timestamp)
                put("performedBy", transaction.performedBy)
                put("description", transaction.description)
            }
            return performPut("transactions/${transaction.id}", json.toString())
        }

        suspend fun fetchAllTransactions(): List<JSONObject> {
            val result = performGet("transactions") ?: return emptyList()
            if (result == "null") return emptyList()
            val jsonObject = JSONObject(result)
            val list = mutableListOf<JSONObject>()
            val keys = jsonObject.keys()
            while (keys.hasNext()) {
                list.add(jsonObject.getJSONObject(keys.next()))
            }
            return list
        }
    }
}

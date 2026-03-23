package com.atomix.app

data class Room(
    val id: String,
    val name: String,
    val description: String = "",
    val isActive: Boolean = true,
    val maxCapacity: Int = 10,
    val currentOccupancy: Int = 0
)

data class AccessLog(
    val id: String,
    val userId: String,
    val roomId: String,
    val userName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val type: AccessLogType // ENTRY or EXIT
)

enum class AccessLogType {
    ENTRY, EXIT
}

data class UserRoomStatus(
    val userId: String,
    val roomId: String,
    val status: AccessLogType, // ENTRY means user is inside
    val lastTapTime: Long = System.currentTimeMillis()
)

data class Event(
    val id: String,
    val name: String,
    val roomId: String,
    val startTime: Long,
    val endTime: Long,
    val isActive: Boolean = true
)

data class UserAccess(
    val userId: String,
    val roomId: String,
    val eventId: String? = null,
    val grantedBy: String,
    val grantedAt: Long = System.currentTimeMillis(),
    val expiresAt: Long? = null,
    val allowedStartTime: Int = 0, // Minutes from midnight (e.g., 540 = 9 AM)
    val allowedEndTime: Int = 1440 // Minutes from midnight (e.g., 1080 = 6 PM)
)
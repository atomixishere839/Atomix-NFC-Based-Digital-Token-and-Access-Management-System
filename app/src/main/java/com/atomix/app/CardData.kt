package com.atomix.app

data class CardData(
    val uid: String = "",
    val userId: String = "",
    val balance: Int = 0,
    val role: UserRole = UserRole.USER,
    val year: String = ""
) {
    fun toWriteFormat(): String {
        return "$userId|$balance|$year"
    }
    
    companion object {
        fun fromReadFormat(data: String, uid: String): CardData {
            return try {
                val parts = data.split("|")
                if (parts.size >= 3) {
                    CardData(
                        uid = uid,
                        userId = parts[0],
                        balance = parts[1].toIntOrNull() ?: 0,
                        year = parts[2],
                        role = if (parts.size >= 4) {
                            try {
                                UserRole.valueOf(parts[3])
                            } catch (e: IllegalArgumentException) {
                                UserRole.USER
                            }
                        } else {
                            UserRole.USER
                        }
                    )
                } else {
                    CardData(uid = uid)
                }
            } catch (e: Exception) {
                CardData(uid = uid)
            }
        }
    }
}

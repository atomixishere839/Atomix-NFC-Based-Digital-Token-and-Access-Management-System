package com.atomix.app

data class CardStats(
    val totalUsers: Int,
    val totalCards: Int,
    val activeUsers: Int,
    val expiredUsers: Int,
    val averageCardsPerUser: Float
)
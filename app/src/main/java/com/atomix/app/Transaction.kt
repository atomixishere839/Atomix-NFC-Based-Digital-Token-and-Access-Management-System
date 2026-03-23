package com.atomix.app

data class Transaction(
    val id: String,
    val userId: String,
    val cardUID: String,
    val type: TransactionType,
    val amount: Int,
    val balanceBefore: Int,
    val balanceAfter: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val performedBy: String,
    val description: String = ""
)

enum class TransactionType {
    ADD_BALANCE,
    SPEND_BALANCE,
    INITIAL_BALANCE,
    ADMIN_ADJUSTMENT
}
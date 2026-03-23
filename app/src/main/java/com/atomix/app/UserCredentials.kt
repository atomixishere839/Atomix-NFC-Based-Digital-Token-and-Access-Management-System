package com.atomix.app

data class UserCredentials(
    val username: String,
    val password: String,
    val role: UserRole
)

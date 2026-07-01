package com.example.animalalert.model

data class LoginRequest(val email: String, val password: String)

data class RegisterRequest(val name: String, val email: String, val password: String)

data class AuthResponse(
    val status: String,
    val message: String? = null,
    val token: String? = null,
    val user: UserInfo? = null
)

data class UserInfo(val name: String, val email: String)

package com.example.mycardemo.models



data class User(
    val name: String,
    val email: String,
    val birthDate: String,
    val password: String
)

data class LoginRequest(
    val email: String,
    val password: String
)

data class AuthResponse(
    val success: Boolean,
    val message: String,
    val user: UserData? = null,
    val token: String? = null
)

data class UserData(
    val id: Int,
    val name: String,
    val email: String,
    val birthDate: String
)

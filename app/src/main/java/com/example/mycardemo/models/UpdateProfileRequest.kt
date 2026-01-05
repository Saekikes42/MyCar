package com.example.mycardemo.models

data class UpdateProfileRequest(
    val name: String,
    val email: String,
    val birthDate: String,
    val password: String
)
package com.example.mycardemo.models

data class CartItem(
    val id: Int,
    val carName: String,
    val variantDesc: String,
    val imageUrl: String,
    val price: Double,
    val quantity: Int
)
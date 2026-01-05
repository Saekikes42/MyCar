package com.example.mycardemo.models

data class Order(
    val id: Int,
    val userId: Int,
    val item: CartItem,
    val clientName: String,
    val clientEmail: String,
    val deliveryMethod: String,
    val salonLocation: String,
    val paymentMethod: String,
    val requestDate: String,
    val status: String,
    val notes: String
)


data class OrderRequest(
    val userId: Int,
    val itemId: Int,
    val clientName: String,
    val clientEmail: String,
    val deliveryMethod: String,
    val salonLocation: String,
    val paymentMethod: String,
    val notes: String
)

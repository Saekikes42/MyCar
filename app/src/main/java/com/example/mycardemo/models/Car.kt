package com.example.mycardemo.models

data class Car(
    val id: Int,
    val brand: String,
    val model: String,
    val quantity: Int,
    val variants: List<Variant>
)

data class Variant(
    val year: Int,
    val colors: List<ColorOption>
)

data class ColorOption(
    val name: String,
    val images: List<String>,
    val complectations: List<Complectation>
)

data class Complectation(
    val id: Int,
    val name: String,
    val price: Double,
    val description: String,
    val imageUrl: String
)

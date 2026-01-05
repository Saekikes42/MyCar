package com.example.mycardemo.ui

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.example.mycardemo.R
import com.example.mycardemo.api.ApiClient
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {
    private lateinit var carsContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        carsContainer = findViewById(R.id.llDataContainer)
        setupBottomNavigation(findViewById<BottomNavigationView>(R.id.bottomNav), R.id.nav_home)
        loadCars()
    }

    private fun loadCars() {
        ApiClient.getCars { success, cars ->
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                carsContainer.removeAllViews()
                if (!success || cars == null) {
                    Toast.makeText(this, "Не удалось загрузить каталог", Toast.LENGTH_SHORT).show()
                    return@runOnUiThread
                }
                if (cars.isEmpty()) {
                    Toast.makeText(this, "Каталог пока пуст", Toast.LENGTH_SHORT).show()
                }
                cars.forEach { car ->
                    val view = layoutInflater.inflate(R.layout.main_item, carsContainer, false)
                    val minimumPrice = car.variants.flatMap { it.colors }
                        .flatMap { it.complectations }.minOfOrNull { it.price } ?: 0.0
                    val image = car.variants.firstOrNull()?.colors?.firstOrNull()?.images?.firstOrNull().orEmpty()
                    view.findViewById<TextView>(R.id.tvTitle).text = "${car.brand} ${car.model}"
                    view.findViewById<TextView>(R.id.tvPrice).text =
                        "от ${String.format("%,.0f", minimumPrice).replace(',', ' ')} ₽"
                    Glide.with(this).load(ApiClient.imageUrl(image))
                        .placeholder(R.drawable.car_image).error(R.drawable.car_image)
                        .into(view.findViewById<ImageView>(R.id.imageCar))
                    view.setOnClickListener {
                        startActivity(Intent(this, CarDetailActivity::class.java).putExtra("carId", car.id))
                    }
                    carsContainer.addView(view)
                }
            }
        }
    }
}

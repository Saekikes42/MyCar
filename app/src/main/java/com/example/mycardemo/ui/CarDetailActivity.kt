package com.example.mycardemo.ui

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.example.mycardemo.R
import com.example.mycardemo.api.ApiClient
import com.example.mycardemo.data.SessionStore
import com.example.mycardemo.databinding.ActivityEquipmentBinding
import com.example.mycardemo.models.Car
import com.example.mycardemo.models.CartItem

class CarDetailActivity : AppCompatActivity() {
    private lateinit var binding: ActivityEquipmentBinding
    private var car: Car? = null
    private var selectedYear = 2025
    private var selectedTrim = "Стандарт"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEquipmentBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val carId = intent.getIntExtra("carId", -1)
        if (carId == -1 || SessionStore(this).userId == -1) {
            finish()
            return
        }
        selectedYear = savedInstanceState?.getInt("year") ?: 2025
        selectedTrim = savedInstanceState?.getString("trim") ?: "Стандарт"
        binding.backButton.setOnClickListener { finish() }
        binding.addCartButton.isEnabled = false
        binding.addCartButton.setOnClickListener { addToCart() }
        binding.year2025.setOnClickListener { selectYear(2025) }
        binding.year2024.setOnClickListener { selectYear(2024) }
        trimViews().forEach { (name, view) ->
            view.setOnClickListener { selectedTrim = name; renderSelection() }
        }
        // The demo catalogue contains white cars; hide unavailable colour swatches.
        for (index in 1 until binding.colorPicker.childCount) {
            binding.colorPicker.getChildAt(index).visibility = View.GONE
        }
        ApiClient.getCarById(carId) { success, result ->
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (success && result != null) {
                    car = result
                    if (result.variants.none { it.year == selectedYear }) {
                        selectedYear = result.variants.firstOrNull()?.year ?: selectedYear
                    }
                    renderSelection()
                } else {
                    Toast.makeText(this, "Ошибка загрузки автомобиля", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("year", selectedYear)
        outState.putString("trim", selectedTrim)
        super.onSaveInstanceState(outState)
    }

    private fun trimViews(): Map<String, LinearLayout> = mapOf(
        "Стандарт" to binding.trimStandard,
        "Комфорт" to binding.trimComfort,
        "Люкс" to binding.trimLUCS
    )

    private fun selectYear(year: Int) {
        if (car?.variants?.any { it.year == year } != true) return
        selectedYear = year
        renderSelection()
    }

    private fun renderSelection() {
        val currentCar = car ?: return
        val color = currentCar.variants.firstOrNull { it.year == selectedYear }?.colors?.firstOrNull()
        val trim = color?.complectations?.firstOrNull { it.name == selectedTrim }
        binding.titleText.text = "${currentCar.brand} ${currentCar.model}"
        mapOf(2025 to binding.year2025, 2024 to binding.year2024).forEach { (year, view) ->
            view.isEnabled = currentCar.variants.any { it.year == year }
            view.alpha = if (view.isEnabled) 1f else 0.4f
            view.setBackgroundResource(if (year == selectedYear) R.drawable.bg_yers_active else R.drawable.bg_yers_inactive)
            view.setTextColor(getColor(if (year == selectedYear) R.color.text_button else R.color.text_primary))
        }
        trimViews().forEach { (name, view) ->
            val option = color?.complectations?.firstOrNull { it.name == name }
            view.isEnabled = option != null
            view.alpha = if (option != null) 1f else 0.4f
            view.setBackgroundResource(if (name == selectedTrim) R.drawable.bg_comlect_active else R.drawable.bg_comlect_inactive)
            (view.getChildAt(1) as TextView).text = option?.let { formatPrice(it.price) } ?: "Недоступно"
        }
        binding.totalPrice.text = trim?.let { formatPrice(it.price) } ?: "Недоступно"
        binding.addCartButton.isEnabled = trim != null
        Glide.with(this).load(ApiClient.imageUrl(trim?.imageUrl.orEmpty()))
            .placeholder(R.drawable.car_image).into(binding.carImage)
    }

    private fun addToCart() {
        val currentCar = car ?: return
        val color = currentCar.variants.firstOrNull { it.year == selectedYear }?.colors?.firstOrNull() ?: return
        val trim = color.complectations.firstOrNull { it.name == selectedTrim } ?: return
        val item = CartItem(trim.id, "${currentCar.brand} ${currentCar.model}",
            "$selectedYear год, ${color.name}, ${trim.name}", trim.imageUrl, trim.price, 1)
        binding.addCartButton.isEnabled = false
        binding.addCartButton.text = "Добавляется..."
        ApiClient.addToCart(item, SessionStore(this).userId) { success ->
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                binding.addCartButton.isEnabled = true
                binding.addCartButton.text = "В корзину"
                Toast.makeText(this, if (success) "Добавлено в корзину" else "Ошибка добавления", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun formatPrice(price: Double) = String.format("%,.0f ₽", price).replace(',', ' ')
}

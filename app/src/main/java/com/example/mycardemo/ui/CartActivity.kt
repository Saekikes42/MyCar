package com.example.mycardemo.ui

import com.example.mycardemo.R
import com.example.mycardemo.data.SessionStore

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.example.mycardemo.api.ApiClient
import com.example.mycardemo.models.CartItem
import com.google.android.material.button.MaterialButton

class CartActivity : AppCompatActivity() {

    private lateinit var backButton: ImageButton
    private lateinit var titleText: TextView
    private lateinit var llDataContainer: LinearLayout
    private lateinit var bottomNav: com.google.android.material.bottomnavigation.BottomNavigationView

    private val cartItems = mutableListOf<CartItem>()
    private var userId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cart)

        userId = getUserId()

        if (userId == -1) {
            Toast.makeText(this, "Пожалуйста, авторизуйтесь", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initViews()
    }

    override fun onResume() {
        super.onResume()
        if (userId != -1) loadCartFromApi()
    }

    private fun initViews() {
        backButton = findViewById(R.id.backButton)
        titleText = findViewById(R.id.titleText)
        llDataContainer = findViewById(R.id.llDataContainer)
        bottomNav = findViewById(R.id.bottomNav)

        backButton.setOnClickListener { finish() }
        titleText.text = "Корзина"
        setupBottomNavigation(bottomNav, R.id.nav_cart)
    }

    private fun getUserId(): Int {
        return SessionStore(this).userId
    }

    private fun loadCartFromApi() {
        llDataContainer.removeAllViews()

        ApiClient.getCart(userId) { success, items, _, _ ->
            runOnUiThread {
                if (success && items != null) {
                    cartItems.clear()
                    cartItems.addAll(items)
                    displayCartItems()
                } else {
                    Toast.makeText(this, "Не удалось загрузить корзину", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun displayCartItems() {
        for (item in cartItems) {
            val view = layoutInflater.inflate(R.layout.cart_item, null)

            Glide.with(this)
                .load(ApiClient.imageUrl(item.imageUrl))
                .placeholder(R.drawable.car_image)
                .error(R.drawable.car_image)
                .into(view.findViewById(R.id.imageCar))

            view.findViewById<TextView>(R.id.tvTitle).text = item.carName
            view.findViewById<TextView>(R.id.tvDesc).text = item.variantDesc
            view.findViewById<TextView>(R.id.tvPrice).text = formatPrice(item.price * item.quantity)

            view.findViewById<ImageView>(R.id.btnClose).setOnClickListener {
                removeItemById(item.id)
            }

            view.findViewById<MaterialButton>(R.id.btnCheckout).setOnClickListener {
                goToCheckout(item)
            }

            llDataContainer.addView(view)
        }

        if (cartItems.isEmpty()) {
            Toast.makeText(this, "Корзина пуста", Toast.LENGTH_SHORT).show()
        }
    }

    private fun goToCheckout(item: CartItem) {
        val intent = Intent(this, OrderActivity::class.java).apply {
            putExtra("itemId", item.id)
        }
        startActivity(intent)
    }

    private fun removeItemById(itemId: Int) {
        val item = cartItems.find { it.id == itemId } ?: return

        val updatedItem = item.copy(quantity = 0)

        ApiClient.updateQuantity(userId, updatedItem) { success ->
            runOnUiThread {
                if (success) {
                    loadCartFromApi()
                    Toast.makeText(this@CartActivity, "✓ Товар удален", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@CartActivity, "✗ Ошибка удаления", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun formatPrice(price: Double): String =
        String.format("%,.0f ₽", price).replace(",", " ")
}

package com.example.mycardemo.ui

import com.example.mycardemo.R
import com.example.mycardemo.data.SessionStore

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.example.mycardemo.api.ApiClient
import com.example.mycardemo.models.CartItem
import com.example.mycardemo.models.OrderRequest

class OrderActivity : AppCompatActivity() {

    private lateinit var backButton: ImageButton
    private lateinit var imageCar: ImageView
    private lateinit var tvTitle: TextView
    private lateinit var tvDesc: TextView
    private lateinit var rgDelivery: RadioGroup
    private lateinit var rbHome: RadioButton
    private lateinit var rbFromSalon: RadioButton
    private lateinit var etSalonAddress: AutoCompleteTextView
    private lateinit var etEmail: EditText
    private lateinit var rgPayment: RadioGroup
    private lateinit var rbCredit: RadioButton
    private lateinit var rbCash: RadioButton
    private lateinit var tvPrice: TextView
    private lateinit var addButton: Button

    private var cartItem: CartItem? = null
    private var userId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_purchase)

        userId = getUserId()
        if (userId == -1) {
            finish()
            return
        }

        initViews()

        val itemId = intent.getIntExtra("itemId", -1)

        if (itemId == -1) {
            Toast.makeText(this, "Ошибка: товар не найден", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        loadCartItem(itemId)
        setupListeners()
    }

    private fun initViews() {
        backButton = findViewById(R.id.backButton)
        imageCar = findViewById(R.id.imageCar)
        tvTitle = findViewById(R.id.tvTitle)
        tvDesc = findViewById(R.id.tvDesc)
        rgDelivery = findViewById(R.id.rgDelivery)
        rbHome = findViewById(R.id.rbHome)
        rbFromSalon = findViewById(R.id.rbFromSalon)
        etSalonAddress = findViewById(R.id.etSalonAddress)
        etEmail = findViewById(R.id.etEmail)
        rgPayment = findViewById(R.id.rgPayment)
        rbCredit = findViewById(R.id.rbCredit)
        rbCash = findViewById(R.id.rbCash)
        tvPrice = findViewById(R.id.tvPrice)
        addButton = findViewById(R.id.addButton)

        backButton.setOnClickListener { finish() }
    }

    private fun setupListeners() {
        rgDelivery.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rbHome -> {
                    etEmail.visibility = View.VISIBLE
                    etSalonAddress.visibility = View.GONE
                }
                R.id.rbFromSalon -> {
                    etEmail.visibility = View.GONE
                    etSalonAddress.visibility = View.VISIBLE
                }
            }
        }

        addButton.setOnClickListener { submitOrder() }
    }

    private fun loadCartItem(itemId: Int) {
        ApiClient.getCart(userId) { success, items, _, _ ->
            runOnUiThread {
                if (success && items != null) {
                    cartItem = items.find { it.id == itemId }
                    if (cartItem != null) {
                        displayItem()
                    } else {
                        Toast.makeText(this, "Товар не найден", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                } else {
                    Toast.makeText(this, "Ошибка загрузки", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }

    private fun displayItem() {
        val item = cartItem ?: return

        Glide.with(this)
            .load(ApiClient.imageUrl(item.imageUrl))
            .placeholder(R.drawable.car_image)
            .error(R.drawable.car_image)
            .into(imageCar)

        tvTitle.text = item.carName
        tvDesc.text = item.variantDesc
        tvPrice.text = formatPrice(item.price * item.quantity)
    }

    private fun submitOrder() {
        val item = cartItem ?: run {
            Toast.makeText(this, "Товар не загружен", Toast.LENGTH_SHORT).show()
            return
        }

        val clientName = getUserName()
        val clientEmail = getUserEmail()
        val deliveryMethod = if (rbFromSalon.isChecked) "Салон" else "Доставка"
        val salonLocation = if (rbFromSalon.isChecked) {
            etSalonAddress.text.toString()
        } else {
            etEmail.text.toString()
        }
        val paymentMethod = if (rbCredit.isChecked) "Кредит" else "Наличные"

        if (clientName.isEmpty() || clientEmail.isEmpty()) {
            Toast.makeText(this, "Заполните профиль", Toast.LENGTH_SHORT).show()
            return
        }

        if (salonLocation.isEmpty()) {
            Toast.makeText(this, "Выберите адрес", Toast.LENGTH_SHORT).show()
            return
        }

        val orderRequest = OrderRequest(
            userId = userId,
            itemId = item.id,
            clientName = clientName,
            clientEmail = clientEmail,
            deliveryMethod = deliveryMethod,
            salonLocation = salonLocation,
            paymentMethod = paymentMethod,
            notes = ""
        )

        addButton.isEnabled = false
        addButton.text = "Оформляется..."
        ApiClient.createOrder(orderRequest) { success ->
            runOnUiThread {
                addButton.isEnabled = true
                addButton.text = "Подтвердить заказ"

                if (success) {
                    Toast.makeText(this, "Заказ создан", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Toast.makeText(this, "Ошибка создания заказа", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun getUserId(): Int {
        return SessionStore(this).userId
    }

    private fun getUserName(): String {
        return SessionStore(this).userName
    }

    private fun getUserEmail(): String {
        return SessionStore(this).userEmail
    }

    private fun formatPrice(price: Double): String =
        String.format("%,.0f ₽", price).replace(",", " ")
}

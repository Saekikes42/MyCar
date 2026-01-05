package com.example.mycardemo.ui

import com.example.mycardemo.R
import com.example.mycardemo.data.SessionStore

import android.os.Bundle
import android.text.TextWatcher
import android.text.Editable
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.mycardemo.api.ApiClient
import com.example.mycardemo.models.Order

class OrdersActivity : AppCompatActivity() {

    private lateinit var backButton: ImageButton
    private lateinit var titleText: TextView
    private lateinit var etSearch: EditText
    private lateinit var filterNew: TextView
    private lateinit var filterOld: TextView
    private lateinit var filterActive: TextView
    private lateinit var filterApplied: TextView
    private lateinit var llDataContainer: LinearLayout

    private val orders = mutableListOf<Order>()
    private val filteredOrders = mutableListOf<Order>()
    private var userId: Int = -1
    private var currentFilter = "all"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_orders)

        userId = getUserId()
        if (userId == -1) {
            finish()
            return
        }

        initViews()
        setupListeners()
        loadOrders()
    }

    private fun initViews() {
        backButton = findViewById(R.id.backButton)
        titleText = findViewById(R.id.titleText)
        etSearch = findViewById(R.id.etSearch)
        filterNew = findViewById(R.id.filterNew)
        filterOld = findViewById(R.id.filterOld)
        filterActive = findViewById(R.id.filterActive)
        filterApplied = findViewById(R.id.filterApplied)
        llDataContainer = findViewById(R.id.llDataContainer)

        backButton.setOnClickListener { finish() }
    }

    private fun setupListeners() {
        filterNew.setOnClickListener { applyFilter("all") }
        filterOld.setOnClickListener { applyFilter("Pending") }
        filterActive.setOnClickListener { applyFilter("Approved") }
        filterApplied.setOnClickListener { applyFilter("Cancelled") }

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterBySearch(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun loadOrders() {
        llDataContainer.removeAllViews()

        ApiClient.getOrders(userId) { success, orderList ->
            runOnUiThread {
                if (success && orderList != null) {

                    orders.clear()
                    orders.addAll(orderList)
                    applyFilter("all")
                } else {

                    Toast.makeText(this, "Ошибка загрузки заказов", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun applyFilter(filter: String) {
        currentFilter = filter
        updateFilterUI()

        filteredOrders.clear()
        if (filter == "all") {
            filteredOrders.addAll(orders)
        } else {
            filteredOrders.addAll(orders.filter { it.status == filter })
        }

        filterBySearch(etSearch.text.toString())
    }

    private fun filterBySearch(query: String) {
        llDataContainer.removeAllViews()

        val results = if (query.isEmpty()) {
            filteredOrders
        } else {
            filteredOrders.filter {
                it.item.carName.contains(query, ignoreCase = true) ||
                        it.clientName.contains(query, ignoreCase = true)
            }
        }

        if (results.isEmpty()) {
            val emptyView = TextView(this).apply {
                text = "Нет заказов"
                textSize = 16f
                setTextColor(getColor(android.R.color.darker_gray))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(16, 32, 16, 32) }
            }
            llDataContainer.addView(emptyView)
        } else {
            for (order in results) {
                val cardView = layoutInflater.inflate(R.layout.orders_item, null)
                displayOrderCard(cardView, order)
                llDataContainer.addView(cardView)
            }
        }
    }

    private fun displayOrderCard(cardView: android.view.View, order: Order) {
        val tvTitle = cardView.findViewById<TextView>(R.id.tvTitle)
        val tvDesc = cardView.findViewById<TextView>(R.id.tvDesc)
        val imageCar = cardView.findViewById<ImageView>(R.id.imageCar)
        val tvPrice = cardView.findViewById<TextView>(R.id.tvPrice)
        val tvSalon = cardView.findViewById<TextView>(R.id.tvSalon)
        val tvStatus = cardView.findViewById<TextView>(R.id.tvStatus)
        val btnClose = cardView.findViewById<ImageView>(R.id.btnClose)

        tvTitle.text = order.item.carName
        tvDesc.text = order.item.variantDesc
        tvPrice.text = formatPrice(order.item.price.toDouble())
        tvSalon.text = order.salonLocation
        tvStatus.text = getStatusText(order.status)
        tvStatus.setTextColor(getStatusColor(order.status))

        com.bumptech.glide.Glide.with(this)
            .load(ApiClient.imageUrl(order.item.imageUrl))
            .placeholder(R.drawable.car_image)
            .error(R.drawable.car_image)
            .into(imageCar)

        btnClose.visibility = android.view.View.GONE
    }

    private fun getStatusText(status: String): String = when (status) {
        "Pending" -> "В обработке"
        "Approved" -> "Одобрен"
        "Cancelled" -> "Отменён"
        else -> status
    }

    private fun getStatusColor(status: String): Int = when (status) {
        "Pending" -> getColor(android.R.color.holo_orange_light)
        "Approved" -> getColor(android.R.color.holo_green_light)
        "Cancelled" -> getColor(android.R.color.holo_red_light)
        else -> getColor(android.R.color.darker_gray)
    }

    private fun updateFilterUI() {
        filterNew.alpha = if (currentFilter == "all") 1.0f else 0.6f
        filterOld.alpha = if (currentFilter == "Pending") 1.0f else 0.6f
        filterActive.alpha = if (currentFilter == "Approved") 1.0f else 0.6f
        filterApplied.alpha = if (currentFilter == "Cancelled") 1.0f else 0.6f
    }

    private fun getUserId(): Int {
        return SessionStore(this).userId
    }

    private fun formatPrice(price: Double): String =
        String.format("%,.0f ₽", price).replace(",", " ")
}

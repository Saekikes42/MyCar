package com.example.mycardemo.ui

import com.example.mycardemo.R
import com.example.mycardemo.data.SessionStore

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.mycardemo.api.ApiClient
import com.example.mycardemo.models.*
import com.google.android.material.bottomnavigation.BottomNavigationView
import android.widget.ScrollView

class ProfileActivity : AppCompatActivity() {

    private lateinit var textName: TextView
    private lateinit var tvEmail: TextView
    private lateinit var mainScrollView: ScrollView

    private lateinit var editFormLayout: LinearLayout
    private lateinit var etName: EditText
    private lateinit var etEmail: EditText
    private lateinit var etBirthDate: EditText
    private lateinit var btnUpdate: Button
    private lateinit var ivCloseEdit: ImageView

    private lateinit var editDataRow: LinearLayout

    private lateinit var rowMyOrders: LinearLayout

    private lateinit var ivChevronEdit: ImageView

    private lateinit var bottomNav: BottomNavigationView

    private var userId: Int = 0
    private var isEditMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        userId = SessionStore(this).userId

        initViews()
        loadUserData()
        setupClickListeners()
    }

    private fun initViews() {
        textName = findViewById(R.id.textName)
        tvEmail = findViewById(R.id.tvEmail)
        mainScrollView = findViewById(R.id.mainScrollView)

        editFormLayout = findViewById(R.id.editFormLayout)
        etName = findViewById(R.id.etName)
        etEmail = findViewById(R.id.etEmail)
        etBirthDate = findViewById(R.id.etBirthDate)
        btnUpdate = findViewById(R.id.btnUpdate)
        ivCloseEdit = findViewById(R.id.ivCloseEdit)
        rowMyOrders = findViewById(R.id.rowMyOrders)
        editDataRow = findViewById(R.id.editDataRow)
        ivChevronEdit = findViewById(R.id.ivChevronEdit)

        bottomNav = findViewById(R.id.bottomNav)
    }

    private fun loadUserData() {
        val userName = SessionStore(this).userName
        val userEmail = SessionStore(this).userEmail

        textName.text = userName
        tvEmail.text = userEmail
        etName.setText(userName)
        etEmail.setText(userEmail)

        ApiClient.getProfile(userId) { success, userData ->
            runOnUiThread {
                if (success && userData != null) {
                    SessionStore(this).save(userData)
                    textName.text = userData.name
                    tvEmail.text = userData.email
                    etName.setText(userData.name)
                    etEmail.setText(userData.email)
                    etBirthDate.setText(userData.birthDate)
                    showToast("Профиль загружен")
                } else {
                    showToast("Ошибка загрузки профиля")
                }
            }
        }
    }

    private fun setupClickListeners() {
        editDataRow.setOnClickListener { toggleEditMode() }
        btnUpdate.setOnClickListener { updateUserData() }
        ivCloseEdit.setOnClickListener { slideFormOut() }
        rowMyOrders.setOnClickListener { openOrders() }
        findViewById<TextView>(R.id.btnLogout).setOnClickListener {
            ApiClient.logout()
            SessionStore(this).clear()
            startActivity(Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
        }
        setupBottomNavigation(bottomNav, R.id.nav_profile)
    }

    private fun openOrders() {
        val intent = Intent(this, OrdersActivity::class.java).apply {
        }
        startActivity(intent)
    }

    private fun toggleEditMode() {
        if (isEditMode) slideFormOut() else slideFormIn()
    }

    private fun slideFormIn() {
        editFormLayout.visibility = View.VISIBLE
        editFormLayout.alpha = 0f
        editFormLayout.translationY = -editFormLayout.height.toFloat()
        animateChevronBack()

        editFormLayout.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(400)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator) {
                    mainScrollView.isNestedScrollingEnabled = false
                }
                override fun onAnimationEnd(animation: Animator) {
                    isEditMode = true
                }
            })
    }

    private fun slideFormOut() {
        editFormLayout.animate()
            .translationY(-editFormLayout.height.toFloat())
            .alpha(0f)
            .setDuration(400)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    editFormLayout.visibility = View.GONE
                    editFormLayout.translationY = 0f
                    isEditMode = false
                    mainScrollView.isNestedScrollingEnabled = true
                    animateChevronForward()
                }
            })
    }

    private fun updateUserData() {
        val name = etName.text.toString().trim()
        val email = etEmail.text.toString().trim()
        val birthDate = etBirthDate.text.toString().trim()

        if (!validateEditData(name, email, birthDate)) return

        val updateRequest = UpdateProfileRequest(name, email, birthDate, "")

        ApiClient.updateProfile( userId, updateRequest) { success, message, userData ->
            runOnUiThread {
                if (success && userData != null) {
                    SessionStore(this).save(userData)
                    textName.text = userData.name
                    tvEmail.text = userData.email
                    slideFormOut()
                    showToast("Профиль обновлен!")
                } else {
                    showToast(message.ifEmpty { "Ошибка обновления" })
                }
            }
        }
    }

    private fun validateEditData(name: String, email: String, birthDate: String): Boolean {
        if (name.isEmpty()) {
            showToast("Имя не может быть пустым")
            return false
        }
        if (email.isEmpty() || !email.contains("@")) {
            showToast("Введите корректный email")
            return false
        }
        return true
    }

    private fun animateChevronForward() {
        ivChevronEdit.animate().rotation(0f).setDuration(200).start()
    }

    private fun animateChevronBack() {
        ivChevronEdit.animate().rotation(180f).setDuration(200).start()
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}

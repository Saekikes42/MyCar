package com.example.mycardemo.ui

import com.example.mycardemo.R
import com.example.mycardemo.data.SessionStore

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.mycardemo.api.ApiClient
import com.example.mycardemo.models.User
import com.example.mycardemo.models.UserData

class RegisterActivity : AppCompatActivity() {

    private lateinit var etName: EditText
    private lateinit var etEmail: EditText
    private lateinit var etBirthDate: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnRegister: Button
    private lateinit var tvGoToLogin: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        initializeViews()
        setupClickListeners()
    }

    private fun initializeViews() {
        etName = findViewById(R.id.etName)
        etEmail = findViewById(R.id.etEmail)
        etBirthDate = findViewById(R.id.etBirthDate)
        etPassword = findViewById(R.id.etPassword)
        btnRegister = findViewById(R.id.btnRegister)
        tvGoToLogin = findViewById(R.id.tvGoToLogin)
    }

    private fun setupClickListeners() {
        btnRegister.setOnClickListener { registerUser() }
        tvGoToLogin.setOnClickListener { navigateToLogin() }
    }

    private fun registerUser() {
        if (!validateRegistration()) return
        val name = etName.text.toString().trim()
        val email = etEmail.text.toString().trim()
        val birthDate = etBirthDate.text.toString().trim()
        val password = etPassword.text.toString()
        if (!validateInput(arrayOf(name, email, birthDate, password))) return
        val user = User(name, email, birthDate, password)
        ApiClient.register(user) { success, message, userData ->
            runOnUiThread {
                handleRegisterResponse(success, message, userData)
            }
        }
    }

    private fun handleRegisterResponse(success: Boolean, message: String, userData: UserData?) {
        if (success && userData != null) {
            onRegisterSuccess(userData)
        } else {
            showToast(message)
        }
    }

    private fun onRegisterSuccess(userData: UserData) {
        showToast("Регистрация завершена. Войдите в аккаунт")
        navigateToLogin(userData)
    }

    private fun navigateToLogin(userData: UserData? = null) {
        val intent = Intent(this, LoginActivity::class.java)
        userData?.let {
            intent.putExtra("USERNAME", it.name)
            intent.putExtra("USERID", it.id)
        }
        startActivity(intent)
        finish()
    }

    private fun validateInput(data: Array<String>): Boolean {
        if (data.any { it.isEmpty() }) {
            showToast("Заполните все поля")
            return false
        }
        return true
    }
    private fun validateRegistration(): Boolean {
        val name = etName.text.toString().trim()
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString()

        if (!isValidName(name)) { showToast("Имя: 2-20 букв, тире, пробелы"); return false }
        if (!isValidEmail(email)) { showToast("Email: 6-60 символов, user@domain.com"); return false }
        if (!isValidPassword(password, name, email)) { showToast("Пароль: 8-64, A-z, 0-9, спецсимволы"); return false }

        return true
    }

    private fun isValidName(name: String): Boolean =
        name.isNotEmpty() && name.length in 2..20 && Regex("^[A-Za-zА-Яа-яЁё\\-\\s]+$").matches(name)

    private fun isValidEmail(email: String): Boolean =
        email.length in 6..60 && Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$").matches(email)

    private fun isValidPassword(password: String, name: String, email: String): Boolean {
        if (password.length !in 8..64) return false
        val hasUpper = password.any { it.isUpperCase() }
        val hasLower = password.any { it.isLowerCase() }
        val hasDigit = password.any { it.isDigit() }
        val hasSpecial = password.any { "!@#$%^&*()_+-=[]{}|;:,.<>?".contains(it) }
        val lowerPass = password.lowercase()
        val hasName = name.lowercase() in lowerPass
        val hasEmail = email.lowercase() in lowerPass
        return hasUpper && hasLower && hasDigit && hasSpecial && !hasName && !hasEmail
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}

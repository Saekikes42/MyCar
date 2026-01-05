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
import com.example.mycardemo.models.LoginRequest
import com.example.mycardemo.models.UserData

class LoginActivity : AppCompatActivity() {

    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnLogin: Button
    private lateinit var tvGoToRegister: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        initializeViews()
        setupClickListeners()
    }

    private fun initializeViews() {
        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        btnLogin = findViewById(R.id.btnLogin)
        tvGoToRegister = findViewById(R.id.tvGoToRegister)
    }

    private fun setupClickListeners() {
        btnLogin.setOnClickListener { loginUser() }
        tvGoToRegister.setOnClickListener { navigateToRegister() }
    }

    private fun loginUser() {
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString()

        if (!validateInput(email, password)) return

        btnLogin.isEnabled = false
        btnLogin.text = "Загрузка..."

        val loginRequest = LoginRequest(email, password)

        ApiClient.login(loginRequest) { success, message, userData ->
            runOnUiThread {
                btnLogin.isEnabled = true
                btnLogin.text = "Вход"
                handleLoginResponse(success, message, userData)
            }
        }
    }

    private fun handleLoginResponse(success: Boolean, message: String, userData: UserData?) {
        if (success && userData != null) {
            onLoginSuccess(userData)
        } else {
            showToast(message.ifEmpty { "Ошибка входа" })
        }
    }

    private fun onLoginSuccess(userData: UserData) {
        showToast("Добро пожаловать, ${userData.name}!")
        saveUserData(userData)
        navigateToMain(userData)
    }

    private fun saveUserData(userData: UserData) {
        SessionStore(this).save(userData)
    }

    private fun navigateToMain(userData: UserData) {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("USERNAME", userData.name)
            putExtra("USERID", userData.id)
            putExtra("USEREMAIL", userData.email)
        }
        startActivity(intent)
        finish()
    }

    private fun navigateToRegister() {
        val intent = Intent(this, RegisterActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun validateInput(email: String, password: String): Boolean {
        if (email.isEmpty() || password.isEmpty()) {
            showToast("Заполните все поля")
            return false
        }

        return true
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}

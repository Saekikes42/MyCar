package com.example.mycardemo.data

import android.content.Context
import com.example.mycardemo.api.ApiClient
import com.example.mycardemo.models.UserData

/** Local session metadata. Passwords are never persisted on the device. */
class SessionStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("auth", Context.MODE_PRIVATE)
    val userId: Int get() = preferences.getInt("userId", -1)
    val userName: String get() = preferences.getString("userName", "").orEmpty()
    val userEmail: String get() = preferences.getString("userEmail", "").orEmpty()
    val token: String? get() = preferences.getString("token", null)

    fun save(user: UserData) {
        preferences.edit()
            .putInt("userId", user.id)
            .putString("userName", user.name)
            .putString("userEmail", user.email)
            .putString("token", ApiClient.token)
            .apply()
    }

    fun clear() {
        ApiClient.token = null
        preferences.edit().clear().apply()
    }
}

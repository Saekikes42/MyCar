package com.example.mycardemo

import android.app.Application
import com.example.mycardemo.api.ApiClient
import com.example.mycardemo.data.SessionStore

class MyCarApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ApiClient.token = SessionStore(this).token
    }
}

package com.example.mycardemo.ui

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import com.example.mycardemo.R
import com.google.android.material.bottomnavigation.BottomNavigationView

/** Each screen retains its selected tab when another activity opens. */
fun AppCompatActivity.setupBottomNavigation(navigation: BottomNavigationView, selectedItem: Int) {
    navigation.selectedItemId = selectedItem
    navigation.setOnItemSelectedListener { item ->
        if (item.itemId == selectedItem) {
            true
        } else {
            val target = when (item.itemId) {
                R.id.nav_home -> MainActivity::class.java
                R.id.nav_cart -> CartActivity::class.java
                R.id.nav_profile -> ProfileActivity::class.java
                else -> null
            }
            target?.let {
                startActivity(Intent(this, it).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
            }
            false
        }
    }
}

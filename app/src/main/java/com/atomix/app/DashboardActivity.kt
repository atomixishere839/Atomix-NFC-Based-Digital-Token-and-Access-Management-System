package com.atomix.app

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton

class DashboardActivity : AppCompatActivity() {

    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var fabScan: FloatingActionButton
    private lateinit var authManager: AuthManager
    private lateinit var themeManager: ThemeManager

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply theme before setContentView
        themeManager = ThemeManager(this)
        themeManager.applyTheme()
        
        super.onCreate(savedInstanceState)
        
        // Check authentication
        authManager = AuthManager(this)
        if (!authManager.isLoggedIn()) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }
        
        setContentView(R.layout.activity_dashboard)
        
        initializeViews()
        setupBottomNavigation()
        
        // Load default fragment
        if (savedInstanceState == null) {
            loadFragment(HomeFragment())
        }
    }

    private fun initializeViews() {
        bottomNavigation = findViewById(R.id.bottomNavigation)
        fabScan = findViewById(R.id.fabScan)
        
        fabScan.setOnClickListener {
            // Navigate to NFC scan (current MainActivity functionality)
            startActivity(Intent(this, MainActivity::class.java))
        }
    }

    private fun setupBottomNavigation() {
        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    loadFragment(HomeFragment())
                    true
                }
                R.id.nav_cards -> {
                    loadFragment(CardsFragment())
                    true
                }
                R.id.nav_settings -> {
                    loadFragment(SettingsFragment())
                    true
                }
                else -> false
            }
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }
}
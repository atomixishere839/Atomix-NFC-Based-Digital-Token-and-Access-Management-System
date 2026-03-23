package com.atomix.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class UserManagementActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_management)
        
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, UserManagementFragment())
                .commit()
        }
    }
}
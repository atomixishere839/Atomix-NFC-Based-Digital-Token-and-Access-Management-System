package com.atomix.app

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginActivity : AppCompatActivity() {

    private lateinit var usernameEditText: TextInputEditText
    private lateinit var passwordEditText: TextInputEditText
    private lateinit var loginButton: Button
    private lateinit var registerButton: Button
    private lateinit var authManager: AuthManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        authManager = AuthManager(this)

        // Check if already logged in
        if (authManager.isLoggedIn()) {
            navigateToMain()
            return
        }

        usernameEditText = findViewById(R.id.usernameEditText)
        passwordEditText = findViewById(R.id.passwordEditText)
        loginButton = findViewById(R.id.loginButton)
        registerButton = findViewById(R.id.registerButton)

        loginButton.setOnClickListener { attemptLogin() }
        registerButton.setOnClickListener { showRegisterDialog() }
    }

    private fun attemptLogin() {
        val username = usernameEditText.text.toString().trim()
        val password = passwordEditText.text.toString().trim()

        if (username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please enter username and password", Toast.LENGTH_SHORT).show()
            return
        }

        loginButton.isEnabled = false
        CoroutineScope(Dispatchers.Main).launch {
            val credentials = authManager.login(username, password)
            if (credentials != null) {
                Toast.makeText(this@LoginActivity, "Login successful as ${credentials.role.name}", Toast.LENGTH_SHORT).show()
                navigateToMain()
            } else {
                Toast.makeText(this@LoginActivity, "Invalid username or password", Toast.LENGTH_SHORT).show()
                loginButton.isEnabled = true
            }
        }
    }

    private fun showRegisterDialog() {
        val roles = arrayOf("USER", "ADMIN")
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Select Role")
            .setItems(roles) { _, which ->
                val role = if (which == 0) UserRole.USER else UserRole.ADMIN
                val intent = Intent(this, CardRegistrationActivity::class.java)
                intent.putExtra("role", role)
                startActivity(intent)
            }
            .show()
    }

    private fun navigateToMain() {
        val intent = Intent(this, DashboardActivity::class.java)
        startActivity(intent)
        finish()
    }
}

package com.atomix.app

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment

class SettingsFragment : Fragment() {

    private lateinit var authManager: AuthManager
    private lateinit var themeManager: ThemeManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        authManager = AuthManager(requireContext())
        themeManager = ThemeManager(requireContext())
        
        setupViews(view)
        loadSettings()
    }

    private fun setupViews(view: View) {
        val themeCard = view.findViewById<CardView>(R.id.themeCard)
        val transactionHistoryCard = view.findViewById<CardView>(R.id.transactionHistoryCard)
        val backupSyncCard = view.findViewById<CardView>(R.id.backupSyncCard)
        val logoutButton = view.findViewById<Button>(R.id.logoutButton)

        themeCard.setOnClickListener {
            showThemeDialog()
        }
        
        transactionHistoryCard.setOnClickListener {
            startActivity(Intent(requireContext(), TransactionHistoryActivity::class.java))
        }
        
        backupSyncCard.setOnClickListener {
            val currentUser = authManager.getCurrentUser()
            if (currentUser?.role == UserRole.ADMIN) {
                startActivity(Intent(requireContext(), BackupSyncActivity::class.java))
            } else {
                android.widget.Toast.makeText(requireContext(), "Admin access required", android.widget.Toast.LENGTH_SHORT).show()
            }
        }

        logoutButton.setOnClickListener {
            logout()
        }
    }

    private fun loadSettings() {
        val currentTheme = themeManager.getCurrentTheme()
        val themeName = themeManager.getThemeName(currentTheme)
        view?.findViewById<TextView>(R.id.currentThemeText)?.text = 
            "Current: $themeName"
    }

    private fun showThemeDialog() {
        val themes = arrayOf("Light", "Dark", "Auto")
        val currentTheme = themeManager.getCurrentTheme()

        AlertDialog.Builder(requireContext())
            .setTitle("Select Theme")
            .setSingleChoiceItems(themes, currentTheme) { dialog, which ->
                themeManager.setTheme(which)
                loadSettings()
                
                // Restart activity to apply theme
                requireActivity().recreate()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun logout() {
        authManager.logout()
        val intent = Intent(requireContext(), LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }
}
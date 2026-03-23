package com.atomix.app

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate

class ThemeManager(private val context: Context) {
    
    companion object {
        private const val PREFS_NAME = "AtomixThemePrefs"
        private const val KEY_THEME_MODE = "theme_mode"
        
        const val THEME_LIGHT = 0
        const val THEME_DARK = 1
        const val THEME_AUTO = 2
    }
    
    private val prefs: SharedPreferences = 
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    fun setTheme(themeMode: Int) {
        prefs.edit().putInt(KEY_THEME_MODE, themeMode).apply()
        applyTheme(themeMode)
    }
    
    fun getCurrentTheme(): Int {
        return prefs.getInt(KEY_THEME_MODE, THEME_AUTO)
    }
    
    fun applyTheme(themeMode: Int = getCurrentTheme()) {
        when (themeMode) {
            THEME_LIGHT -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            THEME_DARK -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            THEME_AUTO -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }
    
    fun getThemeName(themeMode: Int): String {
        return when (themeMode) {
            THEME_LIGHT -> "Light"
            THEME_DARK -> "Dark"
            THEME_AUTO -> "Auto"
            else -> "Auto"
        }
    }
}
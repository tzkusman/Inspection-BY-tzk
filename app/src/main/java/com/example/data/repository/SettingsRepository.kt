package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences

class SettingsRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("inspect_cam_settings", Context.MODE_PRIVATE)

    var defaultCompanyName: String
        get() = prefs.getString("default_company_name", "Global Apparel QC") ?: "Global Apparel QC"
        set(value) = prefs.edit().putString("default_company_name", value).apply()

    var defaultInspectorName: String
        get() = prefs.getString("default_inspector_name", "Senior Inspector") ?: "Senior Inspector"
        set(value) = prefs.edit().putString("default_inspector_name", value).apply()

    var imageQuality: String
        get() = prefs.getString("image_quality", "Medium") ?: "Medium"
        set(value) = prefs.edit().putString("image_quality", value).apply()

    fun getImageQualityConfig(): Pair<Int, Float> {
        return when (imageQuality) {
            "Low" -> Pair(1200, 0.7f)
            "High" -> Pair(2048, 0.9f)
            else -> Pair(1600, 0.8f) // Medium default
        }
    }
}

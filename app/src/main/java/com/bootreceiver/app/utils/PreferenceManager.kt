package com.bootreceiver.app.utils

import android.content.Context
import android.content.SharedPreferences

class PreferenceManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    fun saveTargetPackageName(packageName: String) { prefs.edit().putString(KEY_TARGET_PACKAGE, packageName).apply() }
    fun getTargetPackageName(): String? = prefs.getString(KEY_TARGET_PACKAGE, null)
    fun isConfigured(): Boolean = !getTargetPackageName().isNullOrEmpty()
    fun clearConfiguration() { prefs.edit().remove(KEY_TARGET_PACKAGE).apply() }
    fun hasSeenDeviceIdInfo(): Boolean = prefs.getBoolean(KEY_HAS_SEEN_DEVICE_ID_INFO, false)
    fun setHasSeenDeviceIdInfo(hasSeen: Boolean) { prefs.edit().putBoolean(KEY_HAS_SEEN_DEVICE_ID_INFO, hasSeen).apply() }
    fun isDeviceRegistered(): Boolean = prefs.getBoolean(KEY_DEVICE_REGISTERED, false)
    fun setDeviceRegistered(registered: Boolean) { prefs.edit().putBoolean(KEY_DEVICE_REGISTERED, registered).apply() }
    fun saveUnitName(unitName: String) { prefs.edit().putString(KEY_UNIT_NAME, unitName).apply() }
    fun getUnitName(): String? = prefs.getString(KEY_UNIT_NAME, null)
    
    companion object {
        private const val PREFS_NAME = "BootReceiverPrefs"
        private const val KEY_TARGET_PACKAGE = "target_package_name"
        private const val KEY_HAS_SEEN_DEVICE_ID_INFO = "has_seen_device_id_info"
        private const val KEY_DEVICE_REGISTERED = "device_registered"
        private const val KEY_UNIT_NAME = "unit_name"
    }
}

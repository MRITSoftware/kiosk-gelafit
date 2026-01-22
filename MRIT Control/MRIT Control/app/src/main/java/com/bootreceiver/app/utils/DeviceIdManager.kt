package com.bootreceiver.app.utils

import android.content.Context
import android.provider.Settings
import android.util.Log

object DeviceIdManager {
    fun getDeviceId(context: Context): String {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        Log.d(TAG, "Device ID obtido: $androidId")
        return androidId ?: "unknown_device"
    }
    private const val TAG = "DeviceIdManager"
}

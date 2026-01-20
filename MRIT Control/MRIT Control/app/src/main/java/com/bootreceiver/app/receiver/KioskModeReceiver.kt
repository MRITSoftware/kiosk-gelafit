package com.bootreceiver.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.bootreceiver.app.utils.AppLauncher
import com.bootreceiver.app.utils.DeviceIdManager
import com.bootreceiver.app.utils.PreferenceManager
import com.bootreceiver.app.utils.SupabaseManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver que intercepta eventos do sistema para prevenir minimização
 * quando o modo kiosk está ativo
 */
class KioskModeReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_CLOSE_SYSTEM_DIALOGS -> {
                // Intercepta quando o usuário tenta abrir menu do sistema
                Log.d(TAG, "🔒 Menu do sistema interceptado")
                
                // Verifica se kiosk está ativo e reabre app se necessário
                checkAndReopenApp(context)
            }
            "android.intent.action.USER_PRESENT" -> {
                // Quando o usuário desbloqueia a tela
                checkAndReopenApp(context)
            }
            Intent.ACTION_SCREEN_ON -> {
                // Quando a tela liga
                checkAndReopenApp(context)
            }
        }
    }
    
    private fun checkAndReopenApp(context: Context) {
        CoroutineScope(Dispatchers.IO + Job()).launch {
            try {
                val deviceId = DeviceIdManager.getDeviceId(context)
                val supabaseManager = SupabaseManager()
                val kioskMode = supabaseManager.getKioskMode(deviceId)
                
                if (kioskMode == true) {
                    val preferenceManager = PreferenceManager(context)
                    val targetPackage = preferenceManager.getTargetPackageName()
                    
                    if (!targetPackage.isNullOrEmpty()) {
                        Log.d(TAG, "🔒 Kiosk ativo - garantindo que app está em foreground")
                        val appLauncher = AppLauncher(context)
                        appLauncher.launchApp(targetPackage)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao verificar kiosk: ${e.message}", e)
            }
        }
    }
    
    companion object {
        private const val TAG = "KioskModeReceiver"
    }
}

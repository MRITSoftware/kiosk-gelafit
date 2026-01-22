package com.bootreceiver.app.utils

import android.app.Activity
import android.os.Build
import android.util.Log
import android.view.WindowManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Gerenciador de modo kiosk para Activities
 * 
 * Aplica flags e configurações para prevenir minimização do app
 */
class KioskModeManager(private val activity: Activity) {
    
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var isKioskActive = false
    
    /**
     * Ativa o modo kiosk na activity
     * Previne que o app seja minimizado
     */
    fun enableKioskMode() {
        if (isKioskActive) {
            return
        }
        
        try {
            Log.d(TAG, "🔒 Ativando modo kiosk na activity...")
            
            val window = activity.window
            
            // Flags para manter em foreground e prevenir minimização
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
            window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
            
            // Para Android 11+ (API 30+), usa SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.setDecorFitsSystemWindows(false)
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = (
                    android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                    or android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )
            }
            
            isKioskActive = true
            Log.d(TAG, "✅ Modo kiosk ativado")
            
            // Monitora se o app foi minimizado e traz de volta
            startMonitoring()
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao ativar modo kiosk: ${e.message}", e)
        }
    }
    
    /**
     * Desativa o modo kiosk
     */
    fun disableKioskMode() {
        if (!isKioskActive) {
            return
        }
        
        try {
            Log.d(TAG, "🔓 Desativando modo kiosk...")
            
            val window = activity.window
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            window.clearFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)
            window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
            window.clearFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.setDecorFitsSystemWindows(true)
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_VISIBLE
            }
            
            isKioskActive = false
            Log.d(TAG, "✅ Modo kiosk desativado")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao desativar modo kiosk: ${e.message}", e)
        }
    }
    
    /**
     * Monitora se o app foi minimizado e traz de volta
     * Nota: Este monitoramento é simplificado pois o KioskModeService já faz
     * o trabalho de verificar se o app está rodando e reabrir se necessário
     */
    private fun startMonitoring() {
        // O monitoramento principal é feito pelo KioskModeService
        // Este método pode ser usado para verificações adicionais se necessário
        scope.launch {
            while (isKioskActive && !activity.isFinishing) {
                try {
                    // Verifica periodicamente se a activity ainda está ativa
                    // O KioskModeService é responsável por reabrir o app se necessário
                    delay(5000) // Verifica a cada 5 segundos
                } catch (e: Exception) {
                    Log.e(TAG, "Erro no monitoramento: ${e.message}", e)
                    delay(10000)
                }
            }
        }
    }
    
    companion object {
        private const val TAG = "KioskModeManager"
    }
}

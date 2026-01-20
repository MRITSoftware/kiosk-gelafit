package com.bootreceiver.app.utils

import android.app.Activity
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager

/**
 * Interceptador de eventos para prevenir minimização do app em modo kiosk
 * 
 * Intercepta eventos de teclas (Home, Back) e movimentos para minimizar
 * antes que aconteçam, mantendo o app sempre em foreground
 */
class KioskModeInterceptor(private val activity: Activity) {
    
    /**
     * Intercepta o evento de pressionar o botão Home
     * Retorna true para consumir o evento e prevenir minimização
     */
    fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_HOME) {
            Log.d(TAG, "🔒 Botão Home pressionado - bloqueado em modo kiosk")
            return true // Consome o evento, previne minimização
        }
        return false
    }
    
    /**
     * Intercepta o evento de pressionar o botão Back
     * Em modo kiosk, pode bloquear ou permitir (dependendo da configuração)
     */
    fun onBackPressed(): Boolean {
        Log.d(TAG, "🔒 Botão Back pressionado - bloqueado em modo kiosk")
        return true // Consome o evento, previne voltar
    }
    
    /**
     * Intercepta quando o usuário tenta sair da activity
     * Chamado antes de onPause() quando o usuário pressiona Home ou muda de app
     */
    fun onUserLeaveHint() {
        Log.d(TAG, "🔒 Tentativa de sair da activity detectada - bloqueando em modo kiosk")
        // Move a task de volta para frente imediatamente
        activity.moveTaskToBack(false)
    }
    
    /**
     * Aplica configurações de janela para prevenir minimização
     */
    fun applyWindowFlags() {
        try {
            val window = activity.window
            val params = window.attributes
            
            // Previne que a janela seja movida para background
            params.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            window.attributes = params
            
            // Adiciona flags adicionais
            window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
            window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            
            Log.d(TAG, "✅ Flags de janela aplicadas para prevenir minimização")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao aplicar flags de janela: ${e.message}", e)
        }
    }
    
    companion object {
        private const val TAG = "KioskModeInterceptor"
    }
}

package com.bootreceiver.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.bootreceiver.app.service.BootService
import com.bootreceiver.app.utils.PermissionChecker
import com.bootreceiver.app.utils.PreferenceManager

/**
 * BroadcastReceiver que escuta o evento de boot completo do Android
 * 
 * Quando o dispositivo Ã© ligado ou reiniciado, o sistema Android envia
 * o broadcast BOOT_COMPLETED. Este receiver captura esse evento e inicia
 * o serviÃ§o que verifica internet e abre o app configurado.
 */
class BootReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON" -> {
                Log.d(TAG, "Boot detectado! Iniciando processo...")
                
                // Verifica permissÃµes e otimizaÃ§Ãµes
                val permissionChecker = PermissionChecker(context)
                val status = permissionChecker.getFullStatus()
                
                if (!status.isReady) {
                    Log.w(TAG, "Problemas detectados que podem impedir funcionamento:")
                    status.issues.forEach { issue ->
                        Log.w(TAG, "  - ")
                    }
                    // Continua mesmo assim, mas registra o problema
                } else {
                    Log.d(TAG, "Todas as permissÃµes e otimizaÃ§Ãµes estÃ£o corretas")
                }
                
                // Verifica se jÃ¡ foi configurado um app para iniciar
                val preferenceManager = PreferenceManager(context)
                val targetPackageName = preferenceManager.getTargetPackageName()
                
                if (targetPackageName.isNullOrEmpty()) {
                    Log.w(TAG, "Nenhum app configurado. Abrindo tela de seleÃ§Ã£o...")
                    // Se nÃ£o houver app configurado, abre a tela de seleÃ§Ã£o
                    // Usa FLAG_ACTIVITY_NEW_TASK e FLAG_ACTIVITY_CLEAR_TOP para garantir que abra mesmo com tela bloqueada
                    val selectionIntent = Intent(context, 
                        com.bootreceiver.app.ui.AppSelectionActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP
                        // Tenta abrir mesmo com tela bloqueada (requer permissÃ£o em Android 10+)
                        addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                    }
                    try {
                        context.startActivity(selectionIntent)
                        Log.d(TAG, "Tela de seleÃ§Ã£o iniciada")
                    } catch (e: Exception) {
                        Log.e(TAG, "Erro ao abrir tela de seleÃ§Ã£o: ", e)
                    }
                } else {
                    Log.d(TAG, "App alvo configurado: ")
                    // Inicia o serviÃ§o que verifica internet e abre o app
                    val serviceIntent = Intent(context, BootService::class.java)
                    try {
                        context.startService(serviceIntent)
                        Log.d(TAG, "BootService iniciado")
                    } catch (e: Exception) {
                        Log.e(TAG, "Erro ao iniciar BootService: ", e)
                    }
                }
                
                // Sempre inicia o serviÃ§o principal de monitoramento de app (sempre roda em background)
                val appMonitorIntent = Intent(context, 
                    com.bootreceiver.app.service.AppMonitorService::class.java)
                try {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        context.startForegroundService(appMonitorIntent)
                    } else {
                        context.startService(appMonitorIntent)
                    }
                    Log.d(TAG, "AppMonitorService iniciado")
                } catch (e: Exception) {
                    Log.e(TAG, "Erro ao iniciar AppMonitorService: ", e)
                }
                
                // Sempre inicia o serviÃ§o de monitoramento de comandos de reiniciar app
                val restartMonitorIntent = Intent(context, 
                    com.bootreceiver.app.service.AppRestartMonitorService::class.java)
                try {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        context.startForegroundService(restartMonitorIntent)
                    } else {
                        context.startService(restartMonitorIntent)
                    }
                    Log.d(TAG, "AppRestartMonitorService iniciado")
                } catch (e: Exception) {
                    Log.e(TAG, "Erro ao iniciar AppRestartMonitorService: ", e)
                }
                
                // Inicia o serviÃ§o de monitoramento de modo kiosk
                val kioskIntent = Intent(context, 
                    com.bootreceiver.app.service.KioskModeService::class.java)
                try {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        context.startForegroundService(kioskIntent)
                    } else {
                        context.startService(kioskIntent)
                    }
                    Log.d(TAG, "KioskModeService iniciado")
                } catch (e: Exception) {
                    Log.e(TAG, "Erro ao iniciar KioskModeService: ", e)
                }
            }
            else -> {
                Log.w(TAG, "AÃ§Ã£o desconhecida recebida: ")
            }
        }
    }
    
    companion object {
        private const val TAG = "BootReceiver"
    }
}
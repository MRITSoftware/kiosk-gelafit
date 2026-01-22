package com.bootreceiver.app

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import com.bootreceiver.app.utils.DeviceIdManager
import com.bootreceiver.app.utils.PreferenceManager
import com.bootreceiver.app.utils.SupabaseManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Application class para inicializaÃ§Ã£o global do app
 * Ãštil para configuraÃ§Ãµes que precisam ser feitas antes de qualquer Activity
 * 
 * IMPORTANTE: Esta classe garante que o BroadcastReceiver seja registrado
 * quando o app Ã© aberto pela primeira vez.
 */
class BootReceiverApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "BootReceiverApplication iniciado")
        
        // ForÃ§a o registro do receiver ao abrir o app
        // Isso garante que o receiver esteja ativo mesmo em Android 10+
        try {
            val pm = packageManager
            val componentName = android.content.ComponentName(
                this,
                "com.bootreceiver.app.receiver.BootReceiver"
            )
            
            // Verifica se o receiver estÃ¡ habilitado
            val state = pm.getComponentEnabledSetting(componentName)
            if (state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED ||
                state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER) {
                Log.w(TAG, "Receiver estava desabilitado. Habilitando...")
                pm.setComponentEnabledSetting(
                    componentName,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
            
            Log.d(TAG, "Receiver verificado e habilitado")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao verificar receiver: ", e)
        }
        
        // Atualiza registro do dispositivo no Supabase (atualiza last_seen)
        updateDeviceRegistration()
        
        // Inicia o serviÃ§o principal de monitoramento de app (sempre roda em background)
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            try {
                val appMonitorIntent = Intent(this, com.bootreceiver.app.service.AppMonitorService::class.java)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    startForegroundService(appMonitorIntent)
                } else {
                    startService(appMonitorIntent)
                }
                Log.d(TAG, "AppMonitorService iniciado")
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao iniciar AppMonitorService: ", e)
            }
        }, 500) // Delay de 500ms para iniciar rapidamente
        
        // Inicia o serviÃ§o de monitoramento de comandos de reiniciar app
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            try {
                val monitorIntent = Intent(this, com.bootreceiver.app.service.AppRestartMonitorService::class.java)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    startForegroundService(monitorIntent)
                } else {
                    startService(monitorIntent)
                }
                Log.d(TAG, "AppRestartMonitorService iniciado")
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao iniciar AppRestartMonitorService: ", e)
            }
        }, 1000) // Delay de 1 segundo para garantir inicializaÃ§Ã£o completa
        
        // Inicia o serviÃ§o de monitoramento de modo kiosk
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            try {
                val kioskIntent = Intent(this, com.bootreceiver.app.service.KioskModeService::class.java)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    startForegroundService(kioskIntent)
                } else {
                    startService(kioskIntent)
                }
                Log.d(TAG, "KioskModeService iniciado")
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao iniciar KioskModeService: ", e)
            }
        }, 2000) // Delay de 2 segundos
    }
    
    /**
     * Atualiza o registro do dispositivo no Supabase (atualiza last_seen)
     * Isso Ã© feito sempre que o app inicia para manter o dispositivo sincronizado
     */
    private fun updateDeviceRegistration() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val preferenceManager = PreferenceManager(this@BootReceiverApplication)
                
                // SÃ³ atualiza se o dispositivo jÃ¡ foi registrado (tem email)
                if (preferenceManager.isDeviceRegistered()) {
                    val deviceId = DeviceIdManager.getDeviceId(this@BootReceiverApplication)
                    val unitName = preferenceManager.getUnitName()
                    val supabaseManager = SupabaseManager()
                    
                    val success = supabaseManager.registerDevice(deviceId, unitName)
                    if (success) {
                        Log.d(TAG, "Registro do dispositivo atualizado no Supabase")
                    } else {
                        Log.w(TAG, "Falha ao atualizar registro do dispositivo")
                    }
                } else {
                    Log.d(TAG, "Dispositivo ainda nÃ£o foi registrado. SerÃ¡ registrado na primeira abertura do app.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao atualizar registro do dispositivo: ", e)
            }
        }
    }
    
    companion object {
        private const val TAG = "BootReceiverApp"
    }
}
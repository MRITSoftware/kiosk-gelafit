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
 * Application class para inicialização global do app
 * Útil para configurações que precisam ser feitas antes de qualquer Activity
 * 
 * IMPORTANTE: Esta classe garante que o BroadcastReceiver seja registrado
 * quando o app é aberto pela primeira vez.
 */
class BootReceiverApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "BootReceiverApplication iniciado")
        
        try {
            // Força o registro do receiver ao abrir o app
            // Isso garante que o receiver esteja ativo mesmo em Android 10+
            try {
                val pm = packageManager
                val componentName = android.content.ComponentName(
                    this,
                    "com.bootreceiver.app.receiver.BootReceiver"
                )
                
                // Verifica se o receiver está habilitado
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
                Log.e(TAG, "Erro ao verificar receiver: ${e.message}", e)
            }
            
            // Atualiza registro do dispositivo no Supabase (atualiza last_seen)
            // Faz em background para não bloquear a inicialização
            updateDeviceRegistration()
            
            // Inicia os serviços em background com delay para não bloquear a inicialização
            // Inicia o serviço de monitoramento de app (monitora quando app escolhido é aberto)
            // Usa try-catch adicional para garantir que não cause crash se a classe não existir
            try {
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    try {
                        val appMonitorIntent = Intent(this, com.bootreceiver.app.service.AppMonitorService::class.java)
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            startForegroundService(appMonitorIntent)
                        } else {
                            startService(appMonitorIntent)
                        }
                        Log.d(TAG, "AppMonitorService iniciado")
                    } catch (e: ClassNotFoundException) {
                        Log.e(TAG, "AppMonitorService não encontrado: ${e.message}", e)
                    } catch (e: Exception) {
                        Log.e(TAG, "Erro ao iniciar AppMonitorService: ${e.message}", e)
                    }
                }, 1000) // Delay de 1 segundo
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao agendar AppMonitorService: ${e.message}", e)
            }
            
            // Inicia o serviço de monitoramento de comandos de reiniciar app
            try {
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    try {
                        val monitorIntent = Intent(this, com.bootreceiver.app.service.AppRestartMonitorService::class.java)
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            startForegroundService(monitorIntent)
                        } else {
                            startService(monitorIntent)
                        }
                        Log.d(TAG, "AppRestartMonitorService iniciado")
                    } catch (e: ClassNotFoundException) {
                        Log.e(TAG, "AppRestartMonitorService não encontrado: ${e.message}", e)
                    } catch (e: Exception) {
                        Log.e(TAG, "Erro ao iniciar AppRestartMonitorService: ${e.message}", e)
                    }
                }, 2000) // Delay de 2 segundos para garantir inicialização completa
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao agendar AppRestartMonitorService: ${e.message}", e)
            }
            
            // Inicia o serviço de monitoramento de modo kiosk
            try {
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    try {
                        val kioskIntent = Intent(this, com.bootreceiver.app.service.KioskModeService::class.java)
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            startForegroundService(kioskIntent)
                        } else {
                            startService(kioskIntent)
                        }
                        Log.d(TAG, "KioskModeService iniciado")
                    } catch (e: ClassNotFoundException) {
                        Log.e(TAG, "KioskModeService não encontrado: ${e.message}", e)
                    } catch (e: Exception) {
                        Log.e(TAG, "Erro ao iniciar KioskModeService: ${e.message}", e)
                    }
                }, 3000) // Delay de 3 segundos
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao agendar KioskModeService: ${e.message}", e)
            }
            
            // Inicia o WorkManager para verificar modo kiosk mesmo quando app está fechado
            try {
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    try {
                        com.bootreceiver.app.worker.KioskModeWorker.start(this)
                        Log.d(TAG, "KioskModeWorker iniciado")
                    } catch (e: ClassNotFoundException) {
                        Log.e(TAG, "KioskModeWorker não encontrado: ${e.message}", e)
                    } catch (e: Exception) {
                        Log.e(TAG, "Erro ao iniciar KioskModeWorker: ${e.message}", e)
                    }
                }, 4000) // Delay de 4 segundos
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao agendar KioskModeWorker: ${e.message}", e)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro crítico no onCreate: ${e.message}", e)
            // Não relança a exceção para evitar crash do app
        }
    }
    
    /**
     * Atualiza o registro do dispositivo no Supabase (atualiza last_seen)
     * Isso é feito sempre que o app inicia para manter o dispositivo sincronizado
     */
    private fun updateDeviceRegistration() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val preferenceManager = PreferenceManager(this@BootReceiverApplication)
                
                // Só atualiza se o dispositivo já foi registrado (tem unit_name)
                if (preferenceManager.isDeviceRegistered()) {
                    val deviceId = DeviceIdManager.getDeviceId(this@BootReceiverApplication)
                    val unitName = preferenceManager.getUnitName()
                    if (!unitName.isNullOrBlank()) {
                        val supabaseManager = SupabaseManager()
                        val success = supabaseManager.registerDevice(deviceId, unitName)
                        if (success) {
                            Log.d(TAG, "Registro do dispositivo atualizado no Supabase")
                        } else {
                            Log.w(TAG, "Falha ao atualizar registro do dispositivo")
                        }
                    } else {
                        Log.w(TAG, "Unit name não configurado")
                    }
                } else {
                    Log.d(TAG, "Dispositivo ainda não foi registrado. Será registrado na primeira abertura do app.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao atualizar registro do dispositivo: ${e.message}", e)
            }
        }
    }
    
    companion object {
        private const val TAG = "BootReceiverApp"
    }
}

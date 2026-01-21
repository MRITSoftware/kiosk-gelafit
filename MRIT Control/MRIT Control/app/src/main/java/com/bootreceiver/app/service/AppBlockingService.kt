package com.bootreceiver.app.service

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.bootreceiver.app.R
import com.bootreceiver.app.ui.GelaFitWorkspaceActivity
import com.bootreceiver.app.utils.AppLauncher
import com.bootreceiver.app.utils.DeviceIdManager
import com.bootreceiver.app.utils.PreferenceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * ServiÃ§o que monitora e bloqueia acesso a outros apps quando is_active = true
 * 
 * Este serviÃ§o:
 * 1. Monitora constantemente qual app estÃ¡ em foreground
 * 2. Se is_active = true e outro app (nÃ£o autorizado) estiver aberto, fecha e reabre o app configurado
 * 3. Previne que outros apps sejam abertos quando is_active = true
 */
class AppBlockingService : Service() {
    
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var isRunning = false
    private var isActive = false
    private lateinit var deviceId: String
    private lateinit var preferenceManager: PreferenceManager
    private val allowedPackages = setOf(
        "com.bootreceiver.app",  // PrÃ³prio GelaFit Control
        "com.android.settings",  // ConfiguraÃ§Ãµes (pode ser necessÃ¡rio)
        "com.android.systemui"    // System UI (necessÃ¡rio para funcionamento do sistema)
    )
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "AppBlockingService criado")
        deviceId = DeviceIdManager.getDeviceId(this)
        preferenceManager = PreferenceManager(this)
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val newIsActive = intent?.getBooleanExtra("is_active", false) ?: false
        
        if (isActive != newIsActive) {
            isActive = newIsActive
            Log.d(TAG, "â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”")
            if (isActive) {
                Log.d(TAG, "ðŸ”’ BLOQUEIO DE APPS ATIVADO")
                Log.d(TAG, "â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”")
            } else {
                Log.d(TAG, "ðŸ”“ BLOQUEIO DE APPS DESATIVADO")
                Log.d(TAG, "â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”")
            }
        }
        
        if (!isRunning && isActive) {
            try {
                isRunning = true
                Log.d(TAG, "AppBlockingService iniciado")
                
                createNotificationChannel()
                
                // Inicia como Foreground Service
                try {
                    val notification = createNotification()
                    startForeground(NOTIFICATION_ID, notification)
                    Log.d(TAG, "Foreground Service iniciado com sucesso")
                } catch (e: Exception) {
                    Log.e(TAG, "Erro ao iniciar Foreground Service: ${e.message}", e)
                }
                
                // Inicia o monitoramento
                serviceScope.launch {
                    startMonitoring()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro crÃ­tico ao iniciar serviÃ§o: ${e.message}", e)
                isRunning = false
            }
        } else if (!isActive) {
            // Se is_active foi desativado, para o serviÃ§o
            Log.d(TAG, "is_active desativado - parando serviÃ§o")
            stopSelf()
        }
        
        return START_STICKY
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Bloqueio de Apps",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitora e bloqueia acesso a outros apps"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        val intent = Intent(this, GelaFitWorkspaceActivity::class.java)
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            pendingIntentFlags
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GelaFit Control - Bloqueio Ativo")
            .setContentText("Acesso a outros apps bloqueado")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setAutoCancel(false)
            .build()
    }
    
    /**
     * Inicia monitoramento constante de apps em foreground
     */
    private suspend fun startMonitoring() {
        while (isRunning && isActive) {
            try {
                val targetPackage = preferenceManager.getTargetPackageName()
                
                if (targetPackage.isNullOrEmpty()) {
                    Log.w(TAG, "Nenhum app configurado. Aguardando...")
                    delay(CHECK_INTERVAL_MS)
                    continue
                }
                
                // Verifica qual app estÃ¡ em foreground
                val foregroundPackage = getForegroundPackage()
                
                if (foregroundPackage != null) {
                    // Se nÃ£o Ã© o app configurado nem um app permitido, bloqueia
                    if (foregroundPackage != targetPackage && !allowedPackages.contains(foregroundPackage)) {
                        Log.w(TAG, "â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”")
                        Log.w(TAG, "ðŸš« APP NÃƒO AUTORIZADO DETECTADO: $foregroundPackage")
                        Log.w(TAG, "ðŸ”„ Fechando e reabrindo app configurado...")
                        Log.w(TAG, "â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”")
                        
                        // Fecha o app nÃ£o autorizado
                        closeApp(foregroundPackage)
                        
                        // Aguarda um pouco e reabre o app configurado
                        delay(500)
                        openConfiguredApp(targetPackage)
                    }
                }
                
                delay(CHECK_INTERVAL_MS)
            } catch (e: Exception) {
                Log.e(TAG, "Erro no monitoramento: ${e.message}", e)
                delay(ERROR_RETRY_DELAY_MS)
            }
        }
        
        Log.d(TAG, "Monitoramento parado")
        isRunning = false
    }
    
    /**
     * ObtÃ©m o package name do app que estÃ¡ em foreground
     */
    private fun getForegroundPackage(): String? {
        try {
            val activityManager = getSystemService(ActivityManager::class.java)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val runningTasks = activityManager.getAppTasks()
                if (runningTasks != null && runningTasks.isNotEmpty()) {
                    val topTask = runningTasks[0]
                    val taskInfo = topTask.taskInfo
                    if (taskInfo != null && taskInfo.topActivity != null) {
                        return taskInfo.topActivity!!.packageName
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                val runningTasks = activityManager.getRunningTasks(1)
                if (runningTasks.isNotEmpty()) {
                    val topActivity = runningTasks[0].topActivity
                    if (topActivity != null) {
                        return topActivity.packageName
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao obter app em foreground: ${e.message}", e)
        }
        
        return null
    }
    
    /**
     * Fecha um app especÃ­fico
     */
    private fun closeApp(packageName: String) {
        try {
            val activityManager = getSystemService(ActivityManager::class.java)
            
            // Tenta fechar usando killBackgroundProcesses
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                activityManager.killBackgroundProcesses(packageName)
            }
            
            // Tenta fechar usando am force-stop (requer permissÃ£o)
            try {
                val process = Runtime.getRuntime().exec("am force-stop $packageName")
                process.waitFor()
                Log.d(TAG, "âœ… App $packageName fechado")
            } catch (e: Exception) {
                Log.w(TAG, "NÃ£o foi possÃ­vel fechar app usando force-stop: ${e.message}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao fechar app: ${e.message}", e)
        }
    }
    
    /**
     * Abre o app configurado
     */
    private fun openConfiguredApp(packageName: String) {
        try {
            val appLauncher = AppLauncher(this)
            val success = appLauncher.launchApp(packageName)
            
            if (success) {
                Log.d(TAG, "âœ… App configurado reaberto: $packageName")
            } else {
                Log.e(TAG, "âŒ Falha ao reabrir app configurado")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao abrir app configurado: ${e.message}", e)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "âš ï¸ AppBlockingService destruÃ­do")
        isRunning = false
    }
    
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d(TAG, "âš ï¸ App removido da lista de tarefas - mas serviÃ§o continua rodando")
        
        // Reinicia o serviÃ§o se is_active ainda estiver ativo
        if (isActive) {
            val restartIntent = Intent(this, AppBlockingService::class.java).apply {
                putExtra("is_active", true)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(restartIntent)
            } else {
                startService(restartIntent)
            }
        }
    }
    
    companion object {
        private const val TAG = "AppBlockingService"
        private const val CHANNEL_ID = "app_blocking_channel"
        private const val NOTIFICATION_ID = 3
        private const val CHECK_INTERVAL_MS = 1000L // Verifica a cada 1 segundo (muito rÃ¡pido)
        private const val ERROR_RETRY_DELAY_MS = 5000L // Em caso de erro, aguarda 5 segundos
    }
}
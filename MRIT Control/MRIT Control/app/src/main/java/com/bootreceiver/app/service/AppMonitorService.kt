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
import com.bootreceiver.app.ui.AppSelectionActivity
import com.bootreceiver.app.utils.PreferenceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Serviço que monitora quando o app escolhido é aberto e garante que
 * o GelaFit Control esteja sempre rodando em background para receber comandos
 * 
 * Este serviço:
 * 1. Monitora periodicamente qual app está em foreground
 * 2. Quando detecta que o app escolhido está aberto, garante que o GelaFit Control esteja rodando
 * 3. Inicia automaticamente o AppRestartMonitorService se não estiver rodando
 * 4. Funciona em background mesmo quando o app está fechado
 */
class AppMonitorService : Service() {
    
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var isRunning = false
    private lateinit var preferenceManager: PreferenceManager
    private var targetPackageName: String? = null
    private var lastForegroundApp: String? = null
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "AppMonitorService criado")
        preferenceManager = PreferenceManager(this)
        targetPackageName = preferenceManager.getTargetPackageName()
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (isRunning) {
            Log.d(TAG, "Serviço já está rodando")
            return START_STICKY
        }
        
        try {
            isRunning = true
            targetPackageName = preferenceManager.getTargetPackageName()
            Log.d(TAG, "AppMonitorService iniciado. Monitorando app: $targetPackageName")
            
            // Garante que o canal de notificação existe
            createNotificationChannel()
            
            // Inicia como Foreground Service
            try {
                val notification = createNotification()
                startForeground(NOTIFICATION_ID, notification)
                Log.d(TAG, "Foreground Service iniciado com sucesso")
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao iniciar Foreground Service: ${e.message}", e)
            }
            
            // Garante que o AppRestartMonitorService está rodando
            ensureAppRestartMonitorServiceRunning()
            
            // Inicia o monitoramento em uma coroutine
            serviceScope.launch {
                startMonitoring()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro crítico ao iniciar serviço: ${e.message}", e)
            isRunning = false
        }
        
        return START_STICKY
    }
    
    /**
     * Cria o canal de notificação (necessário para Android 8.0+)
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Monitoramento de App",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitora quando o app escolhido é aberto"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * Cria a notificação para o Foreground Service
     */
    private fun createNotification(): Notification {
        val intent = Intent(this, AppSelectionActivity::class.java)
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
        
        val smallIcon = android.R.drawable.ic_dialog_info
        
        val targetApp = targetPackageName ?: "Nenhum"
        val statusText = if (targetPackageName != null) {
            "Monitorando: $targetApp"
        } else {
            "Aguardando configuração..."
        }
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GelaFit Control - Monitorando")
            .setContentText(statusText)
            .setSmallIcon(smallIcon)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setAutoCancel(false)
            .build()
    }
    
    /**
     * Garante que o AppRestartMonitorService está rodando
     */
    private fun ensureAppRestartMonitorServiceRunning() {
        try {
            if (!isServiceRunning(AppRestartMonitorService::class.java)) {
                Log.d(TAG, "AppRestartMonitorService não está rodando. Iniciando...")
                val monitorIntent = Intent(this, AppRestartMonitorService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(monitorIntent)
                } else {
                    startService(monitorIntent)
                }
                Log.d(TAG, "AppRestartMonitorService iniciado")
            } else {
                Log.d(TAG, "AppRestartMonitorService já está rodando")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao iniciar AppRestartMonitorService: ${e.message}", e)
        }
    }
    
    /**
     * Verifica se um serviço está rodando
     */
    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        try {
            val activityManager = getSystemService(ActivityManager::class.java)
            val runningServices = activityManager.getRunningServices(Integer.MAX_VALUE)
            
            for (service in runningServices) {
                if (serviceClass.name == service.service.className) {
                    return true
                }
            }
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao verificar se serviço está rodando: ${e.message}", e)
            return false
        }
    }
    
    /**
     * Inicia o monitoramento periódico do app em foreground
     */
    private suspend fun startMonitoring() {
        while (isRunning) {
            try {
                // Atualiza o targetPackageName caso tenha mudado
                targetPackageName = preferenceManager.getTargetPackageName()
                
                if (targetPackageName.isNullOrEmpty()) {
                    Log.d(TAG, "Nenhum app configurado. Aguardando...")
                    delay(CHECK_INTERVAL_MS)
                    continue
                }
                
                // Verifica qual app está em foreground
                val foregroundApp = getForegroundApp()
                
                if (foregroundApp != null) {
                    // Se o app escolhido está em foreground
                    if (foregroundApp == targetPackageName) {
                        if (lastForegroundApp != foregroundApp) {
                            Log.d(TAG, "✅ App escolhido detectado em foreground: $foregroundApp")
                            lastForegroundApp = foregroundApp
                            
                            // Garante que o AppRestartMonitorService está rodando
                            ensureAppRestartMonitorServiceRunning()
                            
                            // Garante que o GelaFit Control está rodando em background
                            // (já está rodando porque este serviço está ativo)
                            Log.d(TAG, "GelaFit Control está conectado e monitorando comandos")
                        }
                    } else {
                        // Outro app está em foreground
                        if (lastForegroundApp == targetPackageName) {
                            Log.d(TAG, "App escolhido saiu de foreground. App atual: $foregroundApp")
                            lastForegroundApp = foregroundApp
                        }
                    }
                }
                
                // Sempre garante que o AppRestartMonitorService está rodando
                // mesmo quando o app escolhido não está em foreground
                ensureAppRestartMonitorServiceRunning()
                
                // Aguarda antes da próxima verificação
                delay(CHECK_INTERVAL_MS)
                
            } catch (e: Exception) {
                Log.e(TAG, "Erro no monitoramento: ${e.message}", e)
                delay(ERROR_RETRY_DELAY_MS)
            }
        }
    }
    
    /**
     * Obtém o package name do app que está em foreground
     * Usa múltiplos métodos para garantir compatibilidade
     */
    private fun getForegroundApp(): String? {
        try {
            val activityManager = getSystemService(ActivityManager::class.java)
            
            // Método 1: Verifica processos em foreground (mais confiável)
            val runningProcesses = activityManager.runningAppProcesses
            if (runningProcesses != null) {
                // Ordena por importância - pega o mais importante primeiro
                val foregroundProcesses = runningProcesses
                    .filter { 
                        it.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND ||
                        it.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE
                    }
                    .sortedByDescending { it.importance }
                
                if (foregroundProcesses.isNotEmpty()) {
                    // Pega o primeiro pacote do processo mais importante
                    val topProcess = foregroundProcesses[0]
                    val packageName = topProcess.pkgList?.getOrNull(0)
                    if (packageName != null) {
                        // Evita retornar o próprio pacote do GelaFit Control
                        if (packageName != "com.bootreceiver.app") {
                            return packageName
                        }
                    }
                }
            }
            
            // Método 2: Android 6.0+ - usa getAppTasks
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val appTasks = activityManager.appTasks
                if (appTasks != null && appTasks.isNotEmpty()) {
                    for (task in appTasks) {
                        val taskInfo = task.taskInfo
                        if (taskInfo != null && taskInfo.topActivity != null) {
                            val packageName = taskInfo.topActivity!!.packageName
                            // Evita retornar o próprio pacote
                            if (packageName != "com.bootreceiver.app") {
                                return packageName
                            }
                        }
                    }
                }
            } else {
                // Método 3: Android 5.1 e abaixo - usa getRunningTasks (deprecated mas funciona)
                @Suppress("DEPRECATION")
                val runningTasks = activityManager.getRunningTasks(1)
                if (runningTasks.isNotEmpty()) {
                    val topActivity = runningTasks[0].topActivity
                    if (topActivity != null) {
                        val packageName = topActivity.packageName
                        // Evita retornar o próprio pacote
                        if (packageName != "com.bootreceiver.app") {
                            return packageName
                        }
                    }
                }
            }
            
            return null
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao obter app em foreground: ${e.message}", e)
            return null
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        Log.d(TAG, "AppMonitorService destruído - tentando reiniciar...")
        
        // Se o serviço foi destruído, tenta reiniciar
        // Isso garante que o serviço continue funcionando mesmo se for morto pelo sistema
        serviceScope.launch {
            try {
                delay(2000) // Aguarda 2 segundos
                Log.d(TAG, "🔄 Reiniciando AppMonitorService em background...")
                val restartIntent = Intent(this@AppMonitorService, AppMonitorService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(restartIntent)
                } else {
                    startService(restartIntent)
                }
                
                // Também agenda um reinício usando AlarmManager como backup
                scheduleRestart()
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao tentar reiniciar serviço: ${e.message}", e)
                scheduleRestart()
            }
        }
    }
    
    /**
     * Agenda um reinício do serviço usando AlarmManager
     * Isso garante que o serviço seja reiniciado mesmo se o app estiver completamente fechado
     */
    private fun scheduleRestart() {
        try {
            val alarmManager = getSystemService(android.content.Context.ALARM_SERVICE) as android.app.AlarmManager
            val restartIntent = Intent(this, AppMonitorService::class.java)
            val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pendingIntent = PendingIntent.getService(
                this,
                0,
                restartIntent,
                pendingIntentFlags
            )
            
            // Agenda reinício em 5 segundos
            val triggerTime = System.currentTimeMillis() + 5000
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    android.app.AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            } else {
                @Suppress("DEPRECATION")
                alarmManager.setExact(android.app.AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            }
            
            Log.d(TAG, "⏰ Reinício do serviço agendado em 5 segundos")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao agendar reinício: ${e.message}", e)
        }
    }
    
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d(TAG, "⚠️ App removido da lista de tarefas - mas serviço continua rodando")
        // O serviço continua rodando mesmo se o app for fechado
        // START_STICKY garante que será reiniciado se necessário
    }
    
    companion object {
        private const val TAG = "AppMonitorService"
        private const val CHANNEL_ID = "app_monitor_channel"
        private const val NOTIFICATION_ID = 2
        private const val CHECK_INTERVAL_MS = 5000L // Verifica a cada 5 segundos
        private const val ERROR_RETRY_DELAY_MS = 10000L // Em caso de erro, aguarda 10 segundos
    }
}

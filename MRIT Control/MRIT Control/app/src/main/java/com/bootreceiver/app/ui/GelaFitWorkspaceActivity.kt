package com.bootreceiver.app.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.bootreceiver.app.R
import com.bootreceiver.app.utils.AppLauncher
import com.bootreceiver.app.utils.DeviceIdManager
import com.bootreceiver.app.utils.PreferenceManager
import com.bootreceiver.app.utils.SupabaseManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Activity principal que serve como "Ã¡rea de trabalho" do GelaFit Control
 * 
 * Esta Activity:
 * 1. Mostra apenas o app escolhido pelo usuÃ¡rio
 * 2. Se is_active = true: bloqueia acesso a outros apps
 * 3. Se is_active = false: permite acesso normal
 * 4. Monitora constantemente o status de is_active no Supabase
 */
class GelaFitWorkspaceActivity : AppCompatActivity() {
    
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private val supabaseManager = SupabaseManager()
    private lateinit var deviceId: String
    private lateinit var preferenceManager: PreferenceManager
    private var isActive: Boolean? = null
    private var isMonitoring = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gelafit_workspace)
        
        deviceId = DeviceIdManager.getDeviceId(this)
        preferenceManager = PreferenceManager(this)
        
        // Configura a Activity para ocupar toda a tela
        setupFullScreen()
        
        // Verifica se hÃ¡ app configurado
        val targetPackage = preferenceManager.getTargetPackageName()
        if (targetPackage.isNullOrEmpty()) {
            Log.w(TAG, "Nenhum app configurado. Redirecionando para seleÃ§Ã£o...")
            val intent = Intent(this, AppSelectionActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(intent)
            finish()
            return
        }
        
        Log.d(TAG, "â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”")
        Log.d(TAG, "ðŸ¢ GelaFit Workspace iniciado")
        Log.d(TAG, "ðŸ“± App configurado: $targetPackage")
        Log.d(TAG, "â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”")
        
        // Inicia monitoramento de is_active
        startMonitoring()
        
        // Abre o app configurado
        openConfiguredApp(targetPackage)
    }
    
    /**
     * Configura a Activity para ocupar toda a tela (fullscreen)
     */
    private fun setupFullScreen() {
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        
        // Remove barra de navegaÃ§Ã£o e status bar
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            window.decorView.systemUiVisibility = (
                android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
        }
    }
    
    /**
     * Abre o app configurado
     */
    private fun openConfiguredApp(packageName: String) {
        try {
            Log.d(TAG, "ðŸš€ Abrindo app: $packageName")
            val appLauncher = AppLauncher(this)
            val success = appLauncher.launchApp(packageName)
            
            if (success) {
                Log.d(TAG, "âœ… App aberto com sucesso")
            } else {
                Log.e(TAG, "âŒ Falha ao abrir app")
            }
        } catch (e: Exception) {
            Log.e(TAG, "âŒ Erro ao abrir app: ${e.message}", e)
        }
    }
    
    /**
     * Inicia monitoramento do status is_active no Supabase
     */
    private fun startMonitoring() {
        if (isMonitoring) {
            Log.d(TAG, "Monitoramento jÃ¡ estÃ¡ ativo")
            return
        }
        
        isMonitoring = true
        serviceScope.launch {
            while (isMonitoring) {
                try {
                    val currentIsActive = supabaseManager.getIsActive(deviceId)
                    
                    // Se mudou o status, aplica as mudanÃ§as
                    if (isActive != currentIsActive) {
                        Log.d(TAG, "â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”")
                        if (currentIsActive == true) {
                            Log.d(TAG, "ðŸ”’ IS_ACTIVE ATIVADO - Bloqueando acesso a outros apps")
                            Log.d(TAG, "â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”")
                            applyAppBlocking()
                        } else {
                            Log.d(TAG, "ðŸ”“ IS_ACTIVE DESATIVADO - Liberando acesso")
                            Log.d(TAG, "â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”")
                            removeAppBlocking()
                        }
                        isActive = currentIsActive
                    }
                    
                    // Se is_active estÃ¡ ativo, garante que apenas o app configurado estÃ¡ aberto
                    if (currentIsActive == true) {
                        ensureOnlyConfiguredAppIsOpen()
                    }
                    
                    delay(CHECK_INTERVAL_MS)
                } catch (e: Exception) {
                    Log.e(TAG, "Erro no monitoramento: ${e.message}", e)
                    delay(ERROR_RETRY_DELAY_MS)
                }
            }
        }
    }
    
    /**
     * Aplica bloqueio de acesso a outros apps
     */
    private fun applyAppBlocking() {
        Log.d(TAG, "ðŸ”’ Aplicando bloqueio de apps...")
        
        // Inicia o serviÃ§o de bloqueio de apps
        try {
            val blockingIntent = Intent(this, com.bootreceiver.app.service.AppBlockingService::class.java).apply {
                putExtra("is_active", true)
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(blockingIntent)
            } else {
                startService(blockingIntent)
            }
            Log.d(TAG, "âœ… ServiÃ§o de bloqueio iniciado")
        } catch (e: Exception) {
            Log.e(TAG, "âŒ Erro ao iniciar serviÃ§o de bloqueio: ${e.message}", e)
        }
    }
    
    /**
     * Remove bloqueio de acesso a outros apps
     */
    private fun removeAppBlocking() {
        Log.d(TAG, "ðŸ”“ Removendo bloqueio de apps...")
        
        // Para o serviÃ§o de bloqueio
        try {
            val blockingIntent = Intent(this, com.bootreceiver.app.service.AppBlockingService::class.java).apply {
                putExtra("is_active", false)
            }
            startService(blockingIntent)
            Log.d(TAG, "âœ… ServiÃ§o de bloqueio parado")
        } catch (e: Exception) {
            Log.e(TAG, "âŒ Erro ao parar serviÃ§o de bloqueio: ${e.message}", e)
        }
    }
    
    /**
     * Garante que apenas o app configurado estÃ¡ aberto
     * Se outro app estiver aberto, fecha e reabre o app configurado
     */
    private fun ensureOnlyConfiguredAppIsOpen() {
        val targetPackage = preferenceManager.getTargetPackageName() ?: return
        
        try {
            val activityManager = getSystemService(android.app.ActivityManager::class.java)
            
            // Verifica qual app estÃ¡ em foreground
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                val runningTasks = activityManager.getAppTasks()
                if (runningTasks != null && runningTasks.isNotEmpty()) {
                    val topTask = runningTasks[0]
                    val taskInfo = topTask.taskInfo
                    if (taskInfo != null && taskInfo.topActivity != null) {
                        val topPackage = taskInfo.topActivity!!.packageName
                        
                        // Se nÃ£o Ã© o app configurado nem o prÃ³prio GelaFit Control, fecha e reabre o app configurado
                        if (topPackage != targetPackage && topPackage != packageName) {
                            Log.w(TAG, "âš ï¸ App nÃ£o autorizado detectado: $topPackage")
                            Log.d(TAG, "ðŸ”„ Fechando e reabrindo app configurado...")
                            
                            // Fecha o app nÃ£o autorizado
                            try {
                                activityManager.killBackgroundProcesses(topPackage)
                            } catch (e: Exception) {
                                Log.w(TAG, "NÃ£o foi possÃ­vel fechar app: ${e.message}")
                            }
                            
                            // Reabre o app configurado
                            delay(500)
                            openConfiguredApp(targetPackage)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao verificar app em foreground: ${e.message}", e)
        }
    }
    
    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume - Garantindo que app configurado estÃ¡ aberto")
        
        // Se is_active estÃ¡ ativo, garante que o app configurado estÃ¡ aberto
        if (isActive == true) {
            val targetPackage = preferenceManager.getTargetPackageName()
            if (!targetPackage.isNullOrEmpty()) {
                openConfiguredApp(targetPackage)
            }
        }
    }
    
    override fun onPause() {
        super.onPause()
        // NÃ£o faz nada - mantÃ©m o monitoramento ativo
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "âš ï¸ GelaFitWorkspaceActivity destruÃ­da")
        isMonitoring = false
    }
    
    override fun onBackPressed() {
        // Se is_active estÃ¡ ativo, bloqueia o botÃ£o voltar
        if (isActive == true) {
            Log.d(TAG, "ðŸ”’ BotÃ£o voltar bloqueado (is_active = true)")
            val targetPackage = preferenceManager.getTargetPackageName()
            if (!targetPackage.isNullOrEmpty()) {
                openConfiguredApp(targetPackage)
            }
            return
        }
        
        // Se is_active estÃ¡ desativado, permite comportamento normal
        super.onBackPressed()
    }
    
    companion object {
        private const val TAG = "GelaFitWorkspace"
        private const val CHECK_INTERVAL_MS = 5000L // Verifica a cada 5 segundos
        private const val ERROR_RETRY_DELAY_MS = 10000L // Em caso de erro, aguarda 10 segundos
    }
}
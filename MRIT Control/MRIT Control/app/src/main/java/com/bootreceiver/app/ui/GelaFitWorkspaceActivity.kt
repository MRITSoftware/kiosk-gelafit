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
 * Activity principal que serve como "área de trabalho" do GelaFit Control
 * 
 * Esta Activity:
 * 1. Mostra apenas o app escolhido pelo usuário
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
        
        // Verifica se há app configurado
        val targetPackage = preferenceManager.getTargetPackageName()
        if (targetPackage.isNullOrEmpty()) {
            Log.w(TAG, "Nenhum app configurado. Redirecionando para seleção...")
            val intent = Intent(this, AppSelectionActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(intent)
            finish()
            return
        }
        
        Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.d(TAG, "🏢 GelaFit Workspace iniciado")
        Log.d(TAG, "📱 App configurado: $targetPackage")
        Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        
        // Inicia monitoramento de is_active (verifica status inicial também)
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
        
        // Remove barra de navegação e status bar
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
            Log.d(TAG, "🚀 Abrindo app: $packageName")
            val appLauncher = AppLauncher(this)
            val success = appLauncher.launchApp(packageName)
            
            if (success) {
                Log.d(TAG, "✅ App aberto com sucesso")
            } else {
                Log.e(TAG, "❌ Falha ao abrir app")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao abrir app: ${e.message}", e)
        }
    }
    
    /**
     * Inicia monitoramento do status is_active no Supabase
     */
    private fun startMonitoring() {
        if (isMonitoring) {
            Log.d(TAG, "Monitoramento já está ativo")
            return
        }
        
        isMonitoring = true
        serviceScope.launch {
            // Verifica status inicial imediatamente
            try {
                val initialIsActive = supabaseManager.getIsActive(deviceId)
                Log.d(TAG, "Status inicial de is_active: $initialIsActive")
                
                // Aplica bloqueio imediatamente se já estiver ativo
                if (initialIsActive == true) {
                    Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                    Log.d(TAG, "🔒 IS_ACTIVE JÁ ESTÁ ATIVO - Aplicando bloqueio imediatamente")
                    Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                    applyAppBlocking()
                }
                isActive = initialIsActive
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao verificar status inicial: ${e.message}", e)
            }
            
            // Loop de monitoramento contínuo
            while (isMonitoring) {
                try {
                    val currentIsActive = supabaseManager.getIsActive(deviceId)
                    
                    // Se mudou o status, aplica as mudanças
                    if (isActive != currentIsActive) {
                        Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                        if (currentIsActive == true) {
                            Log.d(TAG, "🔒 IS_ACTIVE ATIVADO - Bloqueando acesso a outros apps")
                            Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                            applyAppBlocking()
                        } else {
                            Log.d(TAG, "🔓 IS_ACTIVE DESATIVADO - Liberando acesso")
                            Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                            removeAppBlocking()
                        }
                        isActive = currentIsActive
                    }
                    
                    // Se is_active está ativo, garante que apenas o app configurado está aberto
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
        Log.d(TAG, "🔒 Aplicando bloqueio de apps...")
        
        // Inicia o serviço de bloqueio de apps
        try {
            val blockingIntent = Intent(this, com.bootreceiver.app.service.AppBlockingService::class.java).apply {
                putExtra("is_active", true)
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(blockingIntent)
            } else {
                startService(blockingIntent)
            }
            Log.d(TAG, "✅ Serviço de bloqueio iniciado")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao iniciar serviço de bloqueio: ${e.message}", e)
        }
    }
    
    /**
     * Remove bloqueio de acesso a outros apps
     */
    private fun removeAppBlocking() {
        Log.d(TAG, "🔓 Removendo bloqueio de apps...")
        
        // Para o serviço de bloqueio
        try {
            val blockingIntent = Intent(this, com.bootreceiver.app.service.AppBlockingService::class.java).apply {
                putExtra("is_active", false)
            }
            startService(blockingIntent)
            Log.d(TAG, "✅ Serviço de bloqueio parado")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao parar serviço de bloqueio: ${e.message}", e)
        }
    }
    
    /**
     * Garante que apenas o app configurado está aberto
     * Se outro app estiver aberto, fecha e reabre o app configurado
     */
    private suspend fun ensureOnlyConfiguredAppIsOpen() {
        val targetPackage = preferenceManager.getTargetPackageName() ?: return
        
        try {
            val activityManager = getSystemService(android.app.ActivityManager::class.java)
            
            // Verifica qual app está em foreground
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                val runningTasks = activityManager.getAppTasks()
                if (runningTasks != null && runningTasks.isNotEmpty()) {
                    val topTask = runningTasks[0]
                    val taskInfo = topTask.taskInfo
                    if (taskInfo != null && taskInfo.topActivity != null) {
                        val topPackage = taskInfo.topActivity!!.packageName
                        
                        // Se não é o app configurado nem o próprio GelaFit Control, fecha e reabre o app configurado
                        if (topPackage != targetPackage && topPackage != packageName) {
                            Log.w(TAG, "⚠️ App não autorizado detectado: $topPackage")
                            Log.d(TAG, "🔄 Fechando e reabrindo app configurado...")
                            
                            // Fecha o app não autorizado
                            try {
                                activityManager.killBackgroundProcesses(topPackage)
                            } catch (e: Exception) {
                                Log.w(TAG, "Não foi possível fechar app: ${e.message}")
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
        Log.d(TAG, "onResume - Garantindo que app configurado está aberto")
        
        // Se is_active está ativo, garante que o app configurado está aberto
        if (isActive == true) {
            val targetPackage = preferenceManager.getTargetPackageName()
            if (!targetPackage.isNullOrEmpty()) {
                openConfiguredApp(targetPackage)
            }
        }
    }
    
    override fun onPause() {
        super.onPause()
        // Não faz nada - mantém o monitoramento ativo
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "⚠️ GelaFitWorkspaceActivity destruída")
        isMonitoring = false
    }
    
    override fun onBackPressed() {
        // Se is_active está ativo, bloqueia o botão voltar
        if (isActive == true) {
            Log.d(TAG, "🔒 Botão voltar bloqueado (is_active = true)")
            val targetPackage = preferenceManager.getTargetPackageName()
            if (!targetPackage.isNullOrEmpty()) {
                openConfiguredApp(targetPackage)
            }
            return
        }
        
        // Se is_active está desativado, permite comportamento normal
        super.onBackPressed()
    }
    
    companion object {
        private const val TAG = "GelaFitWorkspace"
        private const val CHECK_INTERVAL_MS = 5000L // Verifica a cada 5 segundos
        private const val ERROR_RETRY_DELAY_MS = 10000L // Em caso de erro, aguarda 10 segundos
    }
}

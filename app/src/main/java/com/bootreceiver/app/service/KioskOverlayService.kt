package com.bootreceiver.app.service

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import com.bootreceiver.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Serviço que cria um overlay invisível para interceptar gestos de minimização
 * quando o modo kiosk está ativo
 * 
 * O overlay cobre toda a tela e intercepta eventos de toque que poderiam minimizar o app
 */
class KioskOverlayService : Service() {
    
    private var overlayView: View? = null
    private var windowManager: WindowManager? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var isKioskEnabled = false
    private var monitoringJob: Job? = null
    
    // Contador de cliques para forçar sincronização
    private var tapCount = 0
    private var lastTapTime = 0L
    private val TAP_TIMEOUT_MS = 1000L // 1 segundo entre cliques
    private val REQUIRED_TAPS = 5 // 5 cliques para forçar sync
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "KioskOverlayService criado")
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        isKioskEnabled = intent?.getBooleanExtra("kiosk_enabled", false) ?: false
        
        if (isKioskEnabled) {
            // Inicia monitoramento contínuo para verificar activities permitidas
            startMonitoring()
            updateOverlayVisibility()
        } else {
            stopMonitoring()
            hideOverlay()
        }
        
        return START_NOT_STICKY
    }
    
    /**
     * Inicia monitoramento contínuo para verificar se activities permitidas estão em foreground
     */
    private fun startMonitoring() {
        monitoringJob?.cancel()
        monitoringJob = serviceScope.launch {
            while (isKioskEnabled) {
                updateOverlayVisibility()
                delay(500) // Verifica a cada 500ms
            }
        }
    }
    
    /**
     * Para o monitoramento
     */
    private fun stopMonitoring() {
        monitoringJob?.cancel()
        monitoringJob = null
    }
    
    /**
     * Atualiza visibilidade do overlay baseado em se uma activity permitida está em foreground
     */
    private fun updateOverlayVisibility() {
        val isAllowedActivityInForeground = checkIfAllowedActivityInForeground()
        
        if (isKioskEnabled && !isAllowedActivityInForeground) {
            if (overlayView == null) {
                showOverlay()
            }
        } else {
            if (overlayView != null) {
                hideOverlay()
            }
        }
    }
    
    /**
     * Verifica se uma activity permitida está em foreground
     * Activities permitidas: SettingsCheckActivity, AddProductActivity
     */
    private fun checkIfAllowedActivityInForeground(): Boolean {
        try {
            val activityManager = getSystemService(android.app.ActivityManager::class.java)
            val allowedActivities = setOf(
                "com.bootreceiver.app.ui.SettingsCheckActivity",
                "com.bootreceiver.app.ui.AddProductActivity"
            )
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                val runningTasks = activityManager.getAppTasks()
                if (runningTasks != null && runningTasks.isNotEmpty()) {
                    val topTask = runningTasks[0]
                    val taskInfo = topTask.taskInfo
                    if (taskInfo != null && taskInfo.topActivity != null) {
                        val topActivity = taskInfo.topActivity!!.className
                        return topActivity in allowedActivities
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Erro ao verificar activity em foreground: ${e.message}")
        }
        return false
    }
    
    private fun showOverlay() {
        if (overlayView != null) {
            Log.d(TAG, "Overlay já está visível")
            return
        }
        
        try {
            Log.d(TAG, "🔒 Mostrando overlay de kiosk...")
            
            // Cria uma view invisível que cobre toda a tela
            overlayView = FrameLayout(this).apply {
                var startX = 0f
                var startY = 0f
                var isTrackingBackGesture = false
                var isBackGesture = false
                
                // IMPORTANTE: Não usar setOnTouchListener aqui!
                // Isso bloqueia todos os toques. Em vez disso, vamos usar uma abordagem diferente:
                // Criar uma view muito fina apenas nas bordas laterais para interceptar gestos
                
                // View vazia que não interfere com toques normais
                // O overlay só intercepta eventos se realmente for um gesto de voltar
                setOnTouchListener { _, event ->
                    val screenWidth = resources.displayMetrics.widthPixels
                    val edgeThreshold = 20f // Área menor (20px) nas bordas laterais
                    
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            startX = event.x
                            startY = event.y
                            isTrackingBackGesture = false
                            isBackGesture = false
                            
                            // Verifica se o toque começou na borda lateral (esquerda ou direita)
                            val isLeftEdge = startX < edgeThreshold
                            val isRightEdge = startX > screenWidth - edgeThreshold
                            
                            // Só começa a rastrear se for na borda lateral (gesto de voltar)
                            if (isLeftEdge || isRightEdge) {
                                isTrackingBackGesture = true
                                Log.d(TAG, "🔍 Rastreando possível gesto de voltar (borda ${if (isLeftEdge) "esquerda" else "direita"})")
                            } else {
                                // Se não é gesto de voltar, processa para detecção de 5 cliques
                                handleTap()
                            }
                            
                            // SEMPRE permite ACTION_DOWN passar - não bloqueia cliques
                            false
                        }
                        MotionEvent.ACTION_MOVE -> {
                            if (!isTrackingBackGesture) {
                                // Se não está rastreando gesto de voltar, permite tudo passar
                                return@setOnTouchListener false
                            }
                            
                            val deltaX = event.x - startX
                            val deltaY = event.y - startY
                            val absDeltaX = Math.abs(deltaX)
                            val absDeltaY = Math.abs(deltaY)
                            
                            val isLeftEdge = startX < edgeThreshold
                            val isRightEdge = startX > screenWidth - edgeThreshold
                            
                            // Gesto de voltar: swipe da borda lateral para dentro da tela
                            // Deve ser principalmente horizontal (deltaX > deltaY) e movimento significativo
                            if ((isLeftEdge || isRightEdge) && absDeltaX > 80f) {
                                // Verifica se está se movendo para dentro da tela (direção correta do gesto)
                                val isMovingInward = (isLeftEdge && deltaX > 0) || (isRightEdge && deltaX < 0)
                                
                                // Só bloqueia se for movimento horizontal para dentro (gesto de voltar)
                                if (isMovingInward && absDeltaX > absDeltaY * 1.5f) {
                                    isBackGesture = true
                                    Log.d(TAG, "🔒 Gesto de VOLTAR detectado e bloqueado! (swipe da borda ${if (isLeftEdge) "esquerda" else "direita"})")
                                    return@setOnTouchListener true // Bloqueia APENAS o gesto de voltar
                                }
                            }
                            
                            // Permite movimento se não for gesto de voltar
                            false
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            if (isBackGesture) {
                                // Se foi um gesto de voltar, bloqueia o ACTION_UP também
                                Log.d(TAG, "🔒 Finalizando bloqueio de gesto de voltar")
                                isTrackingBackGesture = false
                                isBackGesture = false
                                return@setOnTouchListener true
                            }
                            
                            // Permite ACTION_UP normal passar (cliques funcionam)
                            isTrackingBackGesture = false
                            isBackGesture = false
                            false
                        }
                    }
                    false // Por padrão, permite TODOS os eventos passarem
                }
            }
            
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                },
                // FLAG_NOT_TOUCH_MODAL permite que toques passem através do overlay
                // mas ainda recebe eventos para interceptar gestos de voltar
                // IMPORTANTE: O setOnTouchListener retorna false na maioria dos casos,
                // permitindo que cliques normais funcionem normalmente
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                    or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSPARENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 0
                y = 0
                alpha = 0.0f // Totalmente transparente
            }
            
            windowManager?.addView(overlayView, params)
            Log.d(TAG, "✅ Overlay de kiosk mostrado")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao mostrar overlay: ${e.message}", e)
        }
    }
    
    private fun hideOverlay() {
        overlayView?.let { view ->
            try {
                windowManager?.removeView(view)
                overlayView = null
                Log.d(TAG, "🔓 Overlay de kiosk removido")
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao remover overlay: ${e.message}", e)
            }
        }
    }
    
    /**
     * Processa toques na tela para detectar 5 cliques consecutivos
     */
    private fun handleTap() {
        val currentTime = System.currentTimeMillis()
        
        // Se passou muito tempo desde o último clique, reseta contador
        if (currentTime - lastTapTime > TAP_TIMEOUT_MS) {
            tapCount = 0
        }
        
        lastTapTime = currentTime
        tapCount++
        
        Log.d(TAG, "👆 Clique detectado no overlay ($tapCount/$REQUIRED_TAPS)")
        
        // Se chegou a 5 cliques, força sincronização
        if (tapCount >= REQUIRED_TAPS) {
            tapCount = 0 // Reseta contador
            Log.d(TAG, "🔄 5 cliques detectados no overlay! Forçando sincronização imediata...")
            forceSync()
            
            // Mostra feedback visual
            vibrateShort()
            Toast.makeText(this, "🔄 Sincronizando com banco...", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Força sincronização enviando broadcast para KioskModeService
     */
    private fun forceSync() {
        val intent = Intent("com.bootreceiver.app.FORCE_SYNC")
        sendBroadcast(intent)
        Log.d(TAG, "📡 Broadcast de sincronização forçada enviado")
    }
    
    /**
     * Vibração curta para feedback
     */
    private fun vibrateShort() {
        try {
            val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(100)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Erro ao vibrar: ${e.message}")
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopMonitoring()
        hideOverlay()
        serviceScope.cancel()
        Log.d(TAG, "KioskOverlayService destruído")
    }
    
    companion object {
        private const val TAG = "KioskOverlayService"
    }
}

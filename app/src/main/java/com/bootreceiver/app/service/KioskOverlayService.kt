package com.bootreceiver.app.service

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import com.bootreceiver.app.R

/**
 * Serviço que cria um overlay invisível para interceptar gestos de minimização
 * quando o modo kiosk está ativo
 * 
 * O overlay cobre toda a tela e intercepta eventos de toque que poderiam minimizar o app
 */
class KioskOverlayService : Service() {
    
    private var overlayView: View? = null
    private var windowManager: WindowManager? = null
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "KioskOverlayService criado")
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val kioskEnabled = intent?.getBooleanExtra("kiosk_enabled", false) ?: false
        
        if (kioskEnabled) {
            showOverlay()
        } else {
            hideOverlay()
        }
        
        return START_NOT_STICKY
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
                // FLAG_NOT_TOUCHABLE permite que toques passem através do overlay
                // Mas precisamos interceptar gestos de voltar, então usamos FLAG_NOT_TOUCH_MODAL
                // que permite toques passarem mas ainda recebe eventos
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
    
    override fun onDestroy() {
        super.onDestroy()
        hideOverlay()
        Log.d(TAG, "KioskOverlayService destruído")
    }
    
    companion object {
        private const val TAG = "KioskOverlayService"
    }
}

package com.bootreceiver.app.ui

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.bootreceiver.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Activity para verificar se todas as configurações necessárias estão ativas
 */
class SettingsCheckActivity : AppCompatActivity() {
    
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings_check)
        
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Verificar Configurações"
        
        checkAllSettings()
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
    
    private fun checkAllSettings() {
        serviceScope.launch {
            val checks = mutableListOf<SettingCheck>()
            
            // 1. Verifica permissão de sobreposição (SYSTEM_ALERT_WINDOW)
            val overlayPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.canDrawOverlays(this@SettingsCheckActivity)
            } else {
                true
            }
            checks.add(SettingCheck(
                "Permissão de Sobreposição",
                overlayPermission,
                "Necessária para modo kiosk funcionar corretamente",
                if (overlayPermission) "✅ Ativa" else "❌ Inativa - Ative em Configurações > Apps > GelaFit Control > Exibir sobre outros apps"
            ))
            
            // 2. Verifica se há app configurado
            val preferenceManager = com.bootreceiver.app.utils.PreferenceManager(this@SettingsCheckActivity)
            val hasAppConfigured = !preferenceManager.getTargetPackageName().isNullOrEmpty()
            checks.add(SettingCheck(
                "App Configurado",
                hasAppConfigured,
                "Um app deve estar configurado para o modo kiosk funcionar",
                if (hasAppConfigured) "✅ Configurado" else "❌ Nenhum app configurado"
            ))
            
            // 3. Verifica se o app configurado está instalado
            if (hasAppConfigured) {
                val targetPackage = preferenceManager.getTargetPackageName()
                val isInstalled = try {
                    packageManager.getPackageInfo(targetPackage!!, 0)
                    true
                } catch (e: PackageManager.NameNotFoundException) {
                    false
                }
                checks.add(SettingCheck(
                    "App Instalado",
                    isInstalled,
                    "O app configurado deve estar instalado",
                    if (isInstalled) "✅ Instalado" else "❌ Não encontrado"
                ))
            }
            
            
            // Atualiza UI
            runOnUiThread {
                displayChecks(checks)
            }
        }
    }
    
    private fun displayChecks(checks: List<SettingCheck>) {
        val container = findViewById<android.widget.LinearLayout>(R.id.checksContainer)
        container.removeAllViews()
        
        checks.forEach { check ->
            val view = layoutInflater.inflate(R.layout.item_setting_check, container, false)
            val titleView = view.findViewById<TextView>(R.id.checkTitle)
            val statusView = view.findViewById<TextView>(R.id.checkStatus)
            val descriptionView = view.findViewById<TextView>(R.id.checkDescription)
            
            titleView.text = check.title
            statusView.text = check.status
            descriptionView.text = check.description
            
            if (check.isOk) {
                statusView.setTextColor(getColor(android.R.color.holo_green_dark))
            } else {
                statusView.setTextColor(getColor(android.R.color.holo_red_dark))
            }
            
            container.addView(view)
        }
    }
    
    data class SettingCheck(
        val title: String,
        val isOk: Boolean,
        val description: String,
        val status: String
    )
    
    companion object {
        private const val TAG = "SettingsCheck"
    }
}

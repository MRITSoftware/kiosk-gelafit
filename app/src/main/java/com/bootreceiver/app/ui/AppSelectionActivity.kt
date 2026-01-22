package com.bootreceiver.app.ui

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.bootreceiver.app.R
import com.bootreceiver.app.utils.DeviceIdManager
import com.bootreceiver.app.utils.PermissionChecker
import com.bootreceiver.app.utils.PreferenceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Activity que permite ao usuário escolher qual app será aberto automaticamente
 * 
 * Esta tela aparece apenas na primeira vez que o app é aberto manualmente.
 * Após selecionar um app, ele será salvo e usado automaticamente nos próximos boots.
 */
class AppSelectionActivity : AppCompatActivity() {
    
    private lateinit var listView: ListView
    private lateinit var searchEditText: EditText
    private lateinit var preferenceManager: PreferenceManager
    private val appsList = mutableListOf<AppInfo>()
    private val filteredAppsList = mutableListOf<AppInfo>()
    private lateinit var adapter: AppListAdapter
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_selection)
        
        preferenceManager = PreferenceManager(this)
        
        // Verifica permissões e otimizações
        checkPermissionsAndOptimizations()
        
        // Verifica se o dispositivo precisa ser registrado
        if (!preferenceManager.isDeviceRegistered()) {
            showDeviceRegistrationDialog()
            return
        }
        
        // Se já estiver configurado, fecha esta activity e abre o app configurado
        if (preferenceManager.isConfigured()) {
            Log.d(TAG, "App já configurado. Abrindo app configurado...")
            val targetPackage = preferenceManager.getTargetPackageName()
            if (targetPackage != null) {
                val appLauncher = com.bootreceiver.app.utils.AppLauncher(this)
                appLauncher.launchApp(targetPackage)
            }
            finish()
            return
        }
        
        // Configura a UI normalmente
        setupUI()
    }
    
    /**
     * Mostra diálogo para registrar o dispositivo com email da unidade
     */
    private fun showDeviceRegistrationDialog() {
        val input = EditText(this)
        input.hint = "Email da unidade (ex: gelafit@gelafit.com)"
        input.inputType = android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        
        AlertDialog.Builder(this)
            .setTitle("Registro do Dispositivo")
            .setMessage(
                "Bem-vindo ao GelaFit Control!\n\n" +
                "Para registrar este dispositivo, informe o email da unidade:"
            )
            .setView(input)
            .setPositiveButton("Registrar") { _, _ ->
                val email = input.text.toString().trim()
                if (email.isBlank()) {
                    Toast.makeText(this, "Por favor, informe o email da unidade", Toast.LENGTH_SHORT).show()
                    showDeviceRegistrationDialog() // Mostra novamente
                } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    Toast.makeText(this, "Por favor, informe um email válido", Toast.LENGTH_SHORT).show()
                    showDeviceRegistrationDialog() // Mostra novamente
                } else {
                    val deviceId = DeviceIdManager.getDeviceId(this)
                    registerDevice(deviceId, email)
                }
            }
            .setCancelable(false)
            .show()
    }
    
    /**
     * Registra o dispositivo no Supabase
     */
    private fun registerDevice(deviceId: String, unitName: String) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                // CRÍTICO: Define cache como false ANTES de registrar, para evitar que serviços iniciem com is_active=true
                preferenceManager.saveIsActiveCached(false)
                preferenceManager.saveKioskModeCached(false)
                preferenceManager.saveStatusLastSync(System.currentTimeMillis())
                Log.d(TAG, "Cache definido como false antes do registro")
                
                val supabaseManager = com.bootreceiver.app.utils.SupabaseManager()
                val success = supabaseManager.registerDevice(deviceId, unitName)
                
                if (success) {
                    // Salva localmente que foi registrado
                    preferenceManager.setDeviceRegistered(true)
                    preferenceManager.saveUnitName(unitName)
                    
                    // Após registrar, verifica os valores do banco mas garante que is_active seja false
                    try {
                        val status = withContext(Dispatchers.IO) {
                            supabaseManager.getDeviceStatus(deviceId)
                        }
                        if (status != null) {
                            // CRÍTICO: Se o banco retornar is_active=true (erro), força false
                            val isActiveFromDb = status.isActive
                            val kioskModeFromDb = status.kioskMode
                            
                            // Se is_active veio como true do banco, algo está errado - força false
                            if (isActiveFromDb) {
                                Log.w(TAG, "⚠️ ATENÇÃO: Banco retornou is_active=true após registro! Forçando false...")
                                // Atualiza o banco para false
                                withContext(Dispatchers.IO) {
                                    supabaseManager.updateIsActive(deviceId, false)
                                }
                                preferenceManager.saveIsActiveCached(false)
                            } else {
                                preferenceManager.saveIsActiveCached(false) // Garante false mesmo se banco retornar false
                            }
                            
                            // kiosk_mode sempre deve ser false após registro
                            if (kioskModeFromDb) {
                                Log.w(TAG, "⚠️ ATENÇÃO: Banco retornou kiosk_mode=true após registro! Forçando false...")
                                withContext(Dispatchers.IO) {
                                    supabaseManager.updateKioskMode(deviceId, false)
                                }
                                preferenceManager.saveKioskModeCached(false)
                            } else {
                                preferenceManager.saveKioskModeCached(false)
                            }
                            
                            preferenceManager.saveStatusLastSync(System.currentTimeMillis())
                            Log.d(TAG, "✅ Cache garantido como false após registro: is_active=false, kiosk_mode=false")
                        } else {
                            // Se não conseguiu buscar, mantém false que já foi definido
                            Log.d(TAG, "Status não encontrado, mantendo cache como false")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Erro ao carregar status após registro: ${e.message}")
                        // Mantém false que já foi definido antes do registro
                        Log.d(TAG, "Mantendo cache como false após erro")
                    }
                    
                    Toast.makeText(
                        this@AppSelectionActivity,
                        "Dispositivo registrado com sucesso!",
                        Toast.LENGTH_LONG
                    ).show()
                    
                    Log.d(TAG, "Dispositivo registrado: $deviceId com nome: $unitName")
                    
                    // Continua com o fluxo normal
                    setupUI()
                } else {
                    Toast.makeText(
                        this@AppSelectionActivity,
                        "Erro ao registrar dispositivo. Verifique sua conexão com a internet.",
                        Toast.LENGTH_LONG
                    ).show()
                    // Tenta novamente
                    showDeviceRegistrationDialog()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao registrar dispositivo: ${e.message}", e)
                Toast.makeText(
                    this@AppSelectionActivity,
                    "Erro ao registrar: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
                // Tenta novamente
                showDeviceRegistrationDialog()
            }
        }
    }
    
    /**
     * Configura a UI após o registro do dispositivo
     */
    private fun setupUI() {
        listView = findViewById(R.id.listViewApps)
        searchEditText = findViewById(R.id.searchEditText)
        
        // Inicializa adapter vazio
        adapter = AppListAdapter(filteredAppsList)
        listView.adapter = adapter
        
        // Configura a barra de pesquisa
        setupSearchBar()
        
        // Carrega lista de apps em background
        loadInstalledApps()
        
        // Configura o click na lista
        listView.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            val selectedApp = filteredAppsList[position]
            showConfirmationDialog(selectedApp)
        }
    }
    
    /**
     * Configura a barra de pesquisa para filtrar apps
     */
    private fun setupSearchBar() {
        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterApps(s.toString())
            }
            
            override fun afterTextChanged(s: Editable?) {}
        })
    }
    
    /**
     * Filtra a lista de apps baseado no texto de pesquisa
     */
    private fun filterApps(query: String) {
        filteredAppsList.clear()
        
        if (query.isBlank()) {
            filteredAppsList.addAll(appsList)
        } else {
            val lowerQuery = query.lowercase()
            appsList.forEach { app ->
                if (app.name.lowercase().contains(lowerQuery) || 
                    app.packageName.lowercase().contains(lowerQuery)) {
                    filteredAppsList.add(app)
                }
            }
        }
        
        updateAdapter()
    }
    
    /**
     * Atualiza o adapter com a lista filtrada
     */
    private fun updateAdapter() {
        adapter.updateList(filteredAppsList)
    }
    
    /**
     * Mostra diálogo de confirmação antes de selecionar o app
     */
    private fun showConfirmationDialog(app: AppInfo) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.confirm_selection))
            .setMessage(getString(R.string.confirm_message, app.name))
            .setPositiveButton("Confirmar") { _, _ ->
                selectApp(app.packageName, app.name)
            }
            .setNegativeButton("Cancelar", null)
            .setCancelable(true)
            .show()
    }
    
    /**
     * Verifica permissões e otimizações e alerta o usuário se necessário
     */
    private fun checkPermissionsAndOptimizations() {
        val permissionChecker = PermissionChecker(this)
        val status = permissionChecker.getFullStatus()
        
        if (!status.isReady) {
            Log.w(TAG, "Problemas detectados: ${status.issues.joinToString()}")
            
            val message = buildString {
                append("Para garantir que o app funcione corretamente, é necessário:\n\n")
                status.issues.forEach { issue ->
                    append("• $issue\n")
                }
                append("\nDeseja configurar agora?")
            }
            
            AlertDialog.Builder(this)
                .setTitle("Configurações Necessárias")
                .setMessage(message)
                .setPositiveButton("Configurar") { _, _ ->
                    // Abre configurações de otimização de bateria
                    if (status.batteryOptimized) {
                        permissionChecker.openBatteryOptimizationSettings()
                    }
                    // Abre configurações de overlay se necessário
                    if (status.issues.any { it.contains("SYSTEM_ALERT_WINDOW") }) {
                        permissionChecker.openOverlayPermissionSettings()
                    }
                }
                .setNegativeButton("Depois", null)
                .setCancelable(true)
                .show()
        } else {
            Log.d(TAG, "Todas as permissões e otimizações estão corretas")
        }
    }
    
    /**
     * Carrega lista de aplicativos instalados
     * Executa em background para não travar a UI
     */
    private fun loadInstalledApps() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val packageManager = packageManager
                val installedPackages = packageManager.getInstalledPackages(PackageManager.GET_META_DATA)
                
                val apps = installedPackages
                    .filter { 
                        // Filtra apenas apps que podem ser abertos
                        val appInfo = it.applicationInfo
                        val launchIntent = packageManager.getLaunchIntentForPackage(it.packageName)
                        // Inclui apps que têm intent de launch (podem ser abertos)
                        launchIntent != null
                    }
                    .map {
                        val appInfo = it.applicationInfo
                        val appName = try {
                            packageManager.getApplicationLabel(appInfo).toString()
                        } catch (e: Exception) {
                            it.packageName
                        }
                        AppInfo(appName, it.packageName)
                    }
                    .sortedBy { it.name }
                    .distinctBy { it.packageName }
                
                withContext(Dispatchers.Main) {
                    Log.d(TAG, "Carregados ${apps.size} aplicativos")
                    appsList.clear()
                    appsList.addAll(apps)
                    filteredAppsList.clear()
                    filteredAppsList.addAll(apps)
                    updateAdapter()
                    
                    if (appsList.isEmpty()) {
                        Toast.makeText(
                            this@AppSelectionActivity,
                            "Nenhum aplicativo encontrado",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Log.d(TAG, "Lista atualizada com sucesso. Total: ${appsList.size} apps")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao carregar apps", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@AppSelectionActivity,
                        "Erro ao carregar aplicativos: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
    
    /**
     * Salva o app selecionado e abre o GelaFit Workspace imediatamente
     * Atualiza is_active para true após selecionar o app
     */
    private fun selectApp(packageName: String, appName: String) {
        Log.d(TAG, "App selecionado: $appName ($packageName)")
        
        // Salva o package name
        preferenceManager.saveTargetPackageName(packageName)
        
        // Adiciona à lista de apps selecionados para o grid
        val currentApps = preferenceManager.getSelectedAppsList().toMutableSet()
        currentApps.add(packageName)
        preferenceManager.saveSelectedAppsList(currentApps)
        
        // CRÍTICO: NÃO atualiza is_active para true automaticamente após selecionar o app
        // is_active e kiosk_mode devem ser FALSE por padrão e só ativados manualmente pelo usuário
        // Garante que ambos permaneçam como false
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val deviceId = DeviceIdManager.getDeviceId(this@AppSelectionActivity)
                val supabaseManager = com.bootreceiver.app.utils.SupabaseManager()
                
                // Garante que is_active e kiosk_mode sejam false
                val activeSuccess = withContext(Dispatchers.IO) {
                    supabaseManager.updateIsActive(deviceId, false)
                }
                val kioskSuccess = withContext(Dispatchers.IO) {
                    supabaseManager.updateKioskMode(deviceId, false)
                }
                
                if (activeSuccess && kioskSuccess) {
                    // Atualiza cache local como false
                    preferenceManager.saveIsActiveCached(false)
                    preferenceManager.saveKioskModeCached(false)
                    preferenceManager.saveStatusLastSync(System.currentTimeMillis())
                    Log.d(TAG, "✅ is_active e kiosk_mode garantidos como false após selecionar app")
                } else {
                    Log.w(TAG, "⚠️ Falha ao garantir is_active e kiosk_mode como false no banco")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao garantir is_active e kiosk_mode como false: ${e.message}", e)
            }
        }
        
        // Abre o GelaFit Workspace imediatamente (sem delay)
        val intent = Intent(this, GelaFitWorkspaceActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        startActivity(intent)
        finish()
    }
    
    /**
     * Adapter customizado para exibir apps na lista
     */
    private inner class AppListAdapter(apps: MutableList<AppInfo>) : BaseAdapter() {
        private val appsList = mutableListOf<AppInfo>().apply { addAll(apps) }
        
        fun updateList(newApps: List<AppInfo>) {
            appsList.clear()
            appsList.addAll(newApps)
            notifyDataSetChanged()
            Log.d(TAG, "Adapter atualizado com ${appsList.size} apps")
        }
        
        override fun getCount(): Int = appsList.size
        
        override fun getItem(position: Int): AppInfo = appsList[position]
        
        override fun getItemId(position: Int): Long = position.toLong()
        
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: layoutInflater.inflate(R.layout.list_item_app, parent, false)
            
            if (position < appsList.size) {
                val app = appsList[position]
                
                val appNameView = view.findViewById<TextView>(R.id.appName)
                val appPackageView = view.findViewById<TextView>(R.id.appPackage)
                
                appNameView?.text = app.name
                appPackageView?.text = app.packageName
            }
            
            return view
        }
    }
    
    /**
     * Classe de dados para representar um app
     */
    data class AppInfo(
        val name: String,
        val packageName: String
    )
    
    companion object {
        private const val TAG = "AppSelectionActivity"
    }
}

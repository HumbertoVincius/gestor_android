package com.humberto.gestorfinanceiro

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import java.io.File
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.humberto.gestorfinanceiro.data.settings.SettingsManager
import com.humberto.gestorfinanceiro.ui.categories.CategoriesScreen
import com.humberto.gestorfinanceiro.ui.chat.ChatBottomSheet
import com.humberto.gestorfinanceiro.ui.home.DebugScreen
import com.humberto.gestorfinanceiro.ui.home.ExpensesScreen
import com.humberto.gestorfinanceiro.ui.metas.MetasScreen
import com.humberto.gestorfinanceiro.ui.navigation.Screen
import com.humberto.gestorfinanceiro.ui.theme.GestorFinanceiroTheme
import com.humberto.gestorfinanceiro.utils.NotificationHelper

class MainActivity : ComponentActivity() {
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Handle permission results
    }
    
    // Launcher para captura de foto
    private var capturedImageUri: Uri? = null
    private var onImageCaptured: ((Uri?) -> Unit)? = null
    private var onCameraPermissionGranted: (() -> Unit)? = null
    
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            onCameraPermissionGranted?.invoke()
        }
        onCameraPermissionGranted = null
    }
    
    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && capturedImageUri != null) {
            onImageCaptured?.invoke(capturedImageUri)
        } else {
            onImageCaptured?.invoke(null)
        }
        onImageCaptured = null
        capturedImageUri = null
    }
    
    fun requestCameraPermission(onPermissionGranted: () -> Unit) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            onPermissionGranted()
        } else {
            onCameraPermissionGranted = onPermissionGranted
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
    
    fun takePicture(onImageCaptured: (Uri?) -> Unit) {
        this.onImageCaptured = onImageCaptured
        
        // Criar arquivo temporário para a foto
        val photoFile = File(getExternalFilesDir(null), "temp_photo_${System.currentTimeMillis()}.jpg")
        capturedImageUri = FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            photoFile
        )
        
        takePictureLauncher.launch(capturedImageUri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            // Inicializar SettingsManager
            SettingsManager.initialize(this)
            
            // Criar canal de notificação
            NotificationHelper.createNotificationChannel(this)
            
            checkPermissions()
            
            setContent {
                GestorFinanceiroTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        MainNavigation()
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Erro fatal no onCreate", e)
            // Mostrar mensagem de erro ao usuário
            setContent {
                GestorFinanceiroTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "Erro ao iniciar o aplicativo",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Por favor, reinicie o aplicativo",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
    
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent) // Atualizar o intent para que LaunchedEffect possa processá-lo
    }

    private fun checkPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS
        )
        
        // Adicionar permissão de notificação para Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        
        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest)
        }
    }
    
    companion object {
        @JvmStatic
        fun getMainActivity(context: android.content.Context): MainActivity? {
            return context as? MainActivity
        }
    }
}

@Composable
fun MainNavigation() {
    var selectedScreen by remember { mutableStateOf(Screen.METAS) }
    var expandedCategory by remember { mutableStateOf<String?>(null) }
    var showChatBottomSheet by remember { mutableStateOf(false) }
    
    // Processar deep link do intent apenas uma vez na inicialização
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = remember { context as? MainActivity }
    
    // Processar intent inicial apenas uma vez
    LaunchedEffect(Unit) {
        activity?.let { act ->
            try {
                val intent = act.intent
                val data = intent?.data
                
                // Processar deep link
                if (data?.scheme == "app" && data.host == "home") {
                    val categoryParam = data.getQueryParameter("category")
                    if (categoryParam != null && categoryParam.isNotBlank()) {
                        selectedScreen = Screen.METAS // Navegar para tela Metas
                        expandedCategory = categoryParam
                    }
                }
                
                // Processar extra do widget
                val screenExtra = intent?.getStringExtra("screen")
                if (screenExtra != null) {
                    try {
                        selectedScreen = Screen.valueOf(screenExtra)
                    } catch (e: Exception) {
                        android.util.Log.e("MainNavigation", "Tela inválida: $screenExtra", e)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MainNavigation", "Erro ao processar intent", e)
            }
        }
    }
    
    Scaffold(
        bottomBar = {
            Box(modifier = Modifier.fillMaxWidth()) {
                NavigationBar {
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                        label = { Text("Home") },
                        selected = selectedScreen == Screen.METAS,
                        onClick = { selectedScreen = Screen.METAS }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.ShoppingCart, contentDescription = "Despesas") },
                        label = { Text("Despesas") },
                        selected = selectedScreen == Screen.HOME,
                        onClick = { selectedScreen = Screen.HOME }
                    )
                    // Espaço para o FAB
                    NavigationBarItem(
                        icon = { Box(modifier = Modifier.size(24.dp)) },
                        label = { Text("") },
                        selected = false,
                        onClick = { }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Edit, contentDescription = "Categorias") },
                        label = { Text("Categorias") },
                        selected = selectedScreen == Screen.CATEGORIES,
                        onClick = { selectedScreen = Screen.CATEGORIES }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Debug") },
                        label = { Text("Debug") },
                        selected = selectedScreen == Screen.DEBUG,
                        onClick = { selectedScreen = Screen.DEBUG }
                    )
                }
                // FAB no centro
                FloatingActionButton(
                    onClick = { showChatBottomSheet = true },
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(48.dp),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(
                        Icons.Default.Message,
                        contentDescription = "Chat",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (selectedScreen) {
                Screen.HOME -> ExpensesScreen(expandedCategory = expandedCategory)
                Screen.METAS -> MetasScreen(expandedCategory = expandedCategory)
                Screen.CATEGORIES -> CategoriesScreen()
                Screen.DEBUG -> DebugScreen()
            }
        }
    }
    
    // BottomSheet de Chat
    ChatBottomSheet(
        isVisible = showChatBottomSheet,
        onDismiss = { showChatBottomSheet = false }
    )
}

package com.humberto.gestorfinanceiro

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.humberto.gestorfinanceiro.data.settings.SettingsManager
import com.humberto.gestorfinanceiro.ui.categories.CategoriesScreen
import com.humberto.gestorfinanceiro.ui.home.DebugScreen
import com.humberto.gestorfinanceiro.ui.home.HomeScreen
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
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
}

@Composable
fun MainNavigation() {
    var selectedScreen by remember { mutableStateOf(Screen.METAS) }
    var expandedCategory by remember { mutableStateOf<String?>(null) }
    
    // Processar deep link do intent
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) {
        val intent = (context as? MainActivity)?.intent
        val data = intent?.data
        if (data?.scheme == "app" && data.host == "home") {
            val categoryParam = data.getQueryParameter("category")
            if (categoryParam != null) {
                selectedScreen = Screen.HOME
                expandedCategory = categoryParam
            }
        }
    }
    
    Scaffold(
        bottomBar = {
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
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (selectedScreen) {
                Screen.HOME -> HomeScreen(expandedCategory = expandedCategory)
                Screen.METAS -> MetasScreen()
                Screen.CATEGORIES -> CategoriesScreen()
                Screen.DEBUG -> DebugScreen()
            }
        }
    }
}

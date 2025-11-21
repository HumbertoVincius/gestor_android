package com.humberto.gestorfinanceiro.ui.home

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import com.humberto.gestorfinanceiro.data.model.Expense
import com.humberto.gestorfinanceiro.data.supabase.ConnectionTestResult
import com.humberto.gestorfinanceiro.di.Dependencies
import com.humberto.gestorfinanceiro.ui.home.formatCurrency
import kotlinx.coroutines.launch

private const val TAG = "DebugScreen"

@Composable
fun DebugScreen() {
    var expenses by remember { mutableStateOf<List<Expense>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isTestingConnection by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<ConnectionTestResult?>(null) }
    val scope = rememberCoroutineScope()

    fun loadExpenses() {
        scope.launch {
            isLoading = true
            errorMessage = null
            Log.d(TAG, "Iniciando carregamento de despesas...")
            try {
                val fetchedExpenses = Dependencies.supabaseRepository.getAllExpenses()
                expenses = fetchedExpenses
                Log.d(TAG, "Despesas carregadas na UI: ${fetchedExpenses.size}")
                if (fetchedExpenses.isEmpty()) {
                    Log.w(TAG, "Nenhuma despesa retornada do repositório")
                }
            } catch (e: Exception) {
                errorMessage = "Erro ao carregar despesas: ${e.message}"
                Log.e(TAG, "Erro ao carregar despesas na UI", e)
                e.printStackTrace()
            } finally {
                isLoading = false
                Log.d(TAG, "Carregamento finalizado. isLoading = false")
            }
        }
    }

    LaunchedEffect(Unit) {
        loadExpenses()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        // Header com botão de teste
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Debug - Despesas",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Button(
                onClick = {
                    scope.launch {
                        isTestingConnection = true
                        testResult = null
                        Log.d(TAG, "Iniciando teste de conexão...")
                        val result = Dependencies.supabaseRepository.testConnection()
                        testResult = result
                        isTestingConnection = false
                        Log.d(TAG, "Teste de conexão concluído: ${result.message}")
                        // Recarregar despesas após o teste
                        loadExpenses()
                    }
                },
                enabled = !isTestingConnection
            ) {
                if (isTestingConnection) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Testar Conexão")
                }
            }
        }
        
        // Mostrar resultado do teste
        testResult?.let { result ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (result.success) 
                        MaterialTheme.colorScheme.primaryContainer 
                    else 
                        MaterialTheme.colorScheme.errorContainer
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = if (result.success) "✓ Teste de Conexão" else "✗ Teste de Conexão",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (result.success) 
                            MaterialTheme.colorScheme.onPrimaryContainer 
                        else 
                            MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = result.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (result.success) 
                            MaterialTheme.colorScheme.onPrimaryContainer 
                        else 
                            MaterialTheme.colorScheme.onErrorContainer
                    )
                    if (result.details.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Detalhes:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (result.success) 
                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            else 
                                MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                        )
                        result.details.forEach { detail ->
                            Text(
                                text = "  • $detail",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (result.success) 
                                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                else 
                                    MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f),
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            if (errorMessage != null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = errorMessage ?: "Erro desconhecido",
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(16.dp)
                        )
                        Text(
                            text = "Verifique os logs para mais detalhes",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                        )
                    }
                }
            } else if (expenses.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Nenhuma despesa encontrada.",
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Verifique se há dados no banco e se o RLS está configurado corretamente.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(expenses) { expense ->
                        ExpenseItem(expense)
                    }
                }
            }
        }
    }
}

@Composable
fun ExpenseItem(expense: Expense) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = expense.estabelecimento ?: "Desconhecido",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = expense.categoria ?: "Outros",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            Text(
                text = formatCurrency(expense.valor ?: 0.0),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}


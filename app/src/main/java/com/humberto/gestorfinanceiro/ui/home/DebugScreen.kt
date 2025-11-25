package com.humberto.gestorfinanceiro.ui.home

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import com.humberto.gestorfinanceiro.data.log.ActivityLogManager
import com.humberto.gestorfinanceiro.data.settings.SettingsManager
import com.humberto.gestorfinanceiro.data.supabase.ConnectionTestResult
import com.humberto.gestorfinanceiro.di.Dependencies
import com.humberto.gestorfinanceiro.utils.NotificationHelper
import kotlinx.coroutines.launch
import org.json.JSONObject

private const val TAG = "DebugScreen"

@Composable
fun LogEntryItem(entry: ActivityLogManager.ActivityLogEntry) {
    val backgroundColor = when (entry.tipoAtividade) {
        "sms_config" -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
        "connection_test" -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
        "sms_capture" -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        "llm_request" -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
        "llm_response" -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
        "db_insert" -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    
    val icon = when (entry.tipoAtividade) {
        "sms_config" -> Icons.Default.Settings
        "connection_test" -> Icons.Default.Refresh
        "sms_capture" -> Icons.Default.Info
        "llm_request" -> Icons.Default.Send
        "llm_response" -> Icons.Default.Done
        "db_insert" -> Icons.Default.Check
        else -> Icons.Default.Info
    }
    
    val tipoLabel = when (entry.tipoAtividade) {
        "sms_config" -> "Config SMS"
        "connection_test" -> "Teste Conexão"
        "sms_capture" -> "SMS Capturado"
        "llm_request" -> "LLM Request"
        "llm_response" -> "LLM Response"
        "db_insert" -> "DB Insert"
        else -> entry.tipoAtividade
    }
    
    Card(
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (entry.sucesso) 
                            MaterialTheme.colorScheme.primary 
                        else 
                            MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = tipoLabel,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (!entry.sucesso) {
                        Text(
                            text = "✗",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                Text(
                    text = entry.timestamp,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            
            entry.descricao?.let { desc ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            
            entry.dados?.let { dados ->
                val parsedData = remember(dados) {
                    try {
                        val jsonObj = JSONObject(dados)
                        val keys = jsonObj.keys()
                        val keysList = mutableListOf<Pair<String, Any?>>()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            keysList.add(key to jsonObj.opt(key))
                        }
                        keysList
                    } catch (e: Exception) {
                        null
                    }
                }
                
                if (parsedData != null && parsedData.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Column {
                        parsedData.forEach { (key, value) ->
                            Text(
                                text = "  • $key: $value",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                modifier = Modifier.padding(vertical = 1.dp)
                            )
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Dados: $dados",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
            
            entry.erro?.let { erro ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Erro: $erro",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun DebugScreen() {
    val context = LocalContext.current
    var isTestingConnection by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<ConnectionTestResult?>(null) }
    val scope = rememberCoroutineScope()
    
    // Configurações SMS
    var smsSenderNumber by remember { mutableStateOf(SettingsManager.getSmsSenderNumber() ?: "") }
    var showSaveSuccess by remember { mutableStateOf(false) }
    
    // Teste LLM
    var testSmsText by remember { mutableStateOf("R$49,99 no bar do zé em 24/11/2025") }
    var isProcessingLlm by remember { mutableStateOf(false) }
    var llmTestResult by remember { mutableStateOf<String?>(null) }
    var llmTestError by remember { mutableStateOf<String?>(null) }
    
    // Logs em tempo real
    val logs by ActivityLogManager.logs.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Header
        Text(
            text = "Debug - Despesas",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        // Seção de Configurações SMS
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Configurações SMS",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                
                Text(
                    text = "Número do Remetente",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                OutlinedTextField(
                    value = smsSenderNumber,
                    onValueChange = { smsSenderNumber = it },
                    label = { Text("Número do banco (ex: 12345)") },
                    placeholder = { Text("Digite o número do remetente") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    singleLine = true
                )
                
                Text(
                    text = "Apenas SMS recebidos deste número serão processados. Deixe em branco para processar todos os SMS.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = {
                            val numberToSave = if (smsSenderNumber.isBlank()) null else smsSenderNumber.trim()
                            SettingsManager.setSmsSenderNumber(numberToSave)
                            showSaveSuccess = true
                            
                            // Log da configuração do número SMS
                            val configData = JSONObject().apply {
                                put("number", numberToSave ?: "")
                                put("action", if (numberToSave == null) "removed" else "saved")
                            }.toString()
                            
                            ActivityLogManager.addLog(
                                tipoAtividade = "sms_config",
                                descricao = if (numberToSave == null) 
                                    "Número SMS removido (processar todos os SMS)" 
                                else 
                                    "Número SMS configurado: $numberToSave",
                                dados = configData,
                                sucesso = true
                            )
                            
                            scope.launch {
                                kotlinx.coroutines.delay(2000)
                                showSaveSuccess = false
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Salvar",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Salvar")
                    }
                }
                
                if (showSaveSuccess) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "✓ Configuração salva com sucesso!",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                
                val currentNumber = SettingsManager.getSmsSenderNumber()
                if (!currentNumber.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Número atual: $currentNumber",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
        
        // Seção 2: Teste de Conexão
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Teste de Conexão",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Button(
                        onClick = {
                            scope.launch {
                                isTestingConnection = true
                                testResult = null
                                Log.d(TAG, "Iniciando teste de conexão...")
                                
                                // Log de início do teste
                                ActivityLogManager.addLog(
                                    tipoAtividade = "connection_test",
                                    descricao = "Iniciando teste de conexão com Supabase",
                                    sucesso = true
                                )
                                
                                val result = Dependencies.supabaseRepository.testConnection()
                                testResult = result
                                isTestingConnection = false
                                Log.d(TAG, "Teste de conexão concluído: ${result.message}")
                                
                                // Log do resultado do teste
                                val testData = JSONObject().apply {
                                    put("success", result.success)
                                    put("message", result.message)
                                    put("expenses_found", result.expensesFound)
                                    put("details_count", result.details.size)
                                }.toString()
                                
                                ActivityLogManager.addLog(
                                    tipoAtividade = "connection_test",
                                    descricao = "Teste de conexão concluído: ${result.message}",
                                    dados = testData,
                                    sucesso = result.success,
                                    erro = if (!result.success) result.message else null
                                )
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
            }
        }
        
        // Seção de Teste LLM (mantida, já gera logs) (mantida, já gera logs)
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Teste de LLM",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                
                Text(
                    text = "Texto do SMS para Teste",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                OutlinedTextField(
                    value = testSmsText,
                    onValueChange = { testSmsText = it },
                    label = { Text("Texto do SMS") },
                    placeholder = { Text("Digite o texto do SMS para testar") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    minLines = 3,
                    maxLines = 5
                )
                
                Text(
                    text = "Este teste processará o texto com a LLM e salvará o resultado no banco de dados.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = {
                            scope.launch {
                                isProcessingLlm = true
                                llmTestResult = null
                                llmTestError = null
                                
                                try {
                                    Log.d(TAG, "Iniciando teste de LLM com texto: $testSmsText")
                                    
                                    // Buscar categorias e subcategorias
                                    val subcategories = Dependencies.supabaseRepository.getSubcategoriesList()
                                    val categories = Dependencies.supabaseRepository.getCategoriesList()
                                    
                                    if (subcategories.isEmpty()) {
                                        llmTestError = "Nenhuma subcategoria encontrada no banco. Configure categorias primeiro."
                                        ActivityLogManager.addLog(
                                            tipoAtividade = "llm_request",
                                            descricao = "Teste LLM: Nenhuma subcategoria encontrada",
                                            sucesso = false,
                                            erro = "Nenhuma subcategoria encontrada no banco"
                                        )
                                        Log.w(TAG, "Nenhuma subcategoria encontrada")
                                    } else {
                                        // Log do teste iniciado
                                        ActivityLogManager.addLog(
                                            tipoAtividade = "llm_request",
                                            descricao = "Teste LLM iniciado",
                                            dados = JSONObject().apply {
                                                put("sms_text", testSmsText)
                                            }.toString(),
                                            sucesso = true
                                        )
                                        
                                        // Processar com LLM
                                        val expense = Dependencies.llmService.parseSms(testSmsText, subcategories, categories)
                                        
                                        if (expense != null) {
                                            // Salvar no banco
                                            Dependencies.supabaseRepository.saveExpense(expense)
                                            llmTestResult = "Despesa processada e salva com sucesso!\n" +
                                                    "Local: ${expense.local}\n" +
                                                    "Valor: R$ ${String.format("%.2f", expense.valor ?: 0.0)}\n" +
                                                    "Categoria: ${expense.categoria}\n" +
                                                    "Subcategoria: ${expense.subcategoria}\n" +
                                                    "Data: ${expense.dataDespesa}"
                                            Log.d(TAG, "Despesa salva com sucesso: ${expense.local}")
                                            
                                            // Exibir notificação
                                            NotificationHelper.showExpenseNotification(
                                                context = context,
                                                valor = expense.valor ?: 0.0,
                                                local = expense.local ?: "Desconhecido",
                                                categoria = expense.categoria,
                                                subcategoria = expense.subcategoria
                                            )
                                        } else {
                                            llmTestError = "A LLM não conseguiu processar o texto. Verifique os logs abaixo para mais detalhes."
                                            Log.w(TAG, "Falha ao processar SMS com LLM")
                                        }
                                    }
                                } catch (e: Exception) {
                                    llmTestError = "Erro ao processar: ${e.message}"
                                    Log.e(TAG, "Erro ao testar LLM", e)
                                    e.printStackTrace()
                                } finally {
                                    isProcessingLlm = false
                                }
                            }
                        },
                        enabled = !isProcessingLlm && testSmsText.isNotBlank()
                    ) {
                        if (isProcessingLlm) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Processar",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Processar e Salvar")
                        }
                    }
                }
                
                // Mostrar resultado do teste
                llmTestResult?.let { result ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text(
                                text = "✓ Sucesso",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = result,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
                
                llmTestError?.let { error ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text(
                                text = "✗ Erro",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
        }
        
        // Seção de Logs em Tempo Real
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Logs de Atividade",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    TextButton(
                        onClick = { ActivityLogManager.clearLogs() },
                        enabled = logs.isNotEmpty()
                    ) {
                        Text("Limpar")
                    }
                }
                
                if (logs.isEmpty()) {
                    Text(
                        text = "Nenhum log ainda. Os logs aparecerão aqui quando houver atividade de SMS, LLM ou inserção no banco.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(logs) { logEntry ->
                            LogEntryItem(logEntry)
                        }
                    }
                }
            }
        }
    }
}



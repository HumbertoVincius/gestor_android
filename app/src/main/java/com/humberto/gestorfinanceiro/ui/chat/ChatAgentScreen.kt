package com.humberto.gestorfinanceiro.ui.chat

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import com.humberto.gestorfinanceiro.data.llm.ChatContext
import com.humberto.gestorfinanceiro.data.model.ChatMessage
import com.humberto.gestorfinanceiro.data.model.Expense
import com.humberto.gestorfinanceiro.di.Dependencies
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.Calendar

private const val TAG = "ChatAgentScreen"

@Composable
fun ChatAgentScreen(
    onExpenseRegistered: (Expense) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    
    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var inputText by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }
    
    // Carregar contexto financeiro
    var chatContext by remember { mutableStateOf<ChatContext?>(null) }
    
    LaunchedEffect(Unit) {
        scope.launch {
            try {
                val calendar = Calendar.getInstance()
                val month = calendar.get(Calendar.MONTH) + 1
                val year = calendar.get(Calendar.YEAR)
                
                val expenses = Dependencies.supabaseRepository.getExpensesByMonthCached(month, year, com.humberto.gestorfinanceiro.data.model.SortOrder.DATE_DESC)
                val goals = Dependencies.supabaseRepository.getGoalsByMonthCached(month, year)
                val categories = Dependencies.supabaseRepository.getCategoriesListCached()
                val subcategories = Dependencies.supabaseRepository.getSubcategoriesListCached()
                
                val totalSpent = expenses.sumOf { it.valor ?: 0.0 }
                
                chatContext = ChatContext(
                    recentExpenses = expenses.take(10),
                    currentMonthGoals = goals,
                    categories = categories,
                    subcategories = subcategories,
                    totalSpentThisMonth = totalSpent,
                    month = month,
                    year = year
                )
                
                // Mensagem inicial do agente
                messages = listOf(
                    ChatMessage(
                        role = "assistant",
                        content = "Olá! Sou seu assistente financeiro. Posso ajudá-lo a:\n\n" +
                                "• Cadastrar despesas (ex: \"Gastei R$50 no supermercado hoje\")\n" +
                                "• Responder perguntas sobre seus gastos\n" +
                                "• Analisar suas despesas e metas\n\n" +
                                "Como posso ajudá-lo hoje?"
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao carregar contexto", e)
                messages = listOf(
                    ChatMessage(
                        role = "assistant",
                        content = "Olá! Sou seu assistente financeiro. Como posso ajudá-lo?"
                    )
                )
            }
        }
    }
    
    // Scroll automático para última mensagem
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            scope.launch {
                listState.animateScrollToItem(messages.size - 1)
            }
        }
    }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        // Lista de mensagens
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 16.dp,
                bottom = 8.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(messages) { message ->
                MessageBubble(message = message)
            }
            
            // Indicador de digitação
            if (isProcessing) {
                item {
                    TypingIndicator()
                }
            }
        }
        
        // Input e botão de enviar - sempre visível acima do teclado
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.ime),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp,
            tonalElevation = 8.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { if (!isProcessing && chatContext != null) inputText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Digite sua mensagem...") },
                    readOnly = isProcessing || chatContext == null,
                    maxLines = 4,
                    minLines = 1
                )
                
                FloatingActionButton(
                    onClick = {
                        if (inputText.isNotBlank() && !isProcessing && chatContext != null) {
                            val userMessage = inputText.trim()
                            inputText = ""
                            
                            // Adicionar mensagem do usuário
                            messages = messages + ChatMessage(
                                role = "user",
                                content = userMessage
                            )
                            
                            // Processar com LLM
                            scope.launch {
                                isProcessing = true
                                try {
                                    val response = Dependencies.llmService.chatWithAgent(
                                        userMessage = userMessage,
                                        context = chatContext!!,
                                        messageHistory = messages
                                    )
                                    
                                    // Verificar se a resposta contém ação de cadastro
                                    val jsonPattern = Regex("""\{[^{}]*"action"\s*:\s*"register_expense"[^{}]*\}""")
                                    val jsonMatch = jsonPattern.find(response)
                                    
                                    if (jsonMatch != null) {
                                        try {
                                            // Extrair JSON da resposta
                                            var jsonStr = jsonMatch.value
                                            
                                            // Tentar encontrar JSON completo (pode estar em múltiplas linhas)
                                            val jsonStart = response.indexOf("{")
                                            val jsonEnd = response.lastIndexOf("}")
                                            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                                                jsonStr = response.substring(jsonStart, jsonEnd + 1)
                                            }
                                            
                                            // Limpar markdown se presente
                                            jsonStr = jsonStr
                                                .removePrefix("```json")
                                                .removePrefix("```")
                                                .removeSuffix("```")
                                                .trim()
                                            
                                            val jsonObj = JSONObject(jsonStr)
                                            
                                            if (jsonObj.optString("action") == "register_expense") {
                                                val valor = jsonObj.optDouble("valor", 0.0)
                                                val dataDespesa = jsonObj.optString("data_despesa")
                                                val estabelecimento = jsonObj.optString("estabelecimento")
                                                val idSubcategoria = jsonObj.optString("id_subcategoria")
                                                
                                                if (valor > 0 && idSubcategoria.isNotBlank()) {
                                                    // Criar despesa
                                                    val expense = Expense(
                                                        valor = valor,
                                                        dataDespesa = if (dataDespesa.isNotBlank()) dataDespesa else {
                                                            val calendar = Calendar.getInstance()
                                                            "%d-%02d-%02d".format(
                                                                calendar.get(Calendar.YEAR),
                                                                calendar.get(Calendar.MONTH) + 1,
                                                                calendar.get(Calendar.DAY_OF_MONTH)
                                                            )
                                                        },
                                                        local = estabelecimento.ifBlank { null },
                                                        idSubcategoria = idSubcategoria
                                                    )
                                                    
                                                    // Salvar no banco
                                                    Dependencies.supabaseRepository.saveExpense(expense, context)
                                                    
                                                    // Notificar callback
                                                    onExpenseRegistered(expense)
                                                    
                                                    // Atualizar contexto
                                                    val calendar = Calendar.getInstance()
                                                    val month = calendar.get(Calendar.MONTH) + 1
                                                    val year = calendar.get(Calendar.YEAR)
                                                    val updatedExpenses = Dependencies.supabaseRepository.getExpensesByMonthCached(
                                                        month, year, com.humberto.gestorfinanceiro.data.model.SortOrder.DATE_DESC
                                                    )
                                                    val totalSpent = updatedExpenses.sumOf { it.valor ?: 0.0 }
                                                    
                                                    chatContext = chatContext!!.copy(
                                                        recentExpenses = updatedExpenses.take(10),
                                                        totalSpentThisMonth = totalSpent
                                                    )
                                                    
                                                    // Adicionar confirmação na resposta
                                                    val confirmation = "\n\n✅ Despesa cadastrada com sucesso!"
                                                    messages = messages + ChatMessage(
                                                        role = "assistant",
                                                        content = response + confirmation
                                                    )
                                                } else {
                                                    Log.w(TAG, "JSON de cadastro inválido: valor ou id_subcategoria faltando")
                                                    messages = messages + ChatMessage(
                                                        role = "assistant",
                                                        content = response
                                                    )
                                                }
                                            } else {
                                                messages = messages + ChatMessage(
                                                    role = "assistant",
                                                    content = response
                                                )
                                            }
                                        } catch (e: Exception) {
                                            Log.e(TAG, "Erro ao processar cadastro de despesa", e)
                                            messages = messages + ChatMessage(
                                                role = "assistant",
                                                content = response + "\n\n⚠️ Houve um erro ao cadastrar a despesa: ${e.message}. Tente novamente."
                                            )
                                        }
                                    } else {
                                        // Resposta normal
                                        messages = messages + ChatMessage(
                                            role = "assistant",
                                            content = response
                                        )
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Erro ao processar mensagem", e)
                                    messages = messages + ChatMessage(
                                        role = "assistant",
                                        content = "Desculpe, ocorreu um erro ao processar sua mensagem. Tente novamente."
                                    )
                                } finally {
                                    isProcessing = false
                                }
                            }
                        }
                    },
                    modifier = Modifier.size(56.dp),
                    containerColor = if (!isProcessing && inputText.isNotBlank() && chatContext != null) 
                        MaterialTheme.colorScheme.primary 
                    else 
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Enviar",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MessageBubble(message: ChatMessage) {
    val isUser = message.role == "user"
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Card(
            modifier = Modifier.widthIn(max = 280.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) 
                    MaterialTheme.colorScheme.primaryContainer 
                else 
                    MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = message.content,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = if (isUser)
                    MaterialTheme.colorScheme.onPrimaryContainer
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun TypingIndicator() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Card(
            modifier = Modifier.widthIn(max = 80.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                repeat(3) { _ ->
                    CircularProgressIndicator(
                        modifier = Modifier.size(8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        strokeWidth = 2.dp
                    )
                }
            }
        }
    }
}


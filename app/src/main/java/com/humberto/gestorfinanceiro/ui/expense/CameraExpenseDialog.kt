package com.humberto.gestorfinanceiro.ui.expense

import android.net.Uri
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.rememberAsyncImagePainter
import com.humberto.gestorfinanceiro.MainActivity
import com.humberto.gestorfinanceiro.data.model.Expense
import com.humberto.gestorfinanceiro.di.Dependencies
import kotlinx.coroutines.launch

private const val TAG = "CameraExpenseDialog"

@Composable
fun CameraExpenseDialog(
    imageUri: Uri?,
    onDismiss: () -> Unit,
    onExpenseRegistered: (Expense) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var isProcessing by remember { mutableStateOf(false) }
    var processingError by remember { mutableStateOf<String?>(null) }
    
    if (imageUri == null) {
        onDismiss()
        return
    }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Foto Capturada",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar")
                    }
                }
                
                Divider()
                
                // Preview da imagem
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Image(
                        painter = rememberAsyncImagePainter(imageUri),
                        contentDescription = "Foto capturada",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
                
                // Erro
                processingError?.let { error ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = "Erro: $error",
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
                
                // Botões
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        enabled = !isProcessing
                    ) {
                        Text("Cancelar")
                    }
                    
                    Button(
                        onClick = {
                            scope.launch {
                                isProcessing = true
                                processingError = null
                                
                                try {
                                    // Buscar categorias e subcategorias
                                    val subcategories = Dependencies.supabaseRepository.getSubcategoriesListCached()
                                    val categories = Dependencies.supabaseRepository.getCategoriesListCached()
                                    
                                    if (subcategories.isEmpty()) {
                                        processingError = "Nenhuma subcategoria encontrada. Configure categorias primeiro."
                                        isProcessing = false
                                        return@launch
                                    }
                                    
                                    // Processar imagem diretamente com LLM Vision
                                    val expense = Dependencies.llmService.processImageForExpense(
                                        imageUri,
                                        context,
                                        subcategories,
                                        categories
                                    )
                                    
                                    if (expense != null) {
                                        // Salvar no banco
                                        Dependencies.supabaseRepository.saveExpense(expense, context)
                                        
                                        // Notificar sucesso
                                        onExpenseRegistered(expense)
                                        onDismiss()
                                    } else {
                                        processingError = "Não foi possível processar os dados da foto. Verifique se a foto contém informações de despesa legíveis."
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Erro ao processar foto", e)
                                    processingError = "Erro ao processar foto: ${e.message}"
                                } finally {
                                    isProcessing = false
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !isProcessing
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Processando...")
                        } else {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Cadastrar")
                        }
                    }
                }
            }
        }
    }
}


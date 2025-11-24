package com.humberto.gestorfinanceiro.ui.categories

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.humberto.gestorfinanceiro.data.model.Category
import com.humberto.gestorfinanceiro.data.model.Subcategory
import com.humberto.gestorfinanceiro.di.Dependencies
import com.humberto.gestorfinanceiro.ui.home.normalizeText
import kotlinx.coroutines.launch

@Composable
fun AddCategoryDialog(
    onDismiss: () -> Unit,
    onCategoryAdded: () -> Unit
) {
    var categoryName by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Nova Categoria",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = categoryName,
                    onValueChange = { categoryName = it },
                    label = { Text("Nome da Categoria") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (categoryName.isNotBlank()) {
                                scope.launch {
                                    isSaving = true
                                    try {
                                        val newCategory = Category(nome = normalizeText(categoryName))
                                        Dependencies.supabaseRepository.createCategory(newCategory)
                                        onCategoryAdded()
                                    } catch (e: Exception) {
                                        // Tratar erro
                                    } finally {
                                        isSaving = false
                                    }
                                }
                            }
                        },
                        enabled = categoryName.isNotBlank() && !isSaving
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp))
                        } else {
                            Text("Adicionar")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AddSubcategoryDialog(
    category: Category,
    onDismiss: () -> Unit,
    onSubcategoryAdded: () -> Unit
) {
    var subcategoryName by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Nova Subcategoria",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Categoria: ${category.nome}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )

                OutlinedTextField(
                    value = subcategoryName,
                    onValueChange = { subcategoryName = it },
                    label = { Text("Nome da Subcategoria") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (subcategoryName.isNotBlank()) {
                                scope.launch {
                                    isSaving = true
                                    try {
                                        val newSubcategory = Subcategory(
                                            idCategoria = category.id,
                                            nome = normalizeText(subcategoryName)
                                        )
                                        Dependencies.supabaseRepository.createSubcategory(newSubcategory)
                                        onSubcategoryAdded()
                                    } catch (e: Exception) {
                                        // Tratar erro
                                    } finally {
                                        isSaving = false
                                    }
                                }
                            }
                        },
                        enabled = subcategoryName.isNotBlank() && !isSaving
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp))
                        } else {
                            Text("Adicionar")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EditCategoryDialog(
    category: Category,
    onDismiss: () -> Unit,
    onCategoryUpdated: () -> Unit
) {
    var categoryName by remember { mutableStateOf(category.nome ?: "") }
    var isSaving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Editar Categoria",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = categoryName,
                    onValueChange = { categoryName = it },
                    label = { Text("Nome da Categoria") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (categoryName.isNotBlank() && categoryName != category.nome) {
                                scope.launch {
                                    isSaving = true
                                    try {
                                        val updatedCategory = category.copy(nome = normalizeText(categoryName))
                                        Dependencies.supabaseRepository.updateCategoryObj(updatedCategory)
                                        onCategoryUpdated()
                                    } catch (e: Exception) {
                                        // Tratar erro
                                    } finally {
                                        isSaving = false
                                    }
                                }
                            }
                        },
                        enabled = categoryName.isNotBlank() && categoryName != category.nome && !isSaving
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp))
                        } else {
                            Text("Salvar")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EditSubcategoryDialog(
    subcategory: Subcategory,
    onDismiss: () -> Unit,
    onSubcategoryUpdated: () -> Unit
) {
    var subcategoryName by remember { mutableStateOf(subcategory.nome ?: "") }
    var isSaving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Editar Subcategoria",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = subcategoryName,
                    onValueChange = { subcategoryName = it },
                    label = { Text("Nome da Subcategoria") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (subcategoryName.isNotBlank() && subcategoryName != subcategory.nome) {
                                scope.launch {
                                    isSaving = true
                                    try {
                                        val updatedSubcategory = subcategory.copy(nome = normalizeText(subcategoryName))
                                        Dependencies.supabaseRepository.updateSubcategoryObj(updatedSubcategory)
                                        onSubcategoryUpdated()
                                    } catch (e: Exception) {
                                        // Tratar erro
                                    } finally {
                                        isSaving = false
                                    }
                                }
                            }
                        },
                        enabled = subcategoryName.isNotBlank() && subcategoryName != subcategory.nome && !isSaving
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp))
                        } else {
                            Text("Salvar")
                        }
                    }
                }
            }
        }
    }
}

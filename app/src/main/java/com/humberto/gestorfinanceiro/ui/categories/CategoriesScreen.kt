package com.humberto.gestorfinanceiro.ui.categories

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.humberto.gestorfinanceiro.data.model.Category
import com.humberto.gestorfinanceiro.data.model.Subcategory
import com.humberto.gestorfinanceiro.di.Dependencies
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen() {
    var categories by remember { mutableStateOf<List<Category>>(emptyList()) }
    var expandedCategories by remember { mutableStateOf(setOf<String>()) }
    var isLoading by remember { mutableStateOf(true) }
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var showAddSubcategoryDialog by remember { mutableStateOf(false) }
    var selectedCategoryForSubcategory by remember { mutableStateOf<Category?>(null) }
    var editingCategory by remember { mutableStateOf<Category?>(null) }
    var editingSubcategory by remember { mutableStateOf<Subcategory?>(null) }
    val scope = rememberCoroutineScope()

    fun loadCategories(forceRefresh: Boolean = false) {
        scope.launch {
            isLoading = true
            try {
                // ✅ Usar cache para performance
                categories = Dependencies.supabaseRepository.getCategoriesListCached(forceRefresh)
            } catch (e: Exception) {
                categories = emptyList()
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        loadCategories()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Categorias e Subcategorias") },
                actions = {
                    IconButton(onClick = { loadCategories(forceRefresh = true) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Atualizar")
                    }
                    IconButton(onClick = { showAddCategoryDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Adicionar categoria")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (categories.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Nenhuma categoria encontrada",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Button(onClick = { showAddCategoryDialog = true }) {
                        Text("Adicionar Primeira Categoria")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categories, key = { it.idCategoria ?: "" }) { category ->
                    CategoryCard(
                        category = category,
                        isExpanded = expandedCategories.contains(category.idCategoria),
                        onExpandedChange = { expanded ->
                            expandedCategories = if (expanded) {
                                expandedCategories + (category.idCategoria ?: "")
                            } else {
                                expandedCategories - (category.idCategoria ?: "")
                            }
                        },
                        onEditCategory = { editingCategory = category },
                        onDeleteCategory = {
                            scope.launch {
                                try {
                                    category.idCategoria?.let { id ->
                                        Dependencies.supabaseRepository.deleteCategoryObj(id)
                                        loadCategories()
                                    }
                                } catch (e: Exception) {
                                    // Tratar erro
                                }
                            }
                        },
                        onAddSubcategory = {
                            selectedCategoryForSubcategory = category
                            showAddSubcategoryDialog = true
                        },
                        onEditSubcategory = { sub ->
                            editingSubcategory = sub
                        },
                        onDeleteSubcategory = { sub ->
                            scope.launch {
                                try {
                                    sub.idSubcategoria?.let { id ->
                                        Dependencies.supabaseRepository.deleteSubcategoryObj(id)
                                        // Need to force reload of subcategories in the card.
                                        // Since we don't have a direct way, we can collapse/expand or pass a trigger.
                                        // For now, we'll rely on user interaction or reload categories (which doesn't help much unless card fetches again).
                                        // A simple hack is to toggle expansion.
                                        expandedCategories = expandedCategories - (category.idCategoria ?: "")
                                        kotlinx.coroutines.delay(100)
                                        expandedCategories = expandedCategories + (category.idCategoria ?: "")
                                    }
                                } catch (e: Exception) {
                                    // Tratar erro
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    if (showAddCategoryDialog) {
        AddCategoryDialog(
            onDismiss = { showAddCategoryDialog = false },
            onCategoryAdded = {
                showAddCategoryDialog = false
                loadCategories()
            }
        )
    }

    if (showAddSubcategoryDialog && selectedCategoryForSubcategory != null) {
        AddSubcategoryDialog(
            category = selectedCategoryForSubcategory!!,
            onDismiss = {
                showAddSubcategoryDialog = false
                selectedCategoryForSubcategory = null
            },
            onSubcategoryAdded = {
                showAddSubcategoryDialog = false
                // Reload subcategories by toggling expansion
                val catId = selectedCategoryForSubcategory?.idCategoria
                selectedCategoryForSubcategory = null
                if (catId != null) {
                    scope.launch {
                        expandedCategories = expandedCategories - catId
                        kotlinx.coroutines.delay(100)
                        expandedCategories = expandedCategories + catId
                    }
                }
            }
        )
    }

    editingCategory?.let { category ->
        EditCategoryDialog(
            category = category,
            onDismiss = { editingCategory = null },
            onCategoryUpdated = {
                editingCategory = null
                loadCategories()
            }
        )
    }

    editingSubcategory?.let { subcategory ->
        EditSubcategoryDialog(
            subcategory = subcategory,
            onDismiss = { editingSubcategory = null },
            onSubcategoryUpdated = {
                editingSubcategory = null
                // Trigger update by toggling expansion of parent category
                // We need the parent category ID. Subcategory has idCategoria.
                val catId = subcategory.idCategoria
                if (catId != null) {
                    scope.launch {
                        expandedCategories = expandedCategories - catId
                        kotlinx.coroutines.delay(100)
                        expandedCategories = expandedCategories + catId
                    }
                }
            }
        )
    }
}

@Composable
fun CategoryCard(
    category: Category,
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onEditCategory: () -> Unit,
    onDeleteCategory: () -> Unit,
    onAddSubcategory: () -> Unit,
    onEditSubcategory: (Subcategory) -> Unit,
    onDeleteSubcategory: (Subcategory) -> Unit
) {
    var subcategories by remember { mutableStateOf<List<Subcategory>>(emptyList()) }
    var isLoadingSubcategories by remember { mutableStateOf(false) }
    var goalValue by remember { mutableStateOf("") }
    var isLoadingGoal by remember { mutableStateOf(false) }
    var isSavingGoal by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    
    // Obter mês e ano atual
    val calendar = java.util.Calendar.getInstance()
    val currentMonth = calendar.get(java.util.Calendar.MONTH) + 1
    val currentYear = calendar.get(java.util.Calendar.YEAR)

    LaunchedEffect(category.idCategoria) {
        // Carregar meta atual
        if (category.idCategoria != null) {
            isLoadingGoal = true
            scope.launch {
                try {
                    val goal = Dependencies.supabaseRepository.getGoalByCategory(
                        category.idCategoria, 
                        currentMonth, 
                        currentYear
                    )
                    goalValue = goal?.valorMeta?.toString() ?: ""
                } catch (e: Exception) {
                    goalValue = ""
                } finally {
                    isLoadingGoal = false
                }
            }
        }
    }

    LaunchedEffect(category.idCategoria, isExpanded) {
        if (isExpanded && category.idCategoria != null) {
            isLoadingSubcategories = true
            scope.launch {
                try {
                    // ✅ Usar cache
                    subcategories = Dependencies.supabaseRepository.getSubcategoriesListCached(category.idCategoria)
                } catch (e: Exception) {
                    subcategories = emptyList()
                } finally {
                    isLoadingSubcategories = false
                }
            }
        }
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // LINHA 1: Nome da categoria + Botões de ação
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = category.nomeCategoria ?: "Sem nome",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = onEditCategory,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Default.Edit, 
                            contentDescription = "Editar categoria",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = onDeleteCategory,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete, 
                            contentDescription = "Deletar categoria",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                    IconButton(
                        onClick = { onExpandedChange(!isExpanded) },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (isExpanded) "Recolher" else "Expandir",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
            
            // LINHA 2: Meta mensal
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = goalValue,
                    onValueChange = { goalValue = it },
                    label = { Text("Meta Mensal") },
                    placeholder = { Text("0,00") },
                    leadingIcon = {
                        Text(
                            text = "R$",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    enabled = !isLoadingGoal && !isSavingGoal,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )
                
                IconButton(
                    onClick = {
                        scope.launch {
                            val value = goalValue.toDoubleOrNull()
                            if (value != null && value > 0 && category.idCategoria != null) {
                                isSavingGoal = true
                                try {
                                    Dependencies.supabaseRepository.upsertGoal(
                                        category.idCategoria,
                                        value,
                                        currentMonth,
                                        currentYear
                                    )
                                } catch (e: Exception) {
                                    // Tratar erro
                                } finally {
                                    isSavingGoal = false
                                }
                            }
                        }
                    },
                    enabled = !isSavingGoal && goalValue.toDoubleOrNull() != null,
                    modifier = Modifier.size(48.dp),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = if (goalValue.toDoubleOrNull() != null) 
                            MaterialTheme.colorScheme.primaryContainer 
                        else 
                            MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    if (isSavingGoal) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    } else {
                        Icon(
                            Icons.Default.Check, 
                            contentDescription = "Salvar meta",
                            tint = if (goalValue.toDoubleOrNull() != null) 
                                MaterialTheme.colorScheme.onPrimaryContainer 
                            else 
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // SEÇÃO EXPANSÍVEL: Subcategorias
            if (isExpanded) {
                Divider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Header de Subcategorias
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Subcategorias",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Button(
                            onClick = onAddSubcategory,
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                Icons.Default.Add, 
                                contentDescription = null, 
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Adicionar")
                        }
                    }

                    // Lista de Subcategorias
                    if (isLoadingSubcategories) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else if (subcategories.isEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Text(
                                text = "Nenhuma subcategoria cadastrada",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            subcategories.forEach { subcategory ->
                                SubcategoryItem(
                                    subcategory = subcategory,
                                    onEdit = { onEditSubcategory(subcategory) },
                                    onDelete = { onDeleteSubcategory(subcategory) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SubcategoryItem(
    subcategory: Subcategory,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
        ),
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    Icons.Default.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f)
                )
                Text(
                    text = subcategory.nomeSubcategoria ?: "Sem nome",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(
                    onClick = onEdit, 
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Edit, 
                        contentDescription = "Editar",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(
                    onClick = onDelete, 
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Delete, 
                        contentDescription = "Deletar",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

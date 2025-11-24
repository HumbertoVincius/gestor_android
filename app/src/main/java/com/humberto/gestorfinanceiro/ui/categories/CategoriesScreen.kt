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

    fun loadCategories() {
        scope.launch {
            isLoading = true
            try {
                categories = Dependencies.supabaseRepository.getCategoriesList()
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
                    IconButton(onClick = { showAddCategoryDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Adicionar categoria")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddCategoryDialog = true }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Adicionar categoria")
            }
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
    val scope = rememberCoroutineScope()

    LaunchedEffect(category.idCategoria, isExpanded) {
        if (isExpanded && category.idCategoria != null) {
            isLoadingSubcategories = true
            scope.launch {
                try {
                    subcategories = Dependencies.supabaseRepository.getSubcategoriesList(category.idCategoria)
                } catch (e: Exception) {
                    subcategories = emptyList()
                } finally {
                    isLoadingSubcategories = false
                }
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = category.nomeCategoria ?: "Sem nome",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Row {
                    IconButton(onClick = onEditCategory) {
                        Icon(Icons.Default.Edit, contentDescription = "Editar categoria")
                    }
                    IconButton(onClick = onDeleteCategory) {
                        Icon(Icons.Default.Delete, contentDescription = "Deletar categoria")
                    }
                    IconButton(onClick = { onExpandedChange(!isExpanded) }) {
                        Icon(
                            if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (isExpanded) "Recolher" else "Expandir"
                        )
                    }
                }
            }

            if (isExpanded) {
                Divider()
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Subcategorias",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        TextButton(onClick = onAddSubcategory) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Adicionar")
                        }
                    }

                    if (isLoadingSubcategories) {
                        CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                    } else if (subcategories.isEmpty()) {
                        Text(
                            text = "Nenhuma subcategoria",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
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

@Composable
fun SubcategoryItem(
    subcategory: Subcategory,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = subcategory.nomeSubcategoria ?: "Sem nome",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Row {
            IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Edit, contentDescription = "Editar", modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "Deletar", modifier = Modifier.size(18.dp))
            }
        }
    }
}

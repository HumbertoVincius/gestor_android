package com.humberto.gestorfinanceiro.ui.home

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import android.net.Uri
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import com.humberto.gestorfinanceiro.MainActivity
import com.humberto.gestorfinanceiro.data.model.Expense
import com.humberto.gestorfinanceiro.data.model.SortOrder
import com.humberto.gestorfinanceiro.di.Dependencies
import com.humberto.gestorfinanceiro.ui.expense.CameraExpenseDialog
import com.humberto.gestorfinanceiro.ui.home.formatCurrency
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import java.util.Locale
import java.util.UUID

private const val TAG = "HomeScreen"

data class SelectedDate(val year: Int, val month: Int, val day: Int)

fun getMonthYearString(month: Int, year: Int): String {
    val monthNames = arrayOf(
        "Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho",
        "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro"
    )
    return "${monthNames[month - 1]} $year"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(expandedCategory: String? = null) {
    val calendar = Calendar.getInstance()
    var selectedMonth by remember { mutableIntStateOf(calendar.get(Calendar.MONTH) + 1) }
    var selectedYear by remember { mutableIntStateOf(calendar.get(Calendar.YEAR)) }
    var expenses by remember { mutableStateOf<List<Expense>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var sortOrder by remember { mutableStateOf(SortOrder.DATE_DESC) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var editingExpense by remember { mutableStateOf<Expense?>(null) }
    var searchQuery by remember { mutableStateOf(expandedCategory ?: "") }
    var showOnlyUnseen by remember { mutableStateOf(false) }
    var capturedImageUri by remember { mutableStateOf<Uri?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    fun markAsSeen(expenseId: String) {
        // Optimistic update: atualiza a lista localmente instantaneamente
        expenses = expenses.map { 
            if (it.idDespesa == expenseId) it.copy(visto = true) else it 
        }
        
        // Envia para o banco em background
        scope.launch {
            Dependencies.supabaseRepository.markExpenseAsSeen(expenseId)
        }
    }

    fun loadExpenses(forceRefresh: Boolean = false) {
        scope.launch {
            isLoading = true
            try {
                // ✅ Usar cache para performance
                val fetchedExpenses = Dependencies.supabaseRepository.getExpensesByMonthCached(
                    selectedMonth,
                    selectedYear,
                    sortOrder,
                    forceRefresh
                )
                expenses = fetchedExpenses
                Log.d(TAG, "Despesas carregadas: ${fetchedExpenses.size}")
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao carregar despesas", e)
                expenses = emptyList()
            } finally {
                isLoading = false
            }
        }
    }

    fun deleteExpense(expenseId: String) {
        scope.launch {
            try {
                isLoading = true
                Dependencies.supabaseRepository.deleteExpense(expenseId)
                loadExpenses()
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao deletar despesa", e)
                isLoading = false
            }
        }
    }

    LaunchedEffect(selectedMonth, selectedYear, sortOrder) {
        loadExpenses()
    }

    // Filtrar despesas baseado na busca e filtro de não vistos
    val filteredExpenses = remember(expenses, searchQuery, showOnlyUnseen) {
        var list = expenses
        
        if (showOnlyUnseen) {
            list = list.filter { it.visto == false }
        }

        if (searchQuery.isNotBlank()) {
            val query = searchQuery.lowercase(Locale.getDefault())
            list = list.filter { expense ->
                expense.local?.lowercase(Locale.getDefault())?.contains(query) == true ||
                expense.categoria?.lowercase(Locale.getDefault())?.contains(query) == true ||
                expense.subcategoria?.lowercase(Locale.getDefault())?.contains(query) == true ||
                expense.valor?.toString()?.contains(query) == true ||
                formatCurrency(expense.valor ?: 0.0).lowercase(Locale.getDefault()).contains(query)
            }
        }
        list
    }

    val groupedExpenses = remember(filteredExpenses) {
        groupExpensesByDay(filteredExpenses)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("Despesas")
                        Text(
                            text = getMonthYearString(selectedMonth, selectedYear),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                },
                actions = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                if (selectedMonth == 1) {
                                    selectedMonth = 12
                                    selectedYear--
                                } else {
                                    selectedMonth--
                                }
                            }
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Mês anterior")
                        }
                        IconButton(
                            onClick = {
                                if (selectedMonth == 12) {
                                    selectedMonth = 1
                                    selectedYear++
                                } else {
                                    selectedMonth++
                                }
                            }
                        ) {
                            Icon(Icons.Default.ArrowForward, contentDescription = "Próximo mês")
                        }
                        IconButton(
                            onClick = { loadExpenses(forceRefresh = true) }
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Atualizar")
                        }
                        SortOrderSelector(
                            currentSort = sortOrder,
                            onSortChanged = { sortOrder = it }
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true }
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Adicionar despesa"
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // Campo de busca e Filtro
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Buscar despesas") },
                    placeholder = { Text("Valor, categoria, etc") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Limpar")
                            }
                        }
                    }
                )
                
                // Filtro de Não Vistos
                FilledIconToggleButton(
                    checked = showOnlyUnseen,
                    onCheckedChange = { showOnlyUnseen = it },
                    colors = IconButtonDefaults.iconToggleButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        checkedContainerColor = MaterialTheme.colorScheme.primary,
                        checkedContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    if (showOnlyUnseen) {
                        Icon(Icons.Default.Info, contentDescription = "Mostrar todas")
                    } else {
                        Icon(Icons.Default.Info, contentDescription = "Mostrar apenas novas")
                    }
                }
            }

            // Card de resumo do mês
            MonthlySummaryCard(
                total = calculateMonthTotal(filteredExpenses),
                expenseCount = filteredExpenses.size,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Lista de despesas agrupadas por dia
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (expenses.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Nenhuma despesa encontrada para este mês.",
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    groupedExpenses.forEach { (date, dayExpenses) ->
                        item(key = date) {
                            DayHeader(
                                date = date,
                                transactionCount = dayExpenses.size,
                                total = calculateDayTotal(dayExpenses),
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                        items(dayExpenses, key = { it.idDespesa ?: UUID.randomUUID().toString() }) { expense ->
                            ExpenseListItem(
                                expense = expense,
                                onEditClick = {
                                    editingExpense = expense
                                },
                                onDeleteClick = { 
                                    expense.idDespesa?.let { id -> deleteExpense(id) }
                                },
                                onMarkAsSeen = {
                                    expense.idDespesa?.let { id -> markAsSeen(id) }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Dialog de criação
    // Dialog de câmera
    capturedImageUri?.let { uri ->
        CameraExpenseDialog(
            imageUri = uri,
            onDismiss = { capturedImageUri = null },
            onExpenseRegistered = { expense ->
                loadExpenses(forceRefresh = true)
                capturedImageUri = null
            }
        )
    }
    
    if (showCreateDialog) {
        CreateExpenseDialog(
            onDismiss = { showCreateDialog = false },
            onExpenseCreated = {
                showCreateDialog = false
                loadExpenses()
            },
            defaultMonth = selectedMonth,
            defaultYear = selectedYear
        )
    }
    
    // Dialog de edição
    editingExpense?.let { expense ->
        EditExpenseDialog(
            expense = expense,
            onDismiss = { editingExpense = null },
            onExpenseUpdated = {
                editingExpense = null
                loadExpenses()
            },
            defaultMonth = selectedMonth,
            defaultYear = selectedYear
        )
    }
}

@Composable
fun DayHeader(
    date: String,
    transactionCount: Int,
    total: Double,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = formatDate(date),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "$transactionCount transação(ões)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            Text(
                text = formatCurrency(total),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ExpenseListItem(
    expense: Expense,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onMarkAsSeen: () -> Unit
) {
    val isUnseen = expense.visto == false
    
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isUnseen) 
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) 
            else 
                MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (isUnseen) {
                    onMarkAsSeen()
                }
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isUnseen) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(Color(0xFF4CAF50), CircleShape) // Verde vibrante
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    
                    Text(
                        text = expense.local ?: "Desconhecido",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (isUnseen) FontWeight.ExtraBold else FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                
                val categoryText = buildString {
                    append(expense.categoria ?: "Outros")
                    if (!expense.subcategoria.isNullOrBlank()) {
                        append(" • ${expense.subcategoria}")
                    }
                }
                Text(
                    text = categoryText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatCurrency(expense.valor ?: 0.0),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                IconButton(onClick = onEditClick) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Editar despesa",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                
                IconButton(onClick = onDeleteClick) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Deletar despesa",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
fun MonthSelector(
    month: Int,
    year: Int,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        IconButton(onClick = onPreviousMonth) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "Mês anterior"
            )
        }
        Text(
            text = "%02d/%d".format(month, year),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        IconButton(onClick = onNextMonth) {
            Icon(
                imageVector = Icons.Default.ArrowForward,
                contentDescription = "Próximo mês"
            )
        }
    }
}

@Composable
fun MonthlySummaryCard(
    total: Double,
    expenseCount: Int,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Total do Mês",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = formatCurrency(total),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = "$expenseCount despesa(s)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun SortOrderSelector(
    currentSort: SortOrder,
    onSortChanged: (SortOrder) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        TextButton(onClick = { expanded = true }) {
            Text(
                text = when (currentSort) {
                    SortOrder.DATE_DESC -> "Data ↓"
                    SortOrder.DATE_ASC -> "Data ↑"
                    SortOrder.VALUE_DESC -> "Valor ↓"
                    SortOrder.VALUE_ASC -> "Valor ↑"
                    SortOrder.NAME_ASC -> "Nome ↑"
                    SortOrder.NAME_DESC -> "Nome ↓"
                    SortOrder.CATEGORY_ASC -> "Categoria ↑"
                    SortOrder.CATEGORY_DESC -> "Categoria ↓"
                },
                style = MaterialTheme.typography.bodyMedium
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("Data ↓") },
                onClick = {
                    onSortChanged(SortOrder.DATE_DESC)
                    expanded = false
                }
            )
            DropdownMenuItem(
                text = { Text("Data ↑") },
                onClick = {
                    onSortChanged(SortOrder.DATE_ASC)
                    expanded = false
                }
            )
            DropdownMenuItem(
                text = { Text("Valor ↓") },
                onClick = {
                    onSortChanged(SortOrder.VALUE_DESC)
                    expanded = false
                }
            )
            DropdownMenuItem(
                text = { Text("Valor ↑") },
                onClick = {
                    onSortChanged(SortOrder.VALUE_ASC)
                    expanded = false
                }
            )
            DropdownMenuItem(
                text = { Text("Nome ↑") },
                onClick = {
                    onSortChanged(SortOrder.NAME_ASC)
                    expanded = false
                }
            )
            DropdownMenuItem(
                text = { Text("Nome ↓") },
                onClick = {
                    onSortChanged(SortOrder.NAME_DESC)
                    expanded = false
                }
            )
            DropdownMenuItem(
                text = { Text("Categoria ↑") },
                onClick = {
                    onSortChanged(SortOrder.CATEGORY_ASC)
                    expanded = false
                }
            )
            DropdownMenuItem(
                text = { Text("Categoria ↓") },
                onClick = {
                    onSortChanged(SortOrder.CATEGORY_DESC)
                    expanded = false
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("UNUSED_PARAMETER")
fun CreateExpenseDialog(
    onDismiss: () -> Unit,
    onExpenseCreated: () -> Unit,
    defaultMonth: Int,
    defaultYear: Int
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val calendar = Calendar.getInstance()
    var selectedDate by remember { 
        mutableStateOf(
            SelectedDate(
                year = calendar.get(Calendar.YEAR),
                month = calendar.get(Calendar.MONTH) + 1,
                day = calendar.get(Calendar.DAY_OF_MONTH)
            )
        )
    }
    var showDatePicker by remember { mutableStateOf(false) }
    
    var valor by remember { mutableStateOf("") }
    var local by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var selectedSubcategory by remember { mutableStateOf<String?>(null) }
    
    var categories by remember { mutableStateOf<List<String>>(emptyList()) }
    var subcategories by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoadingCategories by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var capturedImageUri by remember { mutableStateOf<Uri?>(null) }
    val scope = rememberCoroutineScope()
    
    // Formatar data para exibição
    val dateFormatter = remember {
        SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
    }
    
    val selectedDateString = remember(selectedDate) {
        val date = Calendar.getInstance().apply {
            set(selectedDate.year, selectedDate.month - 1, selectedDate.day)
        }.time
        dateFormatter.format(date)
    }

    LaunchedEffect(Unit) {
        scope.launch {
            try {
                android.util.Log.d(TAG, "Iniciando carregamento de categorias...")
                // Forçar refresh na primeira vez para garantir que temos dados
                categories = Dependencies.supabaseRepository.getUniqueCategoriesCached(forceRefresh = true)
                android.util.Log.d(TAG, "Categorias carregadas: ${categories.size}")
                
                if (categories.isEmpty()) {
                    android.util.Log.w(TAG, "Nenhuma categoria encontrada, tentando novamente sem cache...")
                    // Tentar novamente sem forçar refresh (pode estar no cache de outra tela)
                    categories = Dependencies.supabaseRepository.getUniqueCategoriesCached(forceRefresh = false)
                    android.util.Log.d(TAG, "Categorias após segunda tentativa: ${categories.size}")
                }
                
                val allSubcategories = Dependencies.supabaseRepository.getSubcategoriesListCached(forceRefresh = true)
                android.util.Log.d(TAG, "Subcategorias carregadas: ${allSubcategories.size}")
                
                subcategories = allSubcategories
                    .mapNotNull { it.nomeSubcategoria }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .sorted()
                
                android.util.Log.d(TAG, "Subcategorias únicas: ${subcategories.size}")
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Erro ao carregar categorias/subcategorias", e)
                e.printStackTrace()
                categories = emptyList()
                subcategories = emptyList()
            } finally {
                isLoadingCategories = false
                android.util.Log.d(TAG, "Carregamento finalizado. Categorias: ${categories.size}, Subcategorias: ${subcategories.size}")
            }
        }
    }

    LaunchedEffect(selectedCategory) {
        // Filtrar subcategorias baseado na categoria selecionada
        scope.launch {
            try {
                if (selectedCategory != null) {
                    // ✅ Buscar do cache
                    subcategories = Dependencies.supabaseRepository.getUniqueSubcategoriesCached(selectedCategory)
                } else {
                    // Se não há categoria selecionada, mostrar todas do cache
                    val allSubcategories = Dependencies.supabaseRepository.getSubcategoriesListCached()
                    subcategories = allSubcategories
                        .mapNotNull { it.nomeSubcategoria }
                        .filter { it.isNotBlank() }
                        .distinct()
                        .sorted()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao carregar subcategorias", e)
                subcategories = emptyList()
            }
        }
    }

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
                    text = "Nova Despesa",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = valor,
                    onValueChange = { valor = it },
                    label = { Text("Valor") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )

                OutlinedTextField(
                    value = local,
                    onValueChange = { local = it },
                    label = { Text("Local/Estabelecimento") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Campo de data
                OutlinedTextField(
                    value = selectedDateString,
                    onValueChange = {},
                    label = { Text("Data") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDatePicker = true },
                    readOnly = true,
                    trailingIcon = {
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(
                                imageVector = Icons.Default.ArrowForward,
                                contentDescription = "Selecionar data"
                            )
                        }
                    }
                )

                var categoryExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = categoryExpanded,
                    onExpandedChange = { categoryExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedCategory ?: "",
                        onValueChange = {},
                        label = { Text("Categoria") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        readOnly = true,
                        enabled = !isLoadingCategories && categories.isNotEmpty(),
                        trailingIcon = { 
                            if (isLoadingCategories) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            } else {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded)
                            }
                        },
                        placeholder = {
                            if (isLoadingCategories) {
                                Text("Carregando categorias...")
                            } else if (categories.isEmpty()) {
                                Text("Nenhuma categoria disponível")
                            } else {
                                Text("Selecione uma categoria")
                            }
                        }
                    )
                    ExposedDropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false }
                    ) {
                        if (isLoadingCategories) {
                            DropdownMenuItem(
                                text = { 
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                                        Text("Carregando...")
                                    }
                                },
                                onClick = {},
                                enabled = false
                            )
                        } else if (categories.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("Nenhuma categoria disponível") },
                                onClick = {},
                                enabled = false
                            )
                        } else {
                            categories.forEach { category ->
                                DropdownMenuItem(
                                    text = { Text(category) },
                                    onClick = {
                                        if (selectedCategory != category) {
                                            selectedSubcategory = null
                                        }
                                        selectedCategory = category
                                        categoryExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                var subcategoryExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = subcategoryExpanded,
                    onExpandedChange = { subcategoryExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedSubcategory ?: "",
                        onValueChange = {},
                        label = { Text("Subcategoria") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        readOnly = true,
                        enabled = subcategories.isNotEmpty(),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = subcategoryExpanded) },
                        placeholder = if (subcategories.isEmpty()) {
                            { Text(if (selectedCategory == null) "Selecione uma categoria primeiro" else "Nenhuma subcategoria disponível") }
                        } else null
                    )
                    ExposedDropdownMenu(
                        expanded = subcategoryExpanded,
                        onDismissRequest = { subcategoryExpanded = false }
                    ) {
                        if (subcategories.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text(if (selectedCategory == null) "Selecione uma categoria primeiro" else "Nenhuma subcategoria disponível") },
                                onClick = { subcategoryExpanded = false },
                                enabled = false
                            )
                        } else {
                            subcategories.forEach { subcategory ->
                                DropdownMenuItem(
                                    text = { Text(subcategory) },
                                    onClick = {
                                        selectedSubcategory = subcategory
                                        subcategoryExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Botão de câmera
                    OutlinedButton(
                        onClick = {
                            val mainActivity = MainActivity.getMainActivity(context)
                            if (mainActivity != null) {
                                // Verificar permissão de câmera
                                if (ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.CAMERA
                                    ) == PackageManager.PERMISSION_GRANTED
                                ) {
                                    mainActivity.takePicture { uri ->
                                        capturedImageUri = uri
                                    }
                                } else {
                                    mainActivity.requestCameraPermission {
                                        mainActivity.takePicture { uri ->
                                            capturedImageUri = uri
                                        }
                                    }
                                }
                            }
                        },
                        enabled = !isSaving
                    ) {
                        Icon(
                            Icons.Default.Camera,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Câmera")
                    }
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("Cancelar")
                        }
                        IconButton(
                            onClick = {
                                // Prevenir múltiplos cliques
                                if (isSaving) {
                                    android.util.Log.d(TAG, "Já está salvando, ignorando clique")
                                    return@IconButton
                                }
                                
                                android.util.Log.d(TAG, "Botão salvar clicado")
                                android.util.Log.d(TAG, "Valor: $valor, Local: $local, Categoria: $selectedCategory, Subcategoria: $selectedSubcategory")
                                
                                // Validar campos antes de processar
                                if (valor.isBlank() || local.isBlank() || 
                                    selectedCategory == null || selectedSubcategory == null) {
                                    android.util.Log.d(TAG, "Campos inválidos")
                                    return@IconButton
                                }
                                
                                android.util.Log.d(TAG, "Todos os campos válidos, iniciando salvamento")
                                
                                scope.launch {
                                    // Marcar como salvando ANTES de iniciar a corrotina
                                    isSaving = true
                                    
                                    try {
                                        // Criar cópias locais para smart cast
                                        val categoria = selectedCategory
                                        val subcategoria = selectedSubcategory
                                        
                                        if (categoria == null || subcategoria == null) {
                                            android.util.Log.e(TAG, "Categoria e subcategoria são obrigatórias")
                                            return@launch
                                        }
                                        
                                        val valorDouble = valor.toDoubleOrNull() ?: 0.0
                                        val dataDespesa = "%d-%02d-%02d".format(
                                            selectedDate.year,
                                            selectedDate.month,
                                            selectedDate.day
                                        )
                                        
                                        android.util.Log.d(TAG, "Criando despesa: valor=$valorDouble, data=$dataDespesa, local=$local, categoria=$categoria, subcategoria=$subcategoria")
                                        
                                        // Buscar ID da subcategoria
                                        val subcategoriaId = Dependencies.supabaseRepository.getSubcategoryIdByName(
                                            categoria,
                                            subcategoria
                                        )
                                        
                                        if (subcategoriaId == null) {
                                            android.util.Log.e(TAG, "Subcategoria não encontrada: categoria=$categoria, subcategoria=$subcategoria")
                                            return@launch
                                        }
                                        
                                        android.util.Log.d(TAG, "ID da subcategoria encontrado: $subcategoriaId")
                                        
                                        val newExpense = Expense(
                                            valor = valorDouble,
                                            dataDespesa = dataDespesa,
                                            local = local.ifBlank { null },
                                            detalhe = null,
                                            idSubcategoria = subcategoriaId,
                                            // Campos derivados de JOINs
                                            categoria = categoria,
                                            subcategoria = subcategoria,
                                            visto = true // Despesas criadas manualmente são consideradas vistas
                                        )
                                        
                                        android.util.Log.d(TAG, "Salvando despesa no banco...")
                                        Dependencies.supabaseRepository.saveExpense(newExpense, context)
                                        android.util.Log.d(TAG, "Despesa salva com sucesso")
                                        
                                        // Resetar estado ANTES de chamar o callback
                                        isSaving = false
                                        
                                        // Chamar callback para fechar dialog e recarregar lista
                                        onExpenseCreated()
                                    } catch (e: Exception) {
                                        android.util.Log.e(TAG, "Erro ao criar despesa", e)
                                        e.printStackTrace()
                                        isSaving = false
                                    }
                                }
                            }
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Salvar",
                                    tint = if (valor.isNotBlank() && local.isNotBlank() && 
                                               selectedCategory != null && selectedSubcategory != null)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    
    // Dialog de câmera (dentro do CreateExpenseDialog)
    capturedImageUri?.let { uri ->
        CameraExpenseDialog(
            imageUri = uri,
            onDismiss = { capturedImageUri = null },
            onExpenseRegistered = { expense ->
                // Preencher campos automaticamente
                valor = String.format("%.2f", expense.valor ?: 0.0)
                local = expense.local ?: ""
                selectedCategory = expense.categoria
                // Buscar subcategoria correspondente
                scope.launch {
                    val allSubcategories = Dependencies.supabaseRepository.getSubcategoriesListCached()
                    val category = categories.find { it == expense.categoria }
                    if (category != null) {
                        val categoryId = Dependencies.supabaseRepository.getCategoriesListCached()
                            .find { it.nomeCategoria == category }?.idCategoria
                        if (categoryId != null) {
                            val subs = allSubcategories
                                .filter { it.idCategoria == categoryId }
                                .mapNotNull { it.nomeSubcategoria }
                                .distinct()
                                .sorted()
                            subcategories = subs
                            selectedSubcategory = expense.subcategoria
                        }
                    }
                }
                capturedImageUri = null
            }
        )
    }
    
    // DatePicker Dialog
    if (showDatePicker) {
        CustomDatePickerDialog(
            initialYear = selectedDate.year,
            initialMonth = selectedDate.month,
            initialDay = selectedDate.day,
            onDateSelected = { y, m, d ->
                selectedDate = SelectedDate(y, m, d)
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("UNUSED_PARAMETER")
fun EditExpenseDialog(
    expense: Expense,
    onDismiss: () -> Unit,
    onExpenseUpdated: () -> Unit,
    defaultMonth: Int,
    defaultYear: Int
) {
    val calendar = Calendar.getInstance()
    
    // Parse da data atual da despesa
    val expenseDate = remember(expense.dataDespesa) {
        val dateStr = expense.dataDespesa
        if (dateStr != null) {
            try {
                val parts = dateStr.split("-")
                if (parts.size >= 3) {
                    SelectedDate(
                        year = parts[0].toInt(),
                        month = parts[1].toInt(),
                        day = parts[2].toInt()
                    )
                } else {
                    SelectedDate(
                        year = calendar.get(Calendar.YEAR),
                        month = calendar.get(Calendar.MONTH) + 1,
                        day = calendar.get(Calendar.DAY_OF_MONTH)
                    )
                }
            } catch (e: Exception) {
                SelectedDate(
                    year = calendar.get(Calendar.YEAR),
                    month = calendar.get(Calendar.MONTH) + 1,
                    day = calendar.get(Calendar.DAY_OF_MONTH)
                )
            }
        } else {
            SelectedDate(
                year = calendar.get(Calendar.YEAR),
                month = calendar.get(Calendar.MONTH) + 1,
                day = calendar.get(Calendar.DAY_OF_MONTH)
            )
        }
    }
    
    var selectedDate by remember { mutableStateOf(expenseDate) }
    var showDatePicker by remember { mutableStateOf(false) }
    
    var valor by remember { mutableStateOf(expense.valor?.toString() ?: "") }
    var local by remember { mutableStateOf(expense.local ?: "") }
    var detalhe by remember { mutableStateOf(expense.detalhe ?: "") }
    var selectedCategory by remember { mutableStateOf<String?>(expense.categoria) }
    var selectedSubcategory by remember { mutableStateOf<String?>(expense.subcategoria) }
    
    var categories by remember { mutableStateOf<List<String>>(emptyList()) }
    var subcategories by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoadingCategories by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    
    val dateFormatter = remember {
        SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
    }
    
    val selectedDateString = remember(selectedDate) {
        val date = Calendar.getInstance().apply {
            set(selectedDate.year, selectedDate.month - 1, selectedDate.day)
        }.time
        dateFormatter.format(date)
    }

    LaunchedEffect(Unit) {
        scope.launch {
            try {
                // ✅ Usar cache
                categories = Dependencies.supabaseRepository.getUniqueCategoriesCached()
                if (selectedCategory != null) {
                    subcategories = Dependencies.supabaseRepository.getUniqueSubcategoriesCached(selectedCategory)
                } else {
                    val allSubcategories = Dependencies.supabaseRepository.getSubcategoriesListCached()
                    subcategories = allSubcategories
                        .mapNotNull { it.nomeSubcategoria }
                        .filter { it.isNotBlank() }
                        .distinct()
                        .sorted()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao carregar categorias/subcategorias", e)
            } finally {
                isLoadingCategories = false
            }
        }
    }

    LaunchedEffect(selectedCategory) {
        scope.launch {
            try {
                if (selectedCategory != null) {
                    // ✅ Usar cache
                    subcategories = Dependencies.supabaseRepository.getUniqueSubcategoriesCached(selectedCategory)
                } else {
                    val allSubcategories = Dependencies.supabaseRepository.getSubcategoriesListCached()
                    subcategories = allSubcategories
                        .mapNotNull { it.nomeSubcategoria }
                        .filter { it.isNotBlank() }
                        .distinct()
                        .sorted()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao carregar subcategorias", e)
                subcategories = emptyList()
            }
        }
    }

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
                    text = "Editar Despesa",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = valor,
                    onValueChange = { valor = it },
                    label = { Text("Valor") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )

                OutlinedTextField(
                    value = local,
                    onValueChange = { local = it },
                    label = { Text("Local/Estabelecimento") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = detalhe,
                    onValueChange = { detalhe = it },
                    label = { Text("Detalhe") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    maxLines = 3
                )

                OutlinedTextField(
                    value = selectedDateString,
                    onValueChange = {},
                    label = { Text("Data") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDatePicker = true },
                    readOnly = true,
                    trailingIcon = {
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(
                                imageVector = Icons.Default.ArrowForward,
                                contentDescription = "Selecionar data"
                            )
                        }
                    }
                )

                var categoryExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = categoryExpanded,
                    onExpandedChange = { categoryExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedCategory ?: "",
                        onValueChange = {},
                        label = { Text("Categoria") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) }
                    )
                    ExposedDropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false }
                    ) {
                        categories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category) },
                                onClick = {
                                    if (selectedCategory != category) {
                                        selectedSubcategory = null
                                    }
                                    selectedCategory = category
                                    categoryExpanded = false
                                }
                            )
                        }
                    }
                }

                var subcategoryExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = subcategoryExpanded,
                    onExpandedChange = { subcategoryExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedSubcategory ?: "",
                        onValueChange = {},
                        label = { Text("Subcategoria") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        readOnly = true,
                        enabled = subcategories.isNotEmpty(),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = subcategoryExpanded) },
                        placeholder = if (subcategories.isEmpty()) {
                            { Text(if (selectedCategory == null) "Selecione uma categoria primeiro" else "Nenhuma subcategoria disponível") }
                        } else null
                    )
                    ExposedDropdownMenu(
                        expanded = subcategoryExpanded,
                        onDismissRequest = { subcategoryExpanded = false }
                    ) {
                        if (subcategories.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text(if (selectedCategory == null) "Selecione uma categoria primeiro" else "Nenhuma subcategoria disponível") },
                                onClick = { subcategoryExpanded = false },
                                enabled = false
                            )
                        } else {
                            subcategories.forEach { subcategory ->
                                DropdownMenuItem(
                                    text = { Text(subcategory) },
                                    onClick = {
                                        selectedSubcategory = subcategory
                                        subcategoryExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            scope.launch {
                                isSaving = true
                                try {
                                    // Criar cópias locais para smart cast
                                    val categoria = selectedCategory
                                    val subcategoria = selectedSubcategory
                                    
                                    if (categoria == null || subcategoria == null) {
                                        Log.e(TAG, "Categoria e subcategoria são obrigatórias")
                                        isSaving = false
                                        return@launch
                                    }
                                    
                                    val valorDouble = valor.toDoubleOrNull() ?: 0.0
                                    val dataDespesa = "%d-%02d-%02d".format(
                                        selectedDate.year,
                                        selectedDate.month,
                                        selectedDate.day
                                    )
                                    
                                    // OTIMIZAÇÃO: Se o usuário NÃO mudou categoria e subcategoria, usar o ID original
                                    val subcategoriaId: String? = if (categoria == expense.categoria && subcategoria == expense.subcategoria) {
                                        Log.d(TAG, "Usando ID original da subcategoria: ${expense.idSubcategoria}")
                                        expense.idSubcategoria
                                    } else {
                                        // Buscar ID da subcategoria pelo nome
                                        Dependencies.supabaseRepository.getSubcategoryIdByName(
                                            categoria,
                                            subcategoria
                                        )
                                    }
                                    
                                    Log.d(TAG, "Atualização - Categoria: '$categoria', Sub: '$subcategoria' -> ID: $subcategoriaId")
                                    
                                    if (subcategoriaId == null) {
                                        Log.e(TAG, "ERRO: Subcategoria não encontrada no banco")
                                        isSaving = false
                                        return@launch
                                    }
                                    
                                    val updatedExpense = expense.copy(
                                        valor = valorDouble,
                                        dataDespesa = dataDespesa,
                                        local = local.ifBlank { null },
                                        detalhe = detalhe.ifBlank { null },
                                        idSubcategoria = subcategoriaId,
                                        // Campos derivados de JOINs
                                        categoria = categoria,
                                        subcategoria = subcategoria
                                    )
                                    
                                    Dependencies.supabaseRepository.updateExpense(updatedExpense)
                                    onExpenseUpdated()
                                } catch (e: Exception) {
                                    Log.e(TAG, "Erro ao atualizar despesa", e)
                                } finally {
                                    isSaving = false
                                }
                            }
                        },
                        enabled = !isSaving && valor.isNotBlank() && local.isNotBlank()
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text("Salvar")
                        }
                    }
                }
            }
        }
    }
    
    if (showDatePicker) {
        CustomDatePickerDialog(
            initialYear = selectedDate.year,
            initialMonth = selectedDate.month,
            initialDay = selectedDate.day,
            onDateSelected = { y, m, d ->
                selectedDate = SelectedDate(y, m, d)
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomDatePickerDialog(
    initialYear: Int,
    initialMonth: Int,
    initialDay: Int,
    onDateSelected: (Int, Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var currentYear by remember { mutableIntStateOf(initialYear) }
    var currentMonth by remember { mutableIntStateOf(initialMonth) }
    var selectedDay by remember { mutableIntStateOf(initialDay) }
    
    // Recalcular dias no mês quando mês/ano mudar
    val calendar = remember(currentYear, currentMonth) {
        Calendar.getInstance().apply {
            set(currentYear, currentMonth - 1, 1)
        }
    }
    
    val daysInMonth = remember(calendar) {
        calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
    }
    
    // Ajustar dia selecionado se for maior que os dias do mês
    LaunchedEffect(daysInMonth) {
        if (selectedDay > daysInMonth) {
            selectedDay = daysInMonth
        }
    }
    
    val firstDayOfWeek = remember(calendar) {
        calendar.get(Calendar.DAY_OF_WEEK)
    }
    
    // Ajustar primeiro dia da semana (Domingo = 1, Segunda = 2, etc.)
    // Calendar.SUNDAY = 1, então subtraímos 1 para obter índice 0-6
    val firstDayOffset = remember(firstDayOfWeek) {
        (firstDayOfWeek - Calendar.SUNDAY) % 7
    }
    
    val monthNames = arrayOf(
        "Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho",
        "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro"
    )
    
    val dayNames = arrayOf("D", "S", "T", "Q", "Q", "S", "S")
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header com mês/ano e navegação
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            if (currentMonth == 1) {
                                currentMonth = 12
                                currentYear--
                            } else {
                                currentMonth--
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Mês anterior"
                        )
                    }
                    
                    Text(
                        text = "${monthNames[currentMonth - 1]} de $currentYear",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    
                    IconButton(
                        onClick = {
                            if (currentMonth == 12) {
                                currentMonth = 1
                                currentYear++
                            } else {
                                currentMonth++
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = "Próximo mês"
                        )
                    }
                }
                
                // Dias da semana
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    dayNames.forEach { day ->
                        Text(
                            text = day,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                
                // Grid de dias
                val weeks = (daysInMonth + firstDayOffset + 6) / 7
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    repeat(weeks) { week ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            repeat(7) { dayOfWeek ->
                                val dayNumber = week * 7 + dayOfWeek - firstDayOffset + 1
                                val isCurrentMonth = dayNumber in 1..daysInMonth
                                val isSelected = isCurrentMonth && dayNumber == selectedDay
                                
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .padding(4.dp)
                                        .then(
                                            if (isSelected) {
                                                Modifier.background(
                                                    MaterialTheme.colorScheme.primary,
                                                    shape = CircleShape
                                                )
                                            } else {
                                                Modifier
                                            }
                                        )
                                        .clickable(enabled = isCurrentMonth) {
                                            if (isCurrentMonth) {
                                                selectedDay = dayNumber
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isCurrentMonth) {
                                        Text(
                                            text = dayNumber.toString(),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (isSelected) {
                                                MaterialTheme.colorScheme.onPrimary
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            },
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                
                // Botões
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
                            onDateSelected(currentYear, currentMonth, selectedDay)
                        }
                    ) {
                        Text("OK")
                    }
                }
            }
        }
    }
}

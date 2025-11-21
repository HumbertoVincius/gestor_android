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
import androidx.compose.material3.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import com.humberto.gestorfinanceiro.data.model.Expense
import com.humberto.gestorfinanceiro.data.model.SortOrder
import com.humberto.gestorfinanceiro.di.Dependencies
import com.humberto.gestorfinanceiro.ui.home.formatCurrency
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import java.util.Locale

private const val TAG = "HomeScreen"

data class SelectedDate(val year: Int, val month: Int, val day: Int)

@Composable
fun HomeScreen() {
    val calendar = Calendar.getInstance()
    var selectedMonth by remember { mutableIntStateOf(calendar.get(Calendar.MONTH) + 1) }
    var selectedYear by remember { mutableIntStateOf(calendar.get(Calendar.YEAR)) }
    var expenses by remember { mutableStateOf<List<Expense>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var sortOrder by remember { mutableStateOf(SortOrder.DATE_DESC) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var editingExpense by remember { mutableStateOf<Expense?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    fun loadExpenses() {
        scope.launch {
            isLoading = true
            try {
                val fetchedExpenses = Dependencies.supabaseRepository.getExpensesByMonth(
                    selectedMonth,
                    selectedYear,
                    sortOrder
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

    // Filtrar despesas baseado na busca
    val filteredExpenses = remember(expenses, searchQuery) {
        if (searchQuery.isBlank()) {
            expenses
        } else {
            val query = searchQuery.lowercase(Locale.getDefault())
            expenses.filter { expense ->
                expense.estabelecimento?.lowercase(Locale.getDefault())?.contains(query) == true ||
                expense.categoria?.lowercase(Locale.getDefault())?.contains(query) == true ||
                expense.subcategoria?.lowercase(Locale.getDefault())?.contains(query) == true ||
                expense.valor?.toString()?.contains(query) == true ||
                formatCurrency(expense.valor ?: 0.0).lowercase(Locale.getDefault()).contains(query)
            }
        }
    }

    val groupedExpenses = remember(filteredExpenses) {
        groupExpensesByDay(filteredExpenses)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MonthSelector(
                    month = selectedMonth,
                    year = selectedYear,
                    onPreviousMonth = {
                        if (selectedMonth == 1) {
                            selectedMonth = 12
                            selectedYear--
                        } else {
                            selectedMonth--
                        }
                    },
                    onNextMonth = {
                        if (selectedMonth == 12) {
                            selectedMonth = 1
                            selectedYear++
                        } else {
                            selectedMonth++
                        }
                    }
                )
                SortOrderSelector(
                    currentSort = sortOrder,
                    onSortChanged = { sortOrder = it }
                )
            }

            // Campo de busca
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Buscar despesas") },
                placeholder = { Text("Valor, categoria, subcategoria ou estabelecimento") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                singleLine = true,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Buscar"
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Limpar busca"
                            )
                        }
                    }
                }
            )

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
                    verticalArrangement = Arrangement.spacedBy(8.dp)
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
                        items(dayExpenses, key = { it.id ?: UUID.randomUUID().toString() }) { expense ->
                            ExpenseListItem(
                                expense = expense,
                                onEditClick = {
                                    editingExpense = expense
                                },
                                onDeleteClick = { 
                                    expense.id?.let { id -> deleteExpense(id) }
                                }
                            )
                        }
                    }
                }
            }
        }

        // Botão flutuante de criação
        FloatingActionButton(
            onClick = { showCreateDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Adicionar despesa"
            )
        }
    }

    // Dialog de criação
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
    onDeleteClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = expense.estabelecimento ?: "Desconhecido",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
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
fun CreateExpenseDialog(
    onDismiss: () -> Unit,
    onExpenseCreated: () -> Unit,
    defaultMonth: Int,
    defaultYear: Int
) {
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
    var estabelecimento by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var selectedSubcategory by remember { mutableStateOf<String?>(null) }
    
    var categories by remember { mutableStateOf<List<String>>(emptyList()) }
    var subcategories by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoadingCategories by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
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
                categories = Dependencies.supabaseRepository.getUniqueCategories()
                // Carregar todas as subcategorias inicialmente
                val allExpenses = Dependencies.supabaseRepository.getAllExpenses()
                subcategories = allExpenses
                    .mapNotNull { it.subcategoria }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .sorted()
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao carregar categorias/subcategorias", e)
            } finally {
                isLoadingCategories = false
            }
        }
    }

    LaunchedEffect(selectedCategory) {
        // Filtrar subcategorias baseado na categoria selecionada
        scope.launch {
            try {
                if (selectedCategory != null) {
                    // Buscar subcategorias específicas da categoria selecionada
                    subcategories = Dependencies.supabaseRepository.getUniqueSubcategories(selectedCategory)
                } else {
                    // Se não há categoria selecionada, mostrar todas as subcategorias
                    val allExpenses = Dependencies.supabaseRepository.getAllExpenses()
                    subcategories = allExpenses
                        .mapNotNull { it.subcategoria }
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
                    value = estabelecimento,
                    onValueChange = { estabelecimento = it },
                    label = { Text("Estabelecimento") },
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
                                    val valorDouble = valor.toDoubleOrNull() ?: 0.0
                                    val dataCompetencia = "%d-%02d-%02d".format(
                                        selectedDate.year,
                                        selectedDate.month,
                                        selectedDate.day
                                    )
                                    
                                    val newExpense = Expense(
                                        estabelecimento = estabelecimento.ifBlank { null },
                                        valor = valorDouble,
                                        dataCompetencia = dataCompetencia,
                                        categoria = selectedCategory?.let { normalizeText(it) },
                                        subcategoria = selectedSubcategory?.let { normalizeText(it) }
                                    )
                                    
                                    Dependencies.supabaseRepository.createExpense(newExpense)
                                    onExpenseCreated()
                                } catch (e: Exception) {
                                    Log.e(TAG, "Erro ao criar despesa", e)
                                } finally {
                                    isSaving = false
                                }
                            }
                        },
                        enabled = !isSaving && valor.isNotBlank() && estabelecimento.isNotBlank()
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
fun EditExpenseDialog(
    expense: Expense,
    onDismiss: () -> Unit,
    onExpenseUpdated: () -> Unit,
    defaultMonth: Int,
    defaultYear: Int
) {
    val calendar = Calendar.getInstance()
    
    // Parse da data atual da despesa
    val expenseDate = remember(expense.dataCompetencia) {
        if (expense.dataCompetencia != null) {
            try {
                val parts = expense.dataCompetencia.split("-")
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
    var estabelecimento by remember { mutableStateOf(expense.estabelecimento ?: "") }
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
                categories = Dependencies.supabaseRepository.getUniqueCategories()
                if (selectedCategory != null) {
                    subcategories = Dependencies.supabaseRepository.getUniqueSubcategories(selectedCategory)
                } else {
                    val allExpenses = Dependencies.supabaseRepository.getAllExpenses()
                    subcategories = allExpenses
                        .mapNotNull { it.subcategoria }
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
                    subcategories = Dependencies.supabaseRepository.getUniqueSubcategories(selectedCategory)
                } else {
                    val allExpenses = Dependencies.supabaseRepository.getAllExpenses()
                    subcategories = allExpenses
                        .mapNotNull { it.subcategoria }
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
                    value = estabelecimento,
                    onValueChange = { estabelecimento = it },
                    label = { Text("Estabelecimento") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
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
                                    val valorDouble = valor.toDoubleOrNull() ?: 0.0
                                    val dataCompetencia = "%d-%02d-%02d".format(
                                        selectedDate.year,
                                        selectedDate.month,
                                        selectedDate.day
                                    )
                                    
                                    val updatedExpense = expense.copy(
                                        estabelecimento = estabelecimento.ifBlank { null },
                                        valor = valorDouble,
                                        dataCompetencia = dataCompetencia,
                                        categoria = selectedCategory?.let { normalizeText(it) },
                                        subcategoria = selectedSubcategory?.let { normalizeText(it) }
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
                        enabled = !isSaving && valor.isNotBlank() && estabelecimento.isNotBlank()
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

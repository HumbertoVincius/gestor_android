package com.humberto.gestorfinanceiro.ui.metas

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.humberto.gestorfinanceiro.data.model.Expense
import com.humberto.gestorfinanceiro.data.model.Goal
import com.humberto.gestorfinanceiro.di.Dependencies
import com.humberto.gestorfinanceiro.ui.home.CustomDatePickerDialog
import com.humberto.gestorfinanceiro.ui.home.SelectedDate
import com.humberto.gestorfinanceiro.ui.home.formatCurrency
import com.humberto.gestorfinanceiro.ui.home.normalizeText
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

private const val TAG = "MetasScreen"

fun getMonthYearString(month: Int, year: Int): String {
    val monthNames = arrayOf(
        "Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho",
        "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro"
    )
    return "${monthNames[month - 1]} $year"
}

@Composable
fun MetasSummaryCard(
    totalGasto: Double,
    totalPlanejado: Double,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Total Gasto",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
                Text(
                    text = formatCurrency(totalGasto),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(40.dp)
                    .background(
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.3f)
                    )
            )
            
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Total Planejado",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
                Text(
                    text = formatCurrency(totalPlanejado),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

enum class MetasSortOrder {
    NAME_ASC,
    NAME_DESC,
    VALUE_ASC,
    VALUE_DESC,
    PERCENTAGE_ASC,
    PERCENTAGE_DESC
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetasScreen() {
    val calendar = Calendar.getInstance()
    var selectedMonth by remember { mutableIntStateOf(calendar.get(Calendar.MONTH) + 1) }
    var selectedYear by remember { mutableIntStateOf(calendar.get(Calendar.YEAR)) }
    var goals by remember { mutableStateOf<List<Goal>>(emptyList()) }
    var expenses by remember { mutableStateOf<List<Expense>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var sortOrder by remember { mutableStateOf(MetasSortOrder.VALUE_DESC) }
    var expandedCategories by remember { mutableStateOf<Set<String>>(emptySet()) }
    var expandedSubcategories by remember { mutableStateOf<Map<String, Set<String>>>(emptyMap()) }
    var editingExpense by remember { mutableStateOf<Expense?>(null) }
    val scope = rememberCoroutineScope()

    fun loadData() {
        scope.launch {
            isLoading = true
            try {
                goals = Dependencies.supabaseRepository.getGoalsByMonth(selectedMonth, selectedYear)
                val allExpenses = Dependencies.supabaseRepository.getExpensesByMonth(
                    selectedMonth,
                    selectedYear,
                    com.humberto.gestorfinanceiro.data.model.SortOrder.DATE_DESC
                )
                expenses = allExpenses
                Log.d(TAG, "Metas carregadas: ${goals.size}, Despesas: ${expenses.size}")
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao carregar dados", e)
                goals = emptyList()
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
                loadData()
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao deletar despesa", e)
                isLoading = false
            }
        }
    }

    LaunchedEffect(selectedMonth, selectedYear) {
        loadData()
    }
    

    // Agrupar despesas por categoria
    val expensesByCategory = remember(expenses) {
        expenses.groupBy { it.categoria ?: "Outros" }
    }

    // Criar lista de categorias com dados agregados
    val categoriesData = remember(goals, expensesByCategory) {
        val categoryMap = mutableMapOf<String, CategoryData>()
        
        // Adicionar categorias que têm metas
        goals.forEach { goal ->
            val category = goal.categoria ?: return@forEach
            val categoryExpenses = expensesByCategory[category] ?: emptyList()
            val total = calculateCategoryTotal(categoryExpenses)
            val goalValue = goal.valorMeta?.toDouble() ?: 0.0
            val percentage = calculatePercentage(total, goalValue)
            val balance = calculateBalance(goalValue, total)
            
            categoryMap[category] = CategoryData(
                category = category,
                goal = goalValue,
                realized = total,
                percentage = percentage,
                balance = balance,
                expenses = categoryExpenses
            )
        }
        
        // Adicionar categorias que têm despesas mas não têm meta
        expensesByCategory.forEach { (category, categoryExpenses) ->
            if (!categoryMap.containsKey(category)) {
                val total = calculateCategoryTotal(categoryExpenses)
                categoryMap[category] = CategoryData(
                    category = category,
                    goal = 0.0,
                    realized = total,
                    percentage = 0.0,
                    balance = -total,
                    expenses = categoryExpenses
                )
            }
        }
        
        categoryMap.values.toList()
    }

    // Ordenar categorias
    val sortedCategories = remember(categoriesData, sortOrder) {
        when (sortOrder) {
            MetasSortOrder.NAME_ASC -> categoriesData.sortedBy { it.category }
            MetasSortOrder.NAME_DESC -> categoriesData.sortedByDescending { it.category }
            MetasSortOrder.VALUE_ASC -> categoriesData.sortedBy { it.realized }
            MetasSortOrder.VALUE_DESC -> categoriesData.sortedByDescending { it.realized }
            MetasSortOrder.PERCENTAGE_ASC -> categoriesData.sortedBy { it.percentage }
            MetasSortOrder.PERCENTAGE_DESC -> categoriesData.sortedByDescending { it.percentage }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("Metas")
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
                            onClick = { loadData() }
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Atualizar")
                        }
                        MetasSortOrderSelector(
                            currentSort = sortOrder,
                            onSortChanged = { sortOrder = it }
                        )
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
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Resumo do mês
                item {
                    MetasSummaryCard(
                        totalGasto = expenses.sumOf { it.valor ?: 0.0 },
                        totalPlanejado = goals.sumOf { it.valorMeta?.toDouble() ?: 0.0 },
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                
                items(sortedCategories, key = { it.category }) { categoryData ->
                    CategoryCard(
                        categoryData = categoryData,
                        isExpanded = expandedCategories.contains(categoryData.category),
                        onExpandedChange = { expanded ->
                            expandedCategories = if (expanded) {
                                expandedCategories + categoryData.category
                            } else {
                                expandedCategories - categoryData.category
                            }
                        },
                        expandedSubcategories = expandedSubcategories[categoryData.category] ?: emptySet(),
                        onSubcategoryExpandedChange = { subcategory, expanded ->
                            val currentSet = expandedSubcategories[categoryData.category] ?: emptySet()
                            expandedSubcategories = expandedSubcategories.toMutableMap().apply {
                                this[categoryData.category] = if (expanded) {
                                    currentSet + subcategory
                                } else {
                                    currentSet - subcategory
                                }
                            }
                        },
                        onEditExpense = { expense ->
                            editingExpense = expense
                        },
                        onDeleteExpense = { expenseId ->
                            deleteExpense(expenseId)
                        }
                    )
                }
            }
        }
    }

    // Dialog de edição
    editingExpense?.let { expense ->
        EditExpenseDialog(
            expense = expense,
            onDismiss = { editingExpense = null },
            onExpenseUpdated = {
                editingExpense = null
                loadData()
            },
            defaultMonth = selectedMonth,
            defaultYear = selectedYear
        )
    }
}

data class CategoryData(
    val category: String,
    val goal: Double,
    val realized: Double,
    val percentage: Double,
    val balance: Double,
    val expenses: List<Expense>
)

@Composable
fun CategoryCard(
    categoryData: CategoryData,
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    expandedSubcategories: Set<String>,
    onSubcategoryExpandedChange: (String, Boolean) -> Unit,
    onEditExpense: (Expense) -> Unit,
    onDeleteExpense: (String) -> Unit
) {
    val subcategories = groupExpensesBySubcategory(categoryData.expenses)
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header da categoria
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onExpandedChange(!isExpanded) },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Ícone circular com primeira letra
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                MaterialTheme.colorScheme.primaryContainer,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = categoryData.category.firstOrNull()?.uppercase() ?: "?",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = categoryData.category,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (categoryData.goal > 0) {
                            Text(
                                text = "Saldo: ${formatCurrency(categoryData.balance)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
                
                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = formatCurrency(categoryData.realized),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    if (categoryData.goal > 0) {
                        Text(
                            text = "%.1f%% de ${formatCurrency(categoryData.goal)}".format(
                                categoryData.percentage
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
                
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Recolher" else "Expandir"
                )
            }
            
            // Barra de progresso
            if (categoryData.goal > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                val progress = (categoryData.percentage / 100.0).coerceIn(0.0, 1.0)
                val progressColor = when {
                    categoryData.percentage > 100 -> MaterialTheme.colorScheme.error
                    categoryData.percentage > 80 -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.primary
                }
                
                LinearProgressIndicator(
                    progress = progress.toFloat(),
                    modifier = Modifier.fillMaxWidth(),
                    color = progressColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
            
            // Conteúdo expandido
            if (isExpanded) {
                Spacer(modifier = Modifier.height(16.dp))
                
                if (subcategories.isEmpty()) {
                    // Mostrar transações diretamente se não houver subcategorias
                    // Ordenar por data (mais recente primeiro)
                    val sortedExpenses = categoryData.expenses.sortedWith(
                        compareByDescending<Expense> { it.dataCompetencia ?: "" }
                    )
                    Text(
                        text = "Transações (${sortedExpenses.size}):",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    sortedExpenses.forEach { expense ->
                        TransactionItem(
                            expense = expense,
                            onEditClick = { onEditExpense(expense) },
                            onDeleteClick = { expense.idDespesa?.let { onDeleteExpense(it) } }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                } else {
                    // Mostrar subcategorias ordenadas por valor (maior para menor)
                    val sortedSubcategories = subcategories.toList().sortedByDescending { (subcategory, expenses) ->
                        expenses.sumOf { it.valor ?: 0.0 }
                    }
                    sortedSubcategories.forEach { (subcategory, subcategoryExpenses) ->
                        SubcategoryCard(
                            subcategory = subcategory,
                            expenses = subcategoryExpenses,
                            isExpanded = expandedSubcategories.contains(subcategory),
                            onExpandedChange = { expanded ->
                                onSubcategoryExpandedChange(subcategory, expanded)
                            },
                            onEditExpense = onEditExpense,
                            onDeleteExpense = onDeleteExpense
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun SubcategoryCard(
    subcategory: String,
    expenses: List<Expense>,
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onEditExpense: (Expense) -> Unit,
    onDeleteExpense: (String) -> Unit
) {
    val total = calculateSubcategoryTotal(expenses, subcategory)
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onExpandedChange(!isExpanded) },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = subcategory,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatCurrency(total),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Recolher" else "Expandir",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            
            if (isExpanded) {
                Spacer(modifier = Modifier.height(8.dp))
                // Ordenar transações por data (mais recente primeiro)
                val sortedExpenses = expenses.sortedWith(
                    compareByDescending<Expense> { it.dataCompetencia ?: it.dataDespesa ?: "" }
                )
                Text(
                    text = "Transações (${sortedExpenses.size}):",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                sortedExpenses.forEach { expense ->
                    TransactionItem(
                        expense = expense,
                        onEditClick = { onEditExpense(expense) },
                        onDeleteClick = { expense.idDespesa?.let { onDeleteExpense(it) } }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
fun TransactionItem(
    expense: Expense,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = expense.estabelecimento ?: "Desconhecido",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                (expense.dataCompetencia ?: expense.dataDespesa)?.let { date ->
                    Text(
                        text = formatTransactionDate(date, expense.hora),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "-${formatCurrency(expense.valor ?: 0.0)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onEditClick) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Editar",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onDeleteClick) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Deletar",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

fun formatTransactionDate(dateString: String, hora: String? = null): String {
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale("pt", "BR"))
        val date = inputFormat.parse(dateString)
        if (date != null) {
            val datePart = SimpleDateFormat("dd/MM", Locale("pt", "BR")).format(date)
            if (!hora.isNullOrBlank()) {
                "$datePart $hora"
            } else {
                datePart
            }
        } else {
            dateString
        }
    } catch (e: Exception) {
        dateString
    }
}

@Composable
fun MetasSortOrderSelector(
    currentSort: MetasSortOrder,
    onSortChanged: (MetasSortOrder) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        TextButton(onClick = { expanded = true }) {
            Text(
                text = when (currentSort) {
                    MetasSortOrder.NAME_ASC -> "Nome ↑"
                    MetasSortOrder.NAME_DESC -> "Nome ↓"
                    MetasSortOrder.VALUE_ASC -> "Valor ↑"
                    MetasSortOrder.VALUE_DESC -> "Valor ↓"
                    MetasSortOrder.PERCENTAGE_ASC -> "% ↑"
                    MetasSortOrder.PERCENTAGE_DESC -> "% ↓"
                },
                style = MaterialTheme.typography.bodyMedium
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("Nome ↑") },
                onClick = {
                    onSortChanged(MetasSortOrder.NAME_ASC)
                    expanded = false
                }
            )
            DropdownMenuItem(
                text = { Text("Nome ↓") },
                onClick = {
                    onSortChanged(MetasSortOrder.NAME_DESC)
                    expanded = false
                }
            )
            DropdownMenuItem(
                text = { Text("Valor ↑") },
                onClick = {
                    onSortChanged(MetasSortOrder.VALUE_ASC)
                    expanded = false
                }
            )
            DropdownMenuItem(
                text = { Text("Valor ↓") },
                onClick = {
                    onSortChanged(MetasSortOrder.VALUE_DESC)
                    expanded = false
                }
            )
            DropdownMenuItem(
                text = { Text("% ↑") },
                onClick = {
                    onSortChanged(MetasSortOrder.PERCENTAGE_ASC)
                    expanded = false
                }
            )
            DropdownMenuItem(
                text = { Text("% ↓") },
                onClick = {
                    onSortChanged(MetasSortOrder.PERCENTAGE_DESC)
                    expanded = false
                }
            )
        }
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


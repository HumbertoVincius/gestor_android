package com.humberto.gestorfinanceiro.widget

import com.humberto.gestorfinanceiro.data.model.Expense
import com.humberto.gestorfinanceiro.data.model.Goal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

data class CategoryWidgetData(
    val category: String,
    val goal: Double,
    val realized: Double,
    val percentage: Double
)

fun formatCurrency(value: Double): String {
    return try {
        val safeValue = when {
            value.isNaN() || value.isInfinite() -> 0.0
            else -> value
        }
        val formatter = DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale("pt", "BR")))
        formatter.decimalFormatSymbols = DecimalFormatSymbols(Locale("pt", "BR")).apply {
            decimalSeparator = ','
            groupingSeparator = '.'
        }
        "R$ ${formatter.format(safeValue)}"
    } catch (e: Exception) {
        "R$ 0,00"
    }
}

fun formatCurrencyShort(value: Double): String {
    return try {
        val safeValue = when {
            value.isNaN() || value.isInfinite() -> 0.0
            else -> value
        }
        val symbols = DecimalFormatSymbols(Locale("pt", "BR")).apply {
            groupingSeparator = '.'
        }
        val formatter = DecimalFormat("#,##0", symbols)
        "R$ ${formatter.format(safeValue)}"
    } catch (e: Exception) {
        "R$ 0"
    }
}

fun calculateCategoryTotal(expenses: List<Expense>): Double {
    return try {
        val sum = expenses.sumOf { it.valor ?: 0.0 }
        if (sum.isNaN() || sum.isInfinite()) 0.0 else sum
    } catch (e: Exception) {
        0.0
    }
}

fun calculatePercentage(realized: Double, goal: Double): Double {
    return if (goal > 0 && !realized.isNaN() && !goal.isNaN()) {
        val result = (realized / goal) * 100.0
        if (result.isNaN() || result.isInfinite()) 0.0 else result
    } else {
        0.0
    }
}

fun calculateCategoryData(
    goals: List<Goal>,
    expenses: List<Expense>
): List<CategoryWidgetData> {
    return try {
        val expensesByCategory = expenses.groupBy { it.categoria ?: "Outros" }
        val categoryMap = mutableMapOf<String, CategoryWidgetData>()
        
        // Adicionar categorias que têm metas
        goals.forEach { goal ->
            val category = goal.nomeCategoria ?: return@forEach
            val categoryExpenses = expensesByCategory[category] ?: emptyList()
            val total = calculateCategoryTotal(categoryExpenses)
            val safeTotal = if (total.isNaN() || total.isInfinite()) 0.0 else total
            val goalValue = goal.valorMeta ?: 0.0
            val safeGoalValue = if (goalValue.isNaN() || goalValue.isInfinite()) 0.0 else goalValue
            val percentage = calculatePercentage(safeTotal, safeGoalValue)
            
            categoryMap[category] = CategoryWidgetData(
                category = category,
                goal = safeGoalValue,
                realized = safeTotal,
                percentage = percentage
            )
        }
        
        categoryMap.values.toList().sortedByDescending { it.realized }
    } catch (e: Exception) {
        emptyList()
    }
}

fun getMonthYearString(month: Int, year: Int): String {
    val monthNames = arrayOf(
        "Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho",
        "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro"
    )
    return "${monthNames[month - 1]} $year"
}


package com.humberto.gestorfinanceiro.ui.home

import com.humberto.gestorfinanceiro.data.model.Expense
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.*

fun groupExpensesByDay(expenses: List<Expense>): Map<String, List<Expense>> {
    return expenses
        .filter { (it.dataCompetencia ?: it.dataDespesa) != null }
        .groupBy { it.dataCompetencia ?: it.dataDespesa ?: "" }
        .toSortedMap(compareByDescending { it })
}

fun calculateDayTotal(expenses: List<Expense>): Double {
    return expenses.sumOf { it.valor ?: 0.0 }
}

fun formatDate(dateString: String): String {
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale("pt", "BR"))
        val outputFormat = SimpleDateFormat("d 'de' MMMM", Locale("pt", "BR"))
        val date = inputFormat.parse(dateString)
        if (date != null) {
            outputFormat.format(date)
        } else {
            dateString
        }
    } catch (e: Exception) {
        dateString
    }
}

fun calculateMonthTotal(expenses: List<Expense>): Double {
    return expenses.sumOf { it.valor ?: 0.0 }
}

fun normalizeText(text: String): String {
    val normalized = Normalizer.normalize(text, Normalizer.Form.NFD)
    return normalized.replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "").lowercase(Locale.getDefault())
}

fun formatCurrency(value: Double): String {
    val formatter = java.text.DecimalFormat("#,##0.00", java.text.DecimalFormatSymbols(Locale("pt", "BR")))
    formatter.decimalFormatSymbols = java.text.DecimalFormatSymbols(Locale("pt", "BR")).apply {
        decimalSeparator = ','
        groupingSeparator = '.'
    }
    return "R$ ${formatter.format(value)}"
}

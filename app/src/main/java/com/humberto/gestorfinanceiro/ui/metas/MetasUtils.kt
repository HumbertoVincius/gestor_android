package com.humberto.gestorfinanceiro.ui.metas

import com.humberto.gestorfinanceiro.data.model.Expense
import com.humberto.gestorfinanceiro.data.model.Goal

fun calculateCategoryTotal(expenses: List<Expense>): Double {
    return expenses.sumOf { it.valor ?: 0.0 }
}

fun calculateSubcategoryTotal(expenses: List<Expense>, subcategory: String): Double {
    return expenses
        .filter { it.subcategoria == subcategory }
        .sumOf { it.valor ?: 0.0 }
}

fun groupExpensesBySubcategory(expenses: List<Expense>): Map<String, List<Expense>> {
    return expenses
        .filter { !it.subcategoria.isNullOrBlank() }
        .groupBy { it.subcategoria!! }
        .toSortedMap()
}

fun getGoalForCategory(goals: List<Goal>, category: String, month: Int, year: Int): Goal? {
    return goals.firstOrNull { 
        it.categoria == category && 
        it.mes == month.toLong() && 
        it.ano == year.toLong() 
    }
}

fun calculatePercentage(realized: Double, goal: Double): Double {
    return if (goal > 0) {
        (realized / goal) * 100.0
    } else {
        0.0
    }
}

fun calculateBalance(goal: Double, realized: Double): Double {
    return goal - realized
}


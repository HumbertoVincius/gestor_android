package com.humberto.gestorfinanceiro.ui.metas

import com.humberto.gestorfinanceiro.data.model.Expense
import com.humberto.gestorfinanceiro.data.model.Goal

fun calculateCategoryTotal(expenses: List<Expense>): Double {
    return try {
        val sum = expenses.sumOf { it.valor ?: 0.0 }
        if (sum.isNaN() || sum.isInfinite()) 0.0 else sum
    } catch (e: Exception) {
        0.0
    }
}

fun calculateSubcategoryTotal(expenses: List<Expense>, subcategory: String): Double {
    return try {
        val sum = expenses
            .filter { it.subcategoria == subcategory }
            .sumOf { it.valor ?: 0.0 }
        if (sum.isNaN() || sum.isInfinite()) 0.0 else sum
    } catch (e: Exception) {
        0.0
    }
}

fun groupExpensesBySubcategory(expenses: List<Expense>): Map<String, List<Expense>> {
    return expenses
        .filter { !it.subcategoria.isNullOrBlank() }
        .groupBy { it.subcategoria!! }
        .toSortedMap()
}

fun getGoalForCategory(goals: List<Goal>, category: String, month: Int, year: Int): Goal? {
    val filterDate = "%d-%02d".format(year, month)
    return goals.firstOrNull { 
        it.nomeCategoria == category && 
        it.dataInicio?.startsWith(filterDate) == true
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

fun calculateBalance(goal: Double, realized: Double): Double {
    return try {
        val safeGoal = if (goal.isNaN() || goal.isInfinite()) 0.0 else goal
        val safeRealized = if (realized.isNaN() || realized.isInfinite()) 0.0 else realized
        val result = safeGoal - safeRealized
        if (result.isNaN() || result.isInfinite()) 0.0 else result
    } catch (e: Exception) {
        0.0
    }
}


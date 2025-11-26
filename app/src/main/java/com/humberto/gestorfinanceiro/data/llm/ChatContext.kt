package com.humberto.gestorfinanceiro.data.llm

import com.humberto.gestorfinanceiro.data.model.Category
import com.humberto.gestorfinanceiro.data.model.Expense
import com.humberto.gestorfinanceiro.data.model.Goal
import com.humberto.gestorfinanceiro.data.model.Subcategory

data class ChatContext(
    val recentExpenses: List<Expense> = emptyList(),
    val currentMonthGoals: List<Goal> = emptyList(),
    val categories: List<Category> = emptyList(),
    val subcategories: List<Subcategory> = emptyList(),
    val totalSpentThisMonth: Double = 0.0,
    val month: Int = 0,
    val year: Int = 0
)


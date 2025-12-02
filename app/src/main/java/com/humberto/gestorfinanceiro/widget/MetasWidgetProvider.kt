package com.humberto.gestorfinanceiro.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import com.humberto.gestorfinanceiro.MainActivity
import com.humberto.gestorfinanceiro.R
import com.humberto.gestorfinanceiro.di.Dependencies
import com.humberto.gestorfinanceiro.ui.navigation.Screen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Calendar

class MetasWidgetProvider : AppWidgetProvider() {
    
    companion object {
        private const val TAG = "MetasWidgetProvider"
        private val widgetScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    }
    
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        Log.d(TAG, "onUpdate chamado para ${appWidgetIds.size} widgets")
        
        // Atualizar com dados padrão primeiro (síncrono e rápido)
        for (appWidgetId in appWidgetIds) {
            try {
                updateWidgetWithDefaults(context, appWidgetManager, appWidgetId)
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao atualizar widget $appWidgetId", e)
                e.printStackTrace()
                // Tentar criar um widget mínimo mesmo em caso de erro
                try {
                    val views = RemoteViews(context.packageName, R.layout.widget_metas)
                    views.setTextViewText(R.id.widget_total_expenses, "Erro")
                    views.setTextViewText(R.id.widget_total_goals, "Erro")
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                } catch (e2: Exception) {
                    Log.e(TAG, "Erro crítico ao criar widget mínimo", e2)
                }
            }
        }
        
        // Depois, atualizar com dados reais em background (assíncrono)
        // Usar Handler para garantir que não bloqueie
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            for (appWidgetId in appWidgetIds) {
                updateWidget(context, appWidgetManager, appWidgetId)
            }
        }, 500) // Delay de 500ms para garantir que o widget foi criado
    }
    
    private fun updateWidgetWithDefaults(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        var views: RemoteViews? = null
        try {
            views = RemoteViews(context.packageName, R.layout.widget_metas_simple)
            Log.d(TAG, "RemoteViews criado com sucesso")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao criar RemoteViews", e)
            e.printStackTrace()
            return
        }
        
        val v = views ?: return
        
        // Configurar clique para abrir app
        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("screen", Screen.METAS.name)
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            v.setOnClickPendingIntent(R.id.widget_container, pendingIntent)
            Log.d(TAG, "PendingIntent configurado")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao configurar pending intent", e)
            e.printStackTrace()
        }
        
        // Valores padrão - usar strings simples para evitar exceções
        try {
            v.setTextViewText(R.id.widget_total_expenses, "R$ 0,00")
            v.setTextViewText(R.id.widget_total_goals, "R$ 0,00")
            v.setTextViewText(R.id.widget_percentage, "0%")
            Log.d(TAG, "Valores padrão definidos")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao definir valores padrão", e)
            e.printStackTrace()
        }
        
        // Mês/ano
        try {
            val calendar = Calendar.getInstance()
            val month = calendar.get(Calendar.MONTH) + 1
            val year = calendar.get(Calendar.YEAR)
            val monthYear = getMonthYearString(month, year)
            v.setTextViewText(R.id.widget_month_year, monthYear)
            Log.d(TAG, "Mês/ano definido: $monthYear")
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao definir mês/ano", e)
                e.printStackTrace()
                try {
                    v.setTextViewText(R.id.widget_month_year, "")
                } catch (e2: Exception) {
                    Log.e(TAG, "Erro ao definir mês/ano vazio", e2)
                }
            }
            
        // Esconder todos os cards de categorias (se existirem) - não crítico
        // Se houver erro, apenas logar e continuar
        try {
            val cardIds = intArrayOf(
                R.id.widget_category_card_1,
                R.id.widget_category_card_2,
                R.id.widget_category_card_3,
                R.id.widget_category_card_4,
                R.id.widget_category_card_5
            )
            for (cardId in cardIds) {
                try {
                    v.setViewVisibility(cardId, android.view.View.GONE)
                } catch (e: Exception) {
                    // Qualquer erro é ignorado - cards são opcionais
                }
            }
        } catch (e: Exception) {
            // Ignorar completamente - não é crítico para o widget funcionar
        }
        
        // Atualizar widget
        try {
            appWidgetManager.updateAppWidget(appWidgetId, v)
            Log.d(TAG, "Widget atualizado com sucesso")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao atualizar widget", e)
            e.printStackTrace()
        }
    }
    
    private fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        widgetScope.launch {
            try {
                // Verificar se Dependencies está inicializado
                try {
                    Dependencies.supabaseRepository
                } catch (e: Exception) {
                    Log.e(TAG, "Dependencies não inicializado", e)
                    return@launch
                }
                
                val calendar = Calendar.getInstance()
                val month = calendar.get(Calendar.MONTH) + 1
                val year = calendar.get(Calendar.YEAR)
                
                // Buscar dados com tratamento de erro
                val goals = try {
                    Dependencies.supabaseRepository.getGoalsByMonthCached(month, year, false)
                } catch (e: Exception) {
                    Log.e(TAG, "Erro ao buscar metas", e)
                    emptyList()
                }
                
                val expenses = try {
                    Dependencies.supabaseRepository.getExpensesByMonthCached(
                        month,
                        year,
                        com.humberto.gestorfinanceiro.data.model.SortOrder.DATE_DESC,
                        false
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Erro ao buscar despesas", e)
                    emptyList()
                }
                
                // Calcular totais
                val totalExpenses = expenses.sumOf { it.valor ?: 0.0 }
                val totalGoals = goals.sumOf { it.valorMeta?.toDouble() ?: 0.0 }
                val totalPercentage = if (totalGoals > 0) {
                    calculatePercentage(totalExpenses, totalGoals)
                } else {
                    0.0
                }
                
                // Calcular dados das categorias
                val categoriesData = calculateCategoryData(goals, expenses)
                    .filter { it.goal > 0 }
                    .take(5) // Limitar a 5 categorias para o widget
                
                // Atualizar UI na thread principal
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    try {
                        val views = RemoteViews(context.packageName, R.layout.widget_metas_simple)
                        
                        // Configurar clique para abrir app
                        try {
                            val intent = Intent(context, MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                                putExtra("screen", Screen.METAS.name)
                            }
                            val pendingIntent = PendingIntent.getActivity(
                                context,
                                0,
                                intent,
                                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                            )
                            views.setOnClickPendingIntent(R.id.widget_container, pendingIntent)
                        } catch (e: Exception) {
                            Log.e(TAG, "Erro ao configurar pending intent", e)
                        }
                        
                        // Atualizar resumo geral
                        try {
                            views.setTextViewText(R.id.widget_total_expenses, formatCurrency(totalExpenses))
                            views.setTextViewText(R.id.widget_total_goals, formatCurrency(totalGoals))
                            views.setTextViewText(R.id.widget_percentage, "%.0f%%".format(totalPercentage))
                        } catch (e: Exception) {
                            Log.e(TAG, "Erro ao atualizar valores", e)
                        }
                        
                        // Atualizar mês/ano
                        try {
                            views.setTextViewText(R.id.widget_month_year, getMonthYearString(month, year))
                        } catch (e: Exception) {
                            Log.e(TAG, "Erro ao atualizar mês/ano", e)
                        }
                        
                        // Atualizar cards de categorias (opcional - não falhar se houver erro)
                        try {
                            updateCategoryCards(context, views, categoriesData)
                        } catch (e: Exception) {
                            Log.w(TAG, "Erro ao atualizar cards (não crítico)", e)
                            // Não relançar exceção - widget deve funcionar mesmo sem cards
                        }
                        
                        // Atualizar widget
                        appWidgetManager.updateAppWidget(appWidgetId, views)
                        Log.d(TAG, "Widget atualizado com sucesso")
                    } catch (e: Exception) {
                        Log.e(TAG, "Erro crítico ao atualizar views do widget", e)
                        e.printStackTrace()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao buscar dados do widget", e)
                // Não mostrar erro no widget, apenas logar
            }
        }
    }
    
    private fun updateCategoryCards(
        context: Context,
        views: RemoteViews,
        categoriesData: List<CategoryWidgetData>
    ) {
        try {
            // Limpar cards existentes
            val cardIds = intArrayOf(
                R.id.widget_category_card_1,
                R.id.widget_category_card_2,
                R.id.widget_category_card_3,
                R.id.widget_category_card_4,
                R.id.widget_category_card_5
            )
            
            // Verificar se pelo menos um card existe
            var cardsExist = false
            for (cardId in cardIds) {
                try {
                    views.setViewVisibility(cardId, android.view.View.GONE)
                    cardsExist = true
                } catch (e: android.content.res.Resources.NotFoundException) {
                    // ID não existe, continuar
                } catch (e: Exception) {
                    Log.w(TAG, "Erro ao verificar card $cardId", e)
                }
            }
            
            // Se nenhum card existe, sair silenciosamente
            if (!cardsExist) {
                Log.d(TAG, "Cards não encontrados no layout, pulando atualização")
                return
            }
            
            // Preencher cards com dados
            categoriesData.forEachIndexed { index, categoryData ->
                if (index < cardIds.size) {
                    try {
                        val cardId = cardIds[index]
                        views.setViewVisibility(cardId, android.view.View.VISIBLE)
                        
                        // Atualizar textos do card
                        val categoryNameId = when (index) {
                            0 -> R.id.widget_category_name_1
                            1 -> R.id.widget_category_name_2
                            2 -> R.id.widget_category_name_3
                            3 -> R.id.widget_category_name_4
                            4 -> R.id.widget_category_name_5
                            else -> null
                        }
                        
                        val categoryValueId = when (index) {
                            0 -> R.id.widget_category_value_1
                            1 -> R.id.widget_category_value_2
                            2 -> R.id.widget_category_value_3
                            3 -> R.id.widget_category_value_4
                            4 -> R.id.widget_category_value_5
                            else -> null
                        }
                        
                        val categoryPercentageId = when (index) {
                            0 -> R.id.widget_category_percentage_1
                            1 -> R.id.widget_category_percentage_2
                            2 -> R.id.widget_category_percentage_3
                            3 -> R.id.widget_category_percentage_4
                            4 -> R.id.widget_category_percentage_5
                            else -> null
                        }
                        
                        categoryNameId?.let { 
                            try {
                                views.setTextViewText(it, categoryData.category)
                            } catch (e: Exception) {
                                Log.w(TAG, "Erro ao definir nome da categoria $index", e)
                            }
                        }
                        categoryValueId?.let {
                            try {
                                views.setTextViewText(
                                    it,
                                    "${formatCurrencyShort(categoryData.realized)} / ${formatCurrencyShort(categoryData.goal)}"
                                )
                            } catch (e: Exception) {
                                Log.w(TAG, "Erro ao definir valor da categoria $index", e)
                            }
                        }
                        categoryPercentageId?.let {
                            try {
                                views.setTextViewText(it, "%.0f%%".format(categoryData.percentage))
                            } catch (e: Exception) {
                                Log.w(TAG, "Erro ao definir porcentagem da categoria $index", e)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Erro ao atualizar card $index", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao atualizar cards de categorias", e)
            // Não lançar exceção para não quebrar o widget
        }
    }
    
    override fun onEnabled(context: Context) {
        Log.d(TAG, "Widget habilitado")
    }
    
    override fun onDisabled(context: Context) {
        Log.d(TAG, "Widget desabilitado")
    }
}


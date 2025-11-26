package com.humberto.gestorfinanceiro.data.cache

import android.util.Log
import com.humberto.gestorfinanceiro.data.model.Category
import com.humberto.gestorfinanceiro.data.model.Expense
import com.humberto.gestorfinanceiro.data.model.Goal
import com.humberto.gestorfinanceiro.data.model.Subcategory
import com.humberto.gestorfinanceiro.di.Dependencies
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Cache singleton para otimizar performance do app
 * Armazena dados em memória para evitar requisições HTTP repetidas ao Supabase
 */
object DataCache {
    private const val TAG = "DataCache"
    
    // Cache de dados
    private var allExpenses: List<Expense>? = null
    private var allCategories: List<Category>? = null
    private var allSubcategories: List<Subcategory>? = null
    private var allGoals: List<Goal>? = null
    
    // Controle de tempo
    private var lastExpensesFetch: Long = 0
    private var lastCategoriesFetch: Long = 0
    private var lastGoalsFetch: Long = 0
    
    // Cache duration: 10 minutos (ajustável)
    private const val CACHE_DURATION = 10 * 60 * 1000L
    
    // Mutex para thread-safety
    private val mutex = Mutex()
    
    /**
     * Busca despesas com JOINs (usa cache se disponível)
     */
    suspend fun getExpenses(forceRefresh: Boolean = false): List<Expense> {
        // Verificar se precisa carregar
        val needsLoad = mutex.withLock {
            val now = System.currentTimeMillis()
            val expired = now - lastExpensesFetch > CACHE_DURATION
            forceRefresh || allExpenses == null || expired
        }
        
        if (needsLoad) {
            Log.d(TAG, "⏳ Carregando despesas do servidor...")
            val startTime = System.currentTimeMillis()
            
            try {
                // ✅ Buscar em PARALELO usando coroutineScope
                val result = coroutineScope {
                    val expensesDeferred = async(Dispatchers.IO) {
                        Dependencies.supabaseRepository.getAllExpenses()
                    }
                    val subcategoriesDeferred = async(Dispatchers.IO) {
                        Dependencies.supabaseRepository.getSubcategoriesList()
                    }
                    val categoriesDeferred = async(Dispatchers.IO) {
                        Dependencies.supabaseRepository.getCategoriesList()
                    }
                    
                    // Aguardar todas as respostas
                    val expenses = expensesDeferred.await()
                    val subcategories = subcategoriesDeferred.await()
                    val categories = categoriesDeferred.await()
                    
                    Triple(expenses, subcategories, categories)
                }
                
                val (expenses, subcategories, categories) = result
                
                Log.d(TAG, "📊 Dados carregados: ${expenses.size} despesas, ${categories.size} categorias, ${subcategories.size} subcategorias")
                
                // Fazer JOINs em memória
                val categoryMap = categories.associateBy { it.idCategoria }
                val subcategoryMap = subcategories.associateBy { it.idSubcategoria }
                
                val enrichedExpenses = expenses.map { expense ->
                    val subcategory = subcategoryMap[expense.idSubcategoria]
                    val category = subcategory?.idCategoria?.let { categoryMap[it] }
                    
                    expense.copy(
                        categoria = category?.nomeCategoria,
                        subcategoria = subcategory?.nomeSubcategoria
                    )
                }
                
                // Atualizar cache com lock
                mutex.withLock {
                    val now = System.currentTimeMillis()
                    allExpenses = enrichedExpenses
                    allCategories = categories
                    allSubcategories = subcategories
                    lastExpensesFetch = now
                    lastCategoriesFetch = now
                }
                
                val duration = System.currentTimeMillis() - startTime
                Log.d(TAG, "✅ Despesas carregadas em ${duration}ms (${enrichedExpenses.size} registros)")
                
                return enrichedExpenses
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao carregar despesas", e)
                e.printStackTrace()
                return mutex.withLock { allExpenses ?: emptyList() }
            }
        } else {
            return mutex.withLock {
                Log.d(TAG, "⚡ Usando despesas do cache (${allExpenses?.size} registros)")
                allExpenses ?: emptyList()
            }
        }
    }
    
    /**
     * Busca categorias (usa cache se disponível)
     */
    suspend fun getCategories(forceRefresh: Boolean = false): List<Category> {
        val needsLoad = mutex.withLock {
            val now = System.currentTimeMillis()
            val expired = now - lastCategoriesFetch > CACHE_DURATION
            forceRefresh || allCategories == null || expired
        }
        
        if (needsLoad) {
            Log.d(TAG, "⏳ Carregando categorias do servidor...")
            try {
                val categories = Dependencies.supabaseRepository.getCategoriesList()
                mutex.withLock {
                    allCategories = categories
                    lastCategoriesFetch = System.currentTimeMillis()
                }
                Log.d(TAG, "✅ Categorias carregadas (${categories.size} registros)")
                return categories
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao carregar categorias", e)
                return mutex.withLock { allCategories ?: emptyList() }
            }
        } else {
            return mutex.withLock {
                Log.d(TAG, "⚡ Usando categorias do cache (${allCategories?.size} registros)")
                allCategories ?: emptyList()
            }
        }
    }
    
    /**
     * Busca subcategorias (usa cache se disponível)
     */
    suspend fun getSubcategories(forceRefresh: Boolean = false): List<Subcategory> {
        val needsLoad = mutex.withLock {
            val now = System.currentTimeMillis()
            val expired = now - lastCategoriesFetch > CACHE_DURATION
            forceRefresh || allSubcategories == null || expired
        }
        
        if (needsLoad) {
            Log.d(TAG, "⏳ Carregando subcategorias do servidor...")
            try {
                val subcategories = Dependencies.supabaseRepository.getSubcategoriesList()
                mutex.withLock {
                    allSubcategories = subcategories
                    lastCategoriesFetch = System.currentTimeMillis()
                }
                Log.d(TAG, "✅ Subcategorias carregadas (${subcategories.size} registros)")
                return subcategories
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao carregar subcategorias", e)
                return mutex.withLock { allSubcategories ?: emptyList() }
            }
        } else {
            return mutex.withLock {
                Log.d(TAG, "⚡ Usando subcategorias do cache (${allSubcategories?.size} registros)")
                allSubcategories ?: emptyList()
            }
        }
    }
    
    /**
     * Busca metas (usa cache se disponível)
     */
    suspend fun getGoals(forceRefresh: Boolean = false): List<Goal> {
        val needsLoad = mutex.withLock {
            val now = System.currentTimeMillis()
            val expired = now - lastGoalsFetch > CACHE_DURATION
            forceRefresh || allGoals == null || expired
        }
        
        if (needsLoad) {
            Log.d(TAG, "⏳ Carregando metas do servidor...")
            try {
                val goals = Dependencies.supabaseRepository.getGoals()
                mutex.withLock {
                    allGoals = goals
                    lastGoalsFetch = System.currentTimeMillis()
                }
                Log.d(TAG, "✅ Metas carregadas (${goals.size} registros)")
                return goals
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao carregar metas", e)
                return mutex.withLock { allGoals ?: emptyList() }
            }
        } else {
            return mutex.withLock {
                Log.d(TAG, "⚡ Usando metas do cache (${allGoals?.size} registros)")
                allGoals ?: emptyList()
            }
        }
    }
    
    /**
     * Invalida cache de despesas (chamar após criar/editar/deletar despesa)
     */
    fun invalidateExpenses() {
        Log.d(TAG, "🔄 Cache de despesas invalidado")
        allExpenses = null
        lastExpensesFetch = 0
    }
    
    /**
     * Invalida cache de categorias e subcategorias
     */
    fun invalidateCategories() {
        Log.d(TAG, "🔄 Cache de categorias invalidado")
        allCategories = null
        allSubcategories = null
        lastCategoriesFetch = 0
    }
    
    /**
     * Invalida cache de metas
     */
    fun invalidateGoals() {
        Log.d(TAG, "🔄 Cache de metas invalidado")
        allGoals = null
        lastGoalsFetch = 0
    }
    
    /**
     * Limpa todo o cache
     */
    fun clearAll() {
        Log.d(TAG, "🗑️ Todo o cache foi limpo")
        allExpenses = null
        allCategories = null
        allSubcategories = null
        allGoals = null
        lastExpensesFetch = 0
        lastCategoriesFetch = 0
        lastGoalsFetch = 0
    }
}


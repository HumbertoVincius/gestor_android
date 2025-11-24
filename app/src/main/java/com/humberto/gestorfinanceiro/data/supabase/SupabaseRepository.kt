package com.humberto.gestorfinanceiro.data.supabase

import android.util.Log
import com.humberto.gestorfinanceiro.data.model.Category
import com.humberto.gestorfinanceiro.data.model.Expense
import com.humberto.gestorfinanceiro.data.model.Goal
import com.humberto.gestorfinanceiro.data.model.SortOrder
import com.humberto.gestorfinanceiro.data.model.Subcategory
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import java.text.Normalizer
import java.util.Locale
import javax.net.ssl.HttpsURLConnection

data class ConnectionTestResult(
    val success: Boolean,
    val message: String,
    val details: List<String> = emptyList(),
    val expensesFound: Int = 0
)

class SupabaseRepository(
    private val supabaseUrl: String,
    private val supabaseKey: String
) {
    private val client: SupabaseClient = createSupabaseClient(
        supabaseUrl = supabaseUrl,
        supabaseKey = supabaseKey
    ) {
        install(Postgrest)
    }
    
    companion object {
        private const val TAG = "SupabaseRepository"
    }

    suspend fun saveExpense(expense: Expense) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Salvando despesa: $expense")
            client.postgrest["despesas"].insert(expense)
            Log.d(TAG, "Despesa salva com sucesso")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao salvar despesa", e)
            e.printStackTrace()
            throw e
        }
    }
    
    suspend fun getExpenses(month: Int, year: Int): List<Expense> = withContext(Dispatchers.IO) {
        try {
            // Format: YYYY-MM
            val filter = "%d-%02d".format(year, month)
            Log.d(TAG, "Buscando despesas para $filter")
            val result = client.postgrest["despesas"]
                .select {
                    filter {
                        like("data_despesa", "$filter%")
                    }
                }
                .decodeList<Expense>()
            Log.d(TAG, "Despesas encontradas para $filter: ${result.size}")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao buscar despesas filtradas", e)
            e.printStackTrace()
            emptyList()
        }
    }
    
    suspend fun getAllExpenses(): List<Expense> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "=== INICIANDO BUSCA DE DESPESAS ===")
            Log.d(TAG, "Supabase URL: $supabaseUrl")
            Log.d(TAG, "Supabase Key (primeiros 10 chars): ${supabaseKey.take(10)}...")
            Log.d(TAG, "Buscando despesas da tabela 'despesas'...")
            
            val query = client.postgrest["despesas"].select()
            Log.d(TAG, "Query criada com sucesso")
            
            val result = query.decodeList<Expense>()
            Log.d(TAG, "Despesas decodificadas: ${result.size}")
            
            if (result.isNotEmpty()) {
                Log.d(TAG, "Primeira despesa encontrada:")
                Log.d(TAG, "  - ID: ${result.first().idDespesa}")
                Log.d(TAG, "  - Estabelecimento: ${result.first().estabelecimento}")
                Log.d(TAG, "  - Valor: ${result.first().valor}")
                Log.d(TAG, "  - Data: ${result.first().dataCompetencia}")
                Log.d(TAG, "  - Categoria: ${result.first().categoria}")
            } else {
                Log.w(TAG, "Nenhuma despesa encontrada na resposta")
                Log.w(TAG, "Possíveis causas:")
                Log.w(TAG, "  1. RLS não está habilitado na tabela (apenas ter a política não basta)")
                Log.w(TAG, "  2. Tabela vazia")
                Log.w(TAG, "  3. Erro na decodificação dos dados")
                Log.w(TAG, "  4. Verifique no Supabase: Authentication > Policies > Enable RLS na tabela")
            }
            
            Log.d(TAG, "=== BUSCA CONCLUÍDA COM SUCESSO ===")
            result
        } catch (e: Exception) {
            Log.e(TAG, "=== ERRO AO BUSCAR DESPESAS ===")
            Log.e(TAG, "Mensagem do erro: ${e.message}")
            Log.e(TAG, "Tipo do erro: ${e.javaClass.name}")
            Log.e(TAG, "Causa: ${e.cause?.message}")
            
            // Log detalhado do stack trace
            val stackTrace = e.stackTraceToString()
            Log.e(TAG, "Stack trace completo:\n$stackTrace")
            
            // Verificar tipo específico de erro
            when {
                e.message?.contains("HTTP", ignoreCase = true) == true -> {
                    Log.e(TAG, "ERRO HTTP detectado. Verifique:")
                    Log.e(TAG, "  1. URL do Supabase está correta?")
                    Log.e(TAG, "  2. Anon key está correta?")
                    Log.e(TAG, "  3. RLS está configurado para permitir SELECT?")
                }
                e.message?.contains("404", ignoreCase = true) == true -> {
                    Log.e(TAG, "ERRO 404: Tabela 'despesas' não encontrada")
                    Log.e(TAG, "Verifique se o nome da tabela está correto no Supabase")
                }
                e.message?.contains("permission", ignoreCase = true) == true -> {
                    Log.e(TAG, "ERRO DE PERMISSÃO: RLS bloqueando acesso")
                    Log.e(TAG, "Configure uma política RLS para permitir SELECT com anon key")
                }
                e.message?.contains("decode", ignoreCase = true) == true -> {
                    Log.e(TAG, "ERRO DE DECODIFICAÇÃO: Problema ao converter dados")
                    Log.e(TAG, "Verifique se o modelo Expense corresponde à estrutura da tabela")
                }
                else -> {
                    Log.e(TAG, "Verifique:")
                    Log.e(TAG, "  1. Conexão com internet")
                    Log.e(TAG, "  2. URL e chave do Supabase")
                    Log.e(TAG, "  3. RLS configurado corretamente")
                    Log.e(TAG, "  4. Nome da tabela: 'despesas'")
                }
            }
            
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun getGoals(): List<Goal> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Buscando metas...")
            val result = client.postgrest["metas"].select().decodeList<Goal>()
            Log.d(TAG, "Metas encontradas: ${result.size}")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao buscar metas", e)
            e.printStackTrace()
            emptyList()
        }
    }
    
    suspend fun getGoalsByMonth(month: Int, year: Int): List<Goal> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Buscando metas para mês $month/$year...")
            val filter = "%d-%02d".format(year, month)
            
            // Buscar todas as metas
            val allGoals = client.postgrest["metas"]
                .select()
                .decodeList<Goal>()
            
            // Buscar categorias para enriquecer
            val categories = client.postgrest["categoria"]
                .select()
                .decodeList<Category>()
            
            val categoryMap = categories.associateBy { it.idCategoria }
            
            // Enriquecer metas com nome da categoria e filtrar por período
            val result = allGoals.filter { goal ->
                goal.dataInicio?.startsWith(filter) == true
            }.map { goal ->
                val category = categoryMap[goal.idCategoria]
                goal.copy(nomeCategoria = category?.nomeCategoria)
            }
            
            Log.d(TAG, "Metas encontradas para $month/$year: ${result.size}")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao buscar metas do mês", e)
            e.printStackTrace()
            emptyList()
        }
    }
    
    suspend fun getExpensesByCategoryAndMonth(category: String, month: Int, year: Int): List<Expense> = withContext(Dispatchers.IO) {
        try {
            val filter = "%d-%02d".format(year, month)
            Log.d(TAG, "Buscando despesas para categoria $category no mês $filter...")
            val query = client.postgrest["despesas"]
                .select {
                    filter {
                        like("data_despesa", "$filter%")
                        // Nota: categoria pode vir de view ou join com subcategoria
                        // Se não houver view, precisará fazer join manualmente
                    }
                }
            val result = query.decodeList<Expense>()
            Log.d(TAG, "Despesas encontradas para categoria $category: ${result.size}")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao buscar despesas por categoria", e)
            e.printStackTrace()
            emptyList()
        }
    }
    
    suspend fun updateExpense(expense: Expense): Expense = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Atualizando despesa: $expense")
            val expenseId = expense.idDespesa ?: throw IllegalArgumentException("Expense ID não pode ser null")
            val result = client.postgrest["despesas"]
                .update(expense) {
                    filter {
                        eq("id_despesa", expenseId)
                    }
                    select()
                }
                .decodeSingle<Expense>()
            Log.d(TAG, "Despesa atualizada com sucesso: ${result.idDespesa}")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao atualizar despesa", e)
            e.printStackTrace()
            throw e
        }
    }
    
    suspend fun getExpensesByMonth(month: Int, year: Int, sortBy: SortOrder): List<Expense> = withContext(Dispatchers.IO) {
        try {
            val filter = "%d-%02d".format(year, month)
            Log.d(TAG, "Buscando despesas para $filter com ordenação: $sortBy")
            
            // Buscar todas as despesas
            val allExpenses = client.postgrest["despesas"]
                .select()
                .decodeList<Expense>()
            
            Log.d(TAG, "Total de despesas no banco: ${allExpenses.size}")
            
            // Buscar todas as subcategorias e categorias para fazer o enriquecimento
            val subcategories = client.postgrest["subcategoria"]
                .select()
                .decodeList<Subcategory>()
            
            val categories = client.postgrest["categoria"]
                .select()
                .decodeList<Category>()
            
            // Criar mapas para lookup rápido
            val categoryMap = categories.associateBy { it.idCategoria }
            val subcategoryMap = subcategories.associateBy { it.idSubcategoria }
            
            // Enriquecer despesas com nomes de categoria e subcategoria
            val enrichedExpenses = allExpenses.map { expense ->
                val subcategory = subcategoryMap[expense.idSubcategoria]
                val category = subcategory?.idCategoria?.let { categoryMap[it] }
                
                expense.copy(
                    categoria = category?.nomeCategoria,
                    subcategoria = subcategory?.nomeSubcategoria,
                    estabelecimento = expense.local, // Usar 'local' como estabelecimento
                    dataCompetencia = expense.dataDespesa // Usar data_despesa como data_competencia para compatibilidade
                )
            }
            
            // Filtrar por mês/ano usando data_despesa
            val result = enrichedExpenses.filter { expense ->
                val dateToCheck = expense.dataDespesa
                val matches = dateToCheck?.startsWith(filter) == true
                if (matches) {
                    Log.d(TAG, "Despesa: ${expense.idDespesa}, local: ${expense.local}, cat: ${expense.categoria}, subcat: ${expense.subcategoria}")
                }
                matches
            }
            
            Log.d(TAG, "Despesas filtradas para $filter: ${result.size}")
            
            // Aplicar ordenação manualmente
            val finalResult = when (sortBy) {
                SortOrder.DATE_DESC -> result.sortedWith(compareByDescending<Expense> { it.dataDespesa ?: "" })
                SortOrder.DATE_ASC -> result.sortedWith(compareBy<Expense> { it.dataDespesa ?: "" })
                SortOrder.VALUE_DESC -> result.sortedWith(compareByDescending<Expense> { it.valor ?: 0.0 })
                SortOrder.VALUE_ASC -> result.sortedWith(compareBy<Expense> { it.valor ?: 0.0 })
                SortOrder.NAME_ASC -> result.sortedWith(compareBy<Expense> { it.local ?: "" })
                SortOrder.NAME_DESC -> result.sortedWith(compareByDescending<Expense> { it.local ?: "" })
                SortOrder.CATEGORY_ASC -> result.sortedWith(compareBy<Expense> { it.categoria ?: "" })
                SortOrder.CATEGORY_DESC -> result.sortedWith(compareByDescending<Expense> { it.categoria ?: "" })
            }
            
            Log.d(TAG, "Despesas ordenadas: ${finalResult.size}")
            finalResult
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao buscar despesas do mês", e)
            e.printStackTrace()
            emptyList()
        }
    }
    
    suspend fun getCategoriesList(): List<Category> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Buscando categorias da tabela 'categoria'...")
            val result = client.postgrest["categoria"]
                .select()
                .decodeList<Category>()
            Log.d(TAG, "Categorias encontradas: ${result.size}")
            result.sortedBy { it.nomeCategoria }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao buscar categorias", e)
            emptyList()
        }
    }

    suspend fun getSubcategoriesList(categoryId: String? = null): List<Subcategory> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Buscando subcategorias da tabela 'subcategoria'...")
            val query = client.postgrest["subcategoria"].select {
                if (categoryId != null) {
                    filter {
                        eq("id_categoria", categoryId)
                    }
                }
            }
            val result = query.decodeList<Subcategory>()
            Log.d(TAG, "Subcategorias encontradas: ${result.size}")
            result.sortedBy { it.nomeSubcategoria }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao buscar subcategorias", e)
            emptyList()
        }
    }

    suspend fun getUniqueCategories(): List<String> = withContext(Dispatchers.IO) {
        getCategoriesList().mapNotNull { it.nomeCategoria }
    }
    
    suspend fun getUniqueSubcategories(categoryName: String?): List<String> = withContext(Dispatchers.IO) {
        if (categoryName == null) return@withContext emptyList()
        
        // Encontrar ID da categoria pelo nome
        val categories = getCategoriesList()
        val category = categories.find { 
            normalizeCategory(it.nomeCategoria ?: "") == normalizeCategory(categoryName) || it.nomeCategoria.equals(categoryName, ignoreCase = true) 
        }
        
        if (category?.idCategoria == null) {
            return@withContext emptyList()
        }
        
        getSubcategoriesList(category.idCategoria).mapNotNull { it.nomeSubcategoria }
    }
    
    private fun normalizeCategory(category: String): String {
        // Remove espaços extras, normaliza acentos e converte para minúsculas
        val trimmed = category.trim()
        val normalized = Normalizer.normalize(trimmed, Normalizer.Form.NFD)
        return normalized.replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
            .lowercase(Locale.getDefault())
            .replace("\\s+".toRegex(), " ") // Normaliza espaços múltiplos
    }
    
    suspend fun getSubcategoryIdByName(categoryName: String, subcategoryName: String): String? = withContext(Dispatchers.IO) {
        try {
            // Buscar categoria primeiro
            val categories = getCategoriesList()
            val category = categories.find { 
                normalizeCategory(it.nomeCategoria ?: "") == normalizeCategory(categoryName)
            }
            
            if (category?.idCategoria == null) {
                Log.w(TAG, "Categoria '$categoryName' não encontrada")
                return@withContext null
            }
            
            // Buscar subcategoria
            val subcategories = getSubcategoriesList(category.idCategoria)
            val subcategory = subcategories.find { 
                normalizeCategory(it.nomeSubcategoria ?: "") == normalizeCategory(subcategoryName)
            }
            
            if (subcategory?.idSubcategoria == null) {
                Log.w(TAG, "Subcategoria '$subcategoryName' não encontrada para categoria '$categoryName'")
            }
            
            subcategory?.idSubcategoria
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao buscar ID da subcategoria", e)
            null
        }
    }
    
    suspend fun createExpense(expense: Expense): Expense = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Criando despesa: $expense")
            val result = client.postgrest["despesas"]
                .insert(expense) {
                    select()
                }
                .decodeSingle<Expense>()
            Log.d(TAG, "Despesa criada com sucesso: ${result.idDespesa}")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao criar despesa", e)
            e.printStackTrace()
            throw e
        }
    }
    
    suspend fun deleteExpense(id: String) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Deletando despesa com ID: $id")
            client.postgrest["despesas"]
                .delete {
                    filter {
                        eq("id_despesa", id)
                    }
                }
            Log.d(TAG, "Despesa deletada com sucesso")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao deletar despesa", e)
            e.printStackTrace()
            throw e
        }
    }
    
    /**
     * Função de teste para diagnosticar problemas de conexão
     * Testa conectividade básica, SSL e acesso ao Supabase
     */
    suspend fun testConnection(): ConnectionTestResult = withContext(Dispatchers.IO) {
        val details = mutableListOf<String>()
        
        try {
            Log.d(TAG, "=== INICIANDO TESTE DE CONEXÃO ===")
            
            // Teste 1: Verificar se a URL é válida
            Log.d(TAG, "Teste 1: Validando URL do Supabase...")
            try {
                val url = URL(supabaseUrl)
                val msg = "✓ URL válida: ${url.protocol}://${url.host}"
                Log.d(TAG, msg)
                details.add(msg)
            } catch (e: Exception) {
                val msg = "✗ URL inválida: ${e.message}"
                Log.e(TAG, msg)
                return@withContext ConnectionTestResult(
                    success = false,
                    message = "Erro: URL do Supabase inválida",
                    details = listOf(msg)
                )
            }
            
            // Teste 2: Tentar conectar diretamente ao Supabase (teste HTTPS básico)
            Log.d(TAG, "Teste 2: Testando conexão HTTPS com Supabase...")
            var httpsSuccess = false
            var httpsError: String? = null
            try {
                val testUrl = "$supabaseUrl/rest/v1/"
                val url = URL(testUrl)
                val connection = url.openConnection() as HttpsURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("apikey", supabaseKey)
                connection.setRequestProperty("Authorization", "Bearer $supabaseKey")
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                
                val responseCode = connection.responseCode
                val msg = "✓ Conexão HTTPS estabelecida. Status: $responseCode"
                Log.d(TAG, msg)
                details.add(msg)
                httpsSuccess = true
                
                if (responseCode == 200 || responseCode == 404) {
                    val msg2 = "✓ Servidor Supabase está acessível"
                    Log.d(TAG, msg2)
                    details.add(msg2)
                } else {
                    val msg2 = "⚠ Status HTTP inesperado: $responseCode"
                    Log.w(TAG, msg2)
                    details.add(msg2)
                }
                
                connection.disconnect()
            } catch (e: Exception) {
                httpsError = e.message
                val msg = "✗ Erro ao conectar com Supabase: ${e.message}"
                Log.e(TAG, msg)
                Log.e(TAG, "Tipo: ${e.javaClass.name}")
                details.add(msg)
                
                if (e.message?.contains("SSL") == true || e.message?.contains("certificate") == true) {
                    val msg2 = "⚠ Problema de SSL detectado. Pode ser bloqueio do Android."
                    Log.e(TAG, msg2)
                    details.add(msg2)
                }
                if (e.message?.contains("timeout") == true) {
                    val msg2 = "⚠ Timeout detectado. Verifique conexão de internet."
                    Log.e(TAG, msg2)
                    details.add(msg2)
                }
                if (e.message?.contains("UnknownHostException") == true || e.message?.contains("UnknownHost") == true) {
                    val msg2 = "⚠ Host não encontrado. Verifique URL do Supabase."
                    Log.e(TAG, msg2)
                    details.add(msg2)
                }
            }
            
            // Teste 3: Testar query simples na tabela despesas
            Log.d(TAG, "Teste 3: Testando query simples na tabela 'despesas'...")
            var querySuccess = false
            var expensesCount = 0
            var queryError: String? = null
            
            try {
                val result = client.postgrest["despesas"]
                    .select()
                    .decodeList<Expense>()
                
                expensesCount = result.size
                val msg = "✓ Query executada com sucesso. Resultados: ${result.size}"
                Log.d(TAG, msg)
                details.add(msg)
                querySuccess = true
                
                if (result.isNotEmpty()) {
                    val msg2 = "✓ Dados retornados corretamente"
                    Log.d(TAG, msg2)
                    details.add(msg2)
                } else {
                    val msg2 = "⚠ Query retornou vazio (pode ser RLS ou tabela vazia)"
                    Log.w(TAG, msg2)
                    details.add(msg2)
                }
            } catch (e: Exception) {
                queryError = e.message
                val msg = "✗ Erro ao executar query: ${e.message}"
                Log.e(TAG, msg)
                Log.e(TAG, "Tipo: ${e.javaClass.name}")
                details.add(msg)
                
                // Análise detalhada do erro
                when {
                    e.message?.contains("404") == true -> {
                        val msg2 = "→ ERRO 404: Tabela 'despesas' não encontrada"
                        Log.e(TAG, msg2)
                        details.add(msg2)
                    }
                    e.message?.contains("permission") == true || 
                    e.message?.contains("RLS") == true || 
                    e.message?.contains("policy") == true -> {
                        val msg2 = "→ ERRO DE PERMISSÃO: RLS bloqueando"
                        Log.e(TAG, msg2)
                        details.add(msg2)
                    }
                    e.message?.contains("decode") == true || 
                    e.message?.contains("serialization") == true -> {
                        val msg2 = "→ ERRO DE DECODIFICAÇÃO: Modelo não corresponde aos dados"
                        Log.e(TAG, msg2)
                        details.add(msg2)
                    }
                    e.message?.contains("network") == true || 
                    e.message?.contains("timeout") == true -> {
                        val msg2 = "→ ERRO DE REDE: Problema de conectividade"
                        Log.e(TAG, msg2)
                        details.add(msg2)
                    }
                    else -> {
                        val msg2 = "→ ERRO DESCONHECIDO: ${e.message}"
                        Log.e(TAG, msg2)
                        details.add(msg2)
                        e.printStackTrace()
                    }
                }
            }
            
            Log.d(TAG, "=== TESTE DE CONEXÃO CONCLUÍDO ===")
            
            // Determinar resultado final
            val success = querySuccess && expensesCount > 0
            val message = when {
                querySuccess && expensesCount > 0 -> "✓ Conexão realizada com sucesso! Encontradas $expensesCount despesa(s)."
                querySuccess && expensesCount == 0 -> "✓ Conexão realizada com sucesso, mas nenhuma despesa foi retornada. Verifique RLS ou se há dados na tabela."
                !httpsSuccess -> "✗ Erro ao conectar com o servidor Supabase: ${httpsError ?: "Erro desconhecido"}"
                else -> "✗ Erro ao buscar despesas: ${queryError ?: "Erro desconhecido"}"
            }
            
            ConnectionTestResult(
                success = success,
                message = message,
                details = details,
                expensesFound = expensesCount
            )
        } catch (e: Exception) {
            Log.e(TAG, "=== ERRO CRÍTICO NO TESTE ===")
            Log.e(TAG, "Erro: ${e.message}")
            e.printStackTrace()
            
            ConnectionTestResult(
                success = false,
                message = "Erro crítico: ${e.message ?: "Erro desconhecido"}",
                details = details + "Erro crítico: ${e.javaClass.simpleName}"
            )
        }
    }
    
    suspend fun createCategory(category: Category) = withContext(Dispatchers.IO) {
        try {
            client.postgrest["categorias"].insert(category)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao criar categoria", e)
            throw e
        }
    }
    
    suspend fun updateCategoryObj(category: Category) = withContext(Dispatchers.IO) {
        try {
            client.postgrest["categorias"].update(category) {
                filter { eq("id_categoria", category.id!!) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao atualizar categoria", e)
            throw e
        }
    }
    
    suspend fun deleteCategoryObj(categoryId: String) = withContext(Dispatchers.IO) {
        try {
            client.postgrest["categorias"].delete {
                filter { eq("id_categoria", categoryId) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao deletar categoria", e)
            throw e
        }
    }
    
    suspend fun createSubcategory(subcategory: Subcategory) = withContext(Dispatchers.IO) {
        try {
            client.postgrest["subcategorias"].insert(subcategory)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao criar subcategoria", e)
            throw e
        }
    }
    
    suspend fun updateSubcategoryObj(subcategory: Subcategory) = withContext(Dispatchers.IO) {
        try {
            client.postgrest["subcategorias"].update(subcategory) {
                filter { eq("id_subcategoria", subcategory.id!!) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao atualizar subcategoria", e)
            throw e
        }
    }
    
    suspend fun deleteSubcategoryObj(subcategoryId: String) = withContext(Dispatchers.IO) {
        try {
            client.postgrest["subcategorias"].delete {
                filter { eq("id_subcategoria", subcategoryId) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao deletar subcategoria", e)
            throw e
        }
    }

    // Métodos legados para manter compatibilidade (se necessário), mas agora não fazem nada
    // Pois a gestão é via tabelas relacionais
    @Deprecated("Use updateCategoryObj instead", ReplaceWith("updateCategoryObj(category)"))
    @Suppress("UNUSED_PARAMETER")
    suspend fun updateCategory(oldCategory: String, newCategory: String) {
        // Deprecated - não faz nada
    }
    
    @Deprecated("Use deleteCategoryObj instead", ReplaceWith("deleteCategoryObj(categoryId)"))
    @Suppress("UNUSED_PARAMETER")
    suspend fun deleteCategory(category: String) {
        // Deprecated - não faz nada
    }
    
    @Deprecated("Use updateSubcategoryObj instead", ReplaceWith("updateSubcategoryObj(subcategory)"))
    @Suppress("UNUSED_PARAMETER")
    suspend fun updateSubcategory(category: String, oldSubcategory: String, newSubcategory: String) {
        // Deprecated - não faz nada
    }
    
    @Deprecated("Use deleteSubcategoryObj instead", ReplaceWith("deleteSubcategoryObj(subcategoryId)"))
    @Suppress("UNUSED_PARAMETER")
    suspend fun deleteSubcategory(category: String, subcategory: String) {
        // Deprecated - não faz nada
    }
}

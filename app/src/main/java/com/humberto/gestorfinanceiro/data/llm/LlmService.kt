package com.humberto.gestorfinanceiro.data.llm

import com.humberto.gestorfinanceiro.data.log.ActivityLogManager
import com.humberto.gestorfinanceiro.data.model.Category
import com.humberto.gestorfinanceiro.data.model.ChatMessage
import com.humberto.gestorfinanceiro.data.model.Expense
import com.humberto.gestorfinanceiro.data.model.Subcategory
import com.humberto.gestorfinanceiro.di.Dependencies
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.JsonConvertException
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.json.JSONObject
import java.util.Calendar

interface LlmService {
    suspend fun parseSms(
        smsBody: String,
        subcategories: List<Subcategory>,
        categories: List<Category>
    ): Expense?
    
    suspend fun chatWithAgent(
        userMessage: String,
        context: ChatContext,
        messageHistory: List<ChatMessage>
    ): String
}

@Serializable
data class OpenAIRequest(
    val model: String,
    val messages: List<OpenAIMessage>,
    val temperature: Double = 0.7
)

@Serializable
data class OpenAIMessage(
    val role: String,
    val content: String
)

@Serializable
data class OpenAIResponse(
    val choices: List<OpenAIChoice>? = null,
    val error: OpenAIError? = null
)

@Serializable
data class OpenAIChoice(
    val message: OpenAIMessageResponse
)

@Serializable
data class OpenAIMessageResponse(
    val content: String
)

@Serializable
data class OpenAIError(
    val message: String,
    val type: String? = null,
    val code: String? = null
)

class OpenAILlmService(private val apiKey: String) : LlmService {
    
    private val httpClient = HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override suspend fun parseSms(
        smsBody: String,
        subcategories: List<Subcategory>,
        categories: List<Category>
    ): Expense? = withContext(Dispatchers.IO) {
        try {
            // Criar mapa de categorias para lookup rápido
            val categoryMap = categories.associateBy { it.idCategoria }
            
            // Construir lista de subcategorias com suas categorias para o prompt
            val subcategoriesText = subcategories.joinToString("\n") { sub ->
                val categoryName = categoryMap[sub.idCategoria]?.nomeCategoria ?: "Desconhecida"
                "  - ID: ${sub.idSubcategoria}, Subcategoria: ${sub.nomeSubcategoria}, Categoria: $categoryName"
            }
            
            val prompt = """
                Extract the following information from this bank SMS and return it as a JSON object matching these fields:
                - estabelecimento (string, merchant/establishment name)
                - valor (number, transaction amount, just the numeric value)
                - data_despesa (string, date in format YYYY-MM-DD)
                - id_subcategoria (string UUID, choose the MOST APPROPRIATE subcategory ID from the list below based on the establishment type and transaction context)
                
                AVAILABLE SUBCATEGORIES (choose the best match):
                $subcategoriesText
                
                IMPORTANT: 
                - Analyze the establishment name carefully to determine the best subcategory
                - Examples: supermarket → "Supermercado", restaurant → "Restaurante", gas station → "Combustivel", pharmacy → "Farmacia"
                - If unsure, choose the most general subcategory within the appropriate category
                - ALWAYS include id_subcategoria in your response
                - You MUST choose one of the subcategory IDs from the list above
                
                DATE HANDLING:
                - If the date in SMS is in format DD/MM (without year), use the CURRENT YEAR
                - If the date in SMS is in format DD/MM/YYYY, convert to YYYY-MM-DD
                - If no date is found, use the current date
                - Current year is ${java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)}
                
                SMS TEXT TO ANALYZE:
                "$smsBody"
                
                Return ONLY a raw JSON object with these 4 fields. Do not wrap it in markdown code blocks or any other formatting.
            """.trimIndent()

            val request = OpenAIRequest(
                model = "gpt-4o-mini",
                messages = listOf(
                    OpenAIMessage(
                        role = "system",
                        content = "You are a financial transaction classifier. Extract transaction details from bank SMS and classify them using the provided subcategory list. Always return valid JSON only, without any additional text or markdown formatting."
                    ),
                    OpenAIMessage(
                        role = "user",
                        content = prompt
                    )
                ),
                temperature = 0.2  // Lower temperature for more consistent classification
            )

            // Log de requisição à LLM
            val requestData = JSONObject().apply {
                put("model", "gpt-4o-mini")
                put("sms_body", smsBody.take(200))
                put("subcategories_count", subcategories.size)
            }.toString()
            ActivityLogManager.addLog(
                tipoAtividade = "llm_request",
                descricao = "Requisição enviada à LLM para processar SMS",
                dados = requestData,
                sucesso = true
            )

            val httpResponse = httpClient.post("https://api.openai.com/v1/chat/completions") {
                headers {
                    append(HttpHeaders.Authorization, "Bearer $apiKey")
                    contentType(ContentType.Application.Json)
                }
                setBody(request)
            }

            // Tentar desserializar a resposta
            val response: OpenAIResponse = try {
                httpResponse.body()
            } catch (e: JsonConvertException) {
                // Se falhar a desserialização, tentar pegar o erro da resposta
                val rawResponse = try {
                    httpResponse.bodyAsText()
                } catch (ex: Exception) {
                    "Não foi possível ler a resposta"
                }
                
                val errorMsg = try {
                    val errorJson = JSONObject(rawResponse)
                    errorJson.optJSONObject("error")?.optString("message") ?: rawResponse
                } catch (ex: Exception) {
                    rawResponse
                }
                
                ActivityLogManager.addLog(
                    tipoAtividade = "llm_response",
                    descricao = "Erro ao desserializar resposta da API OpenAI",
                    dados = JSONObject().apply {
                        put("error_message", errorMsg.take(500))
                        put("raw_response", rawResponse.take(500))
                    }.toString(),
                    sucesso = false,
                    erro = errorMsg.take(200)
                )
                android.util.Log.e("OpenAILlmService", "Failed to parse OpenAI response: $errorMsg")
                return@withContext null
            }

            // Verificar se há erro na resposta da API
            if (response.error != null) {
                val errorMsg = response.error.message
                ActivityLogManager.addLog(
                    tipoAtividade = "llm_response",
                    descricao = "Erro retornado pela API OpenAI",
                    dados = JSONObject().apply {
                        put("error_message", errorMsg)
                        put("error_type", response.error.type ?: "unknown")
                        put("error_code", response.error.code ?: "unknown")
                    }.toString(),
                    sucesso = false,
                    erro = errorMsg
                )
                android.util.Log.e("OpenAILlmService", "OpenAI API Error: $errorMsg")
                return@withContext null
            }

            val responseText = response.choices?.firstOrNull()?.message?.content ?: run {
                // Log de resposta vazia da LLM
                ActivityLogManager.addLog(
                    tipoAtividade = "llm_response",
                    descricao = "Resposta vazia da LLM",
                    dados = requestData,
                    sucesso = false,
                    erro = "Resposta da LLM não contém conteúdo"
                )
                return@withContext null
            }
            
            // Log de resposta da LLM
            val responseData = JSONObject().apply {
                put("response_text", responseText.take(500))
                put("model", "gpt-4o-mini")
            }.toString()
            ActivityLogManager.addLog(
                tipoAtividade = "llm_response",
                descricao = "Resposta recebida da LLM",
                dados = responseData,
                sucesso = true
            )
            
            // Parse JSON response - remover possível formatação markdown
            val jsonString = responseText.trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
            
            val jsonObj = JSONObject(jsonString)
            
            // Get subcategory ID from response
            val subcategoriaId = jsonObj.optString("id_subcategoria")
            
            // Verify that the subcategory ID is valid
            val subcategory = subcategories.find { it.idSubcategoria == subcategoriaId }
            if (subcategory == null) {
                android.util.Log.w("OpenAILlmService", "Invalid subcategory ID returned by LLM: $subcategoriaId")
                
                // Log de erro: subcategoria inválida
                ActivityLogManager.addLog(
                    tipoAtividade = "llm_response",
                    descricao = "Subcategoria inválida retornada pela LLM",
                    dados = responseData,
                    sucesso = false,
                    erro = "Subcategoria ID inválido: $subcategoriaId"
                )
                
                return@withContext null
            }
            
            // Get category name for the selected subcategory
            val category = categoryMap[subcategory.idCategoria]
            
            // Get date or use current date
            val dataDespesa = jsonObj.optString("data_despesa").ifBlank {
                val calendar = Calendar.getInstance()
                "%d-%02d-%02d".format(
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH) + 1,
                    calendar.get(Calendar.DAY_OF_MONTH)
                )
            }

            Expense(
                valor = jsonObj.optDouble("valor", 0.0),
                dataDespesa = dataDespesa,
                local = jsonObj.optString("estabelecimento", "Desconhecido"),
                idSubcategoria = subcategoriaId,
                detalhe = null,
                // Campos derivados de JOINs (para exibição)
                categoria = category?.nomeCategoria,
                subcategoria = subcategory.nomeSubcategoria
            )
        } catch (e: Exception) {
            android.util.Log.e("OpenAILlmService", "Error parsing SMS", e)
            e.printStackTrace()
            
            // Log de erro na requisição/processamento LLM
            val errorData = JSONObject().apply {
                put("sms_body", smsBody.take(200))
                put("error_type", e.javaClass.simpleName)
            }.toString()
            ActivityLogManager.addLog(
                tipoAtividade = "llm_request",
                descricao = "Erro ao processar SMS com LLM",
                dados = errorData,
                sucesso = false,
                erro = e.message ?: "Erro desconhecido"
            )
            
            null
        }
    }
    
    override suspend fun chatWithAgent(
        userMessage: String,
        context: ChatContext,
        messageHistory: List<ChatMessage>
    ): String = withContext(Dispatchers.IO) {
        try {
            // Construir contexto financeiro para o agente
            val categoryMap = context.categories.associateBy { it.idCategoria }
            val subcategoriesText = context.subcategories.joinToString("\n") { sub ->
                val categoryName = categoryMap[sub.idCategoria]?.nomeCategoria ?: "Desconhecida"
                "  - ID: ${sub.idSubcategoria}, Subcategoria: ${sub.nomeSubcategoria}, Categoria: $categoryName"
            }
            
            // Resumo de despesas recentes
            val recentExpensesText = if (context.recentExpenses.isNotEmpty()) {
                context.recentExpenses.take(10).joinToString("\n") { expense ->
                    "  - ${expense.local}: R$ ${String.format("%.2f", expense.valor ?: 0.0)} em ${expense.dataDespesa} (${expense.categoria ?: "N/A"})"
                }
            } else {
                "Nenhuma despesa recente"
            }
            
            // Resumo de metas
            val goalsText = if (context.currentMonthGoals.isNotEmpty()) {
                context.currentMonthGoals.joinToString("\n") { goal ->
                    val categoryName = categoryMap[goal.idCategoria]?.nomeCategoria ?: "Desconhecida"
                    "  - $categoryName: Meta R$ ${String.format("%.2f", goal.valorMeta ?: 0.0)}"
                }
            } else {
                "Nenhuma meta definida para este mês"
            }
            
            val systemPrompt = """
Você é um assistente financeiro inteligente que ajuda o usuário a gerenciar suas despesas e entender seus gastos.

SUAS CAPACIDADES:
1. Cadastrar despesas: Quando o usuário mencionar uma despesa (ex: "Gastei R$50 no supermercado hoje"), você deve extrair as informações e cadastrar. Use o formato JSON para cadastrar despesas.
2. Responder perguntas sobre dados financeiros: Você tem acesso aos dados do banco e pode responder sobre gastos, metas, categorias, etc.
3. Fornecer insights e análises sobre os gastos do usuário.

DADOS DISPONÍVEIS:
- Total gasto este mês (${context.month}/${context.year}): R$ ${String.format("%.2f", context.totalSpentThisMonth)}
- Despesas recentes:
$recentExpensesText

- Metas do mês atual:
$goalsText

- Categorias e Subcategorias disponíveis:
$subcategoriesText

INSTRUÇÕES PARA CADASTRO DE DESPESAS:
Quando o usuário mencionar uma despesa, você DEVE:
1. Identificar: estabelecimento, valor, data (se mencionada, senão use a data atual), e a subcategoria mais apropriada
2. Retornar APENAS um JSON no formato:
{
  "action": "register_expense",
  "estabelecimento": "nome do estabelecimento",
  "valor": valor_numerico,
  "data_despesa": "YYYY-MM-DD",
  "id_subcategoria": "uuid_da_subcategoria"
}

IMPORTANTE:
- Seja conversacional e amigável
- Use português brasileiro
- Quando cadastrar uma despesa, confirme ao usuário de forma clara
- Se não tiver certeza sobre alguma informação, pergunte ao usuário
- Para perguntas sobre dados, use as informações fornecidas acima
- Se o usuário pedir algo que você não pode fazer, explique educadamente
""".trimIndent()
            
            // Construir histórico de mensagens
            val messages = mutableListOf<OpenAIMessage>().apply {
                add(OpenAIMessage(role = "system", content = systemPrompt))
                // Adicionar histórico (últimas 10 mensagens para não exceder tokens)
                messageHistory.takeLast(10).forEach { msg ->
                    add(OpenAIMessage(role = msg.role, content = msg.content))
                }
                // Adicionar mensagem atual do usuário
                add(OpenAIMessage(role = "user", content = userMessage))
            }
            
            val request = OpenAIRequest(
                model = "gpt-4o-mini",
                messages = messages,
                temperature = 0.7
            )
            
            val httpResponse = httpClient.post("https://api.openai.com/v1/chat/completions") {
                headers {
                    append(HttpHeaders.Authorization, "Bearer $apiKey")
                    contentType(ContentType.Application.Json)
                }
                setBody(request)
            }
            
            val response: OpenAIResponse = try {
                httpResponse.body()
            } catch (e: JsonConvertException) {
                val rawResponse = try {
                    httpResponse.bodyAsText()
                } catch (ex: Exception) {
                    "Não foi possível ler a resposta"
                }
                
                val errorMsg = try {
                    val errorJson = JSONObject(rawResponse)
                    errorJson.optJSONObject("error")?.optString("message") ?: rawResponse
                } catch (ex: Exception) {
                    rawResponse
                }
                
                android.util.Log.e("OpenAILlmService", "Failed to parse OpenAI response: $errorMsg")
                return@withContext "Desculpe, ocorreu um erro ao processar sua mensagem. Tente novamente."
            }
            
            if (response.error != null) {
                android.util.Log.e("OpenAILlmService", "OpenAI API Error: ${response.error.message}")
                return@withContext "Desculpe, ocorreu um erro: ${response.error.message}"
            }
            
            val responseText = response.choices?.firstOrNull()?.message?.content 
                ?: "Desculpe, não consegui processar sua mensagem."
            
            responseText
        } catch (e: Exception) {
            android.util.Log.e("OpenAILlmService", "Error in chatWithAgent", e)
            e.printStackTrace()
            "Desculpe, ocorreu um erro inesperado. Tente novamente mais tarde."
        }
    }
}

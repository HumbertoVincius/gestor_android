package com.humberto.gestorfinanceiro.data.llm

import com.humberto.gestorfinanceiro.data.model.Category
import com.humberto.gestorfinanceiro.data.model.Expense
import com.humberto.gestorfinanceiro.data.model.Subcategory
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
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
}

@Serializable
data class OpenAIRequest(
    val model: String,
    val messages: List<OpenAIMessage>,
    val response_format: OpenAIResponseFormat? = null,
    val temperature: Double = 0.7
)

@Serializable
data class OpenAIMessage(
    val role: String,
    val content: String
)

@Serializable
data class OpenAIResponseFormat(
    val type: String = "json_object"
)

@Serializable
data class OpenAIResponse(
    val choices: List<OpenAIChoice>
)

@Serializable
data class OpenAIChoice(
    val message: OpenAIMessageResponse
)

@Serializable
data class OpenAIMessageResponse(
    val content: String
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
                - valor (number, transaction amount)
                - data_competencia (string, date in format YYYY-MM-DD, use current date if not found)
                - hora (string, time in format HH:MM, use "00:00" if not found)
                - id_subcategoria (string UUID, choose the MOST APPROPRIATE subcategory ID from the list below based on the establishment type and transaction context)
                - cartao (string, card name/brand if available, otherwise null)
                - final_cartao (number, last 4 digits of card if available, otherwise null)
                
                AVAILABLE SUBCATEGORIES (choose the best match):
                $subcategoriesText
                
                IMPORTANT: 
                - Analyze the establishment name carefully to determine the best subcategory
                - Examples: supermarket → "Supermercado", restaurant → "Restaurante", gas station → "Combustivel", pharmacy → "Farmacia"
                - If unsure, choose the most general subcategory within the appropriate category
                - ALWAYS include id_subcategoria in your response
                
                SMS TEXT TO ANALYZE:
                "$smsBody"
                
                Return ONLY a valid JSON object with the fields above, no additional text or explanation.
            """.trimIndent()

            val request = OpenAIRequest(
                model = "gpt-4o-mini",
                messages = listOf(
                    OpenAIMessage(
                        role = "system",
                        content = "You are a financial transaction classifier. Extract transaction details from bank SMS and classify them using the provided subcategory list. Always return valid JSON only."
                    ),
                    OpenAIMessage(
                        role = "user",
                        content = prompt
                    )
                ),
                response_format = OpenAIResponseFormat(type = "json_object"),
                temperature = 0.2  // Lower temperature for more consistent classification
            )

            val response: OpenAIResponse = httpClient.post("https://api.openai.com/v1/chat/completions") {
                headers {
                    append(HttpHeaders.Authorization, "Bearer $apiKey")
                    contentType(ContentType.Application.Json)
                }
                setBody(request)
            }.body()

            val responseText = response.choices.firstOrNull()?.message?.content ?: return@withContext null
            
            // Parse JSON response
            val jsonString = responseText.trim()
                .substringAfter("{")
                .substringBeforeLast("}")
            val jsonObj = JSONObject("{$jsonString}")
            
            // Get subcategory ID from response
            val subcategoriaId = jsonObj.optString("id_subcategoria")
            
            // Verify that the subcategory ID is valid
            val subcategory = subcategories.find { it.idSubcategoria == subcategoriaId }
            if (subcategory == null) {
                android.util.Log.w("OpenAILlmService", "Invalid subcategory ID returned by LLM: $subcategoriaId")
                return@withContext null
            }
            
            // Get category name for the selected subcategory
            val category = categoryMap[subcategory.idCategoria]
            
            // Get date or use current date
            val dataCompetencia = jsonObj.optString("data_competencia").ifBlank {
                val calendar = Calendar.getInstance()
                "%d-%02d-%02d".format(
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH) + 1,
                    calendar.get(Calendar.DAY_OF_MONTH)
                )
            }

            Expense(
                valor = jsonObj.optDouble("valor", 0.0),
                dataDespesa = dataCompetencia,
                local = jsonObj.optString("estabelecimento", "Desconhecido"),
                idSubcategoria = subcategoriaId,
                // Campos derivados para exibição
                dataCompetencia = dataCompetencia,
                estabelecimento = jsonObj.optString("estabelecimento", "Desconhecido"),
                categoria = category?.nomeCategoria,
                subcategoria = subcategory.nomeSubcategoria,
                hora = jsonObj.optString("hora", "00:00"),
                cartao = jsonObj.optString("cartao").ifBlank { null },
                finalCartao = jsonObj.optLong("final_cartao", 0).takeIf { it > 0 },
                statusTransacao = "Aprovada",
                mes = Calendar.getInstance().get(Calendar.MONTH).toLong() + 1
            )
        } catch (e: Exception) {
            android.util.Log.e("OpenAILlmService", "Error parsing SMS", e)
            e.printStackTrace()
            null
        }
    }
}

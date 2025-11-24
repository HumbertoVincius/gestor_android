package com.humberto.gestorfinanceiro.data.llm

import com.humberto.gestorfinanceiro.data.model.Expense
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
    suspend fun parseSms(smsBody: String): Expense?
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

    override suspend fun parseSms(smsBody: String): Expense? = withContext(Dispatchers.IO) {
        try {
            val prompt = """
                Extract the following information from this bank SMS and return it as a JSON object matching these fields:
                - estabelecimento (string, merchant name)
                - valor (number, amount)
                - data_competencia (string, format YYYY-MM-DD)
                - hora (string, HH:MM)
                - categoria (string, guess based on merchant e.g. Alimentacao, Transporte)
                - cartao (string, card name if available)
                - final_cartao (number, last 4 digits if available)
                
                SMS: "$smsBody"
                
                Return ONLY the JSON object, no additional text.
            """.trimIndent()

            val request = OpenAIRequest(
                model = "gpt-4o-mini",
                messages = listOf(
                    OpenAIMessage(
                        role = "system",
                        content = "You are a helpful assistant that extracts information from bank SMS messages and returns JSON. Always return valid JSON only."
                    ),
                    OpenAIMessage(
                        role = "user",
                        content = prompt
                    )
                ),
                response_format = OpenAIResponseFormat(type = "json_object"),
                temperature = 0.3
            )

            val response: OpenAIResponse = httpClient.post("https://api.openai.com/v1/chat/completions") {
                headers {
                    append(HttpHeaders.Authorization, "Bearer $apiKey")
                    contentType(ContentType.Application.Json)
                }
                setBody(request)
            }.body()

            val responseText = response.choices.firstOrNull()?.message?.content ?: return@withContext null
            
            // Limpar o texto da resposta para extrair apenas o JSON
            val jsonString = responseText.trim()
                .substringAfter("{")
                .substringBeforeLast("}")
            val json = JSONObject("{$jsonString}")
            
            // Calculate current month for 'mes' field
            val calendar = Calendar.getInstance()
            val currentMonth = (calendar.get(Calendar.MONTH) + 1).toLong()

            Expense(
                estabelecimento = json.optString("estabelecimento", "Desconhecido"),
                valor = json.optDouble("valor", 0.0),
                dataCompetencia = json.optString("data_competencia"),
                hora = json.optString("hora"),
                categoria = json.optString("categoria", "Outros"),
                cartao = json.optString("cartao"),
                finalCartao = json.optLong("final_cartao"),
                mes = currentMonth,
                statusTransacao = "Aprovada" // Default assumption
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

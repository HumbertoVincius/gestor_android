package com.humberto.gestorfinanceiro.data.llm

import com.google.ai.client.generativeai.GenerativeModel
import com.humberto.gestorfinanceiro.data.model.Expense
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.Calendar

interface LlmService {
    suspend fun parseSms(smsBody: String): Expense?
}

class GeminiLlmService(private val apiKey: String) : LlmService {
    
    private val generativeModel = GenerativeModel(
        modelName = "gemini-pro",
        apiKey = apiKey
    )

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
                
                Return ONLY the JSON.
            """.trimIndent()

            val response = generativeModel.generateContent(prompt)
            val responseText = response.text ?: return@withContext null
            
            val jsonString = responseText.substringAfter("{").substringBeforeLast("}")
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

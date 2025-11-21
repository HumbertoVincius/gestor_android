package com.humberto.gestorfinanceiro.data.llm

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.Date

data class Transaction(
    val amount: Double,
    val merchant: String,
    val date: Long,
    val category: String,
    val originalSms: String
)

interface LlmService {
    suspend fun parseSms(smsBody: String): Transaction?
}

class GeminiLlmService(private val apiKey: String) : LlmService {
    
    private val generativeModel = GenerativeModel(
        modelName = "gemini-pro",
        apiKey = apiKey
    )

    override suspend fun parseSms(smsBody: String): Transaction? = withContext(Dispatchers.IO) {
        try {
            val prompt = """
                Extract the following information from this bank SMS and return it as a JSON object:
                - amount (number, use dot for decimal)
                - merchant (string)
                - date (string, format YYYY-MM-DD)
                - category (string, guess based on merchant, e.g., Food, Transport, Shopping)
                
                SMS: "$smsBody"
                
                Return ONLY the JSON.
            """.trimIndent()

            val response = generativeModel.generateContent(prompt)
            val responseText = response.text ?: return@withContext null
            
            // Simple cleanup to ensure we get just JSON
            val jsonString = responseText.substringAfter("{").substringBeforeLast("}")
            val json = JSONObject("{$jsonString}")
            
            Transaction(
                amount = json.optDouble("amount", 0.0),
                merchant = json.optString("merchant", "Unknown"),
                date = System.currentTimeMillis(), // Simplified: Use current time for now, parsing date string is complex without a formatter
                category = json.optString("category", "Uncategorized"),
                originalSms = smsBody
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

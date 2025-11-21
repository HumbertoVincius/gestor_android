package com.humberto.gestorfinanceiro.data.supabase

import com.humberto.gestorfinanceiro.data.llm.Transaction
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

@Serializable
data class TransactionDto(
    val amount: Double,
    val merchant: String,
    val date: Long,
    val category: String,
    val original_sms: String
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

    suspend fun saveTransaction(transaction: Transaction) = withContext(Dispatchers.IO) {
        try {
            val dto = TransactionDto(
                amount = transaction.amount,
                merchant = transaction.merchant,
                date = transaction.date,
                category = transaction.category,
                original_sms = transaction.originalSms
            )
            client.postgrest["transactions"].insert(dto)
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }
    
    suspend fun getTransactions(): List<TransactionDto> = withContext(Dispatchers.IO) {
        try {
            client.postgrest["transactions"].select().decodeList<TransactionDto>()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}

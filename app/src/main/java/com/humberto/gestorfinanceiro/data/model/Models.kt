package com.humberto.gestorfinanceiro.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Expense(
    val id: String? = null, // uuid, nullable for insert
    val estabelecimento: String? = null,
    val valor: Double? = null,
    @SerialName("data_competencia") val dataCompetencia: String? = null,
    val hora: String? = null,
    val categoria: String? = null,
    val subcategoria: String? = null,
    val cartao: String? = null,
    @SerialName("final_cartao") val finalCartao: Long? = null,
    @SerialName("status_transacao") val statusTransacao: String? = null,
    val vencimento: String? = null,
    val mes: Long? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class Goal(
    val id: String? = null,
    val categoria: String? = null,
    @SerialName("valor_meta") val valorMeta: Long? = null,
    val mes: Long? = null,
    val ano: Long? = null,
    @SerialName("created_at") val createdAt: String? = null
)

package com.humberto.gestorfinanceiro.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Expense(
    @SerialName("id_despesa") val idDespesa: String? = null, // uuid, nullable for insert
    val valor: Double? = null, // numeric(10,2) - NOT NULL
    @SerialName("data_despesa") val dataDespesa: String? = null, // date - NOT NULL
    @SerialName("id_subcategoria") val idSubcategoria: String? = null, // uuid - NOT NULL
    val local: String? = null, // text - nullable (usado como estabelecimento)
    val detalhe: String? = null, // text - nullable
    
    // Campos calculados/derivados (podem vir de views ou joins)
    val estabelecimento: String? = null, // alias para 'local' ou vem de view
    @SerialName("data_competencia") val dataCompetencia: String? = null, // alias para 'data_despesa' ou vem de view
    val categoria: String? = null, // vem de join com subcategoria
    val subcategoria: String? = null, // vem de join com subcategoria
    val hora: String? = null, // pode vir de view ou ser null
    val cartao: String? = null, // pode vir de view ou ser null
    @SerialName("final_cartao") val finalCartao: Long? = null, // pode vir de view ou ser null
    @SerialName("status_transacao") val statusTransacao: String? = null, // pode vir de view ou ser null
    val vencimento: String? = null, // pode vir de view ou ser null
    val mes: Long? = null, // calculado
    @SerialName("created_at") val createdAt: String? = null // pode vir de view ou ser null
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

@Serializable
data class Category(
    @SerialName("id_categoria") val id: String? = null,
    val nome: String? = null,
    val descricao: String? = null
)

@Serializable
data class Subcategory(
    @SerialName("id_subcategoria") val id: String? = null,
    @SerialName("id_categoria") val idCategoria: String? = null,
    val nome: String? = null,
    val descricao: String? = null
)

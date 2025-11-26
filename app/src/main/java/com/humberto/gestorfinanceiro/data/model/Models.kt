package com.humberto.gestorfinanceiro.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Expense(
    @SerialName("id_despesa") val idDespesa: String? = null, // uuid, nullable for insert
    val valor: Double? = null, // numeric(10,2) - NOT NULL
    @SerialName("data_despesa") val dataDespesa: String? = null, // date - NOT NULL
    @SerialName("id_subcategoria") val idSubcategoria: String? = null, // uuid - NOT NULL
    val local: String? = null, // text - nullable (estabelecimento)
    val detalhe: String? = null, // text - nullable
    val visto: Boolean? = false, // Novo campo: false = não visto/novo
    
    // Campos derivados de JOINs (apenas para exibição, não são inseridos no banco)
    val categoria: String? = null, // vem de join: subcategoria -> categoria
    val subcategoria: String? = null, // vem de join: despesas -> subcategoria
    @SerialName("nome_categoria") val nomeCategoria: String? = null, // alias para categoria
    @SerialName("nome_subcategoria") val nomeSubcategoria: String? = null // alias para subcategoria
) {
    // Retorna apenas os campos que existem na tabela despesas para INSERT
    fun toInsertModel(): ExpenseInsert {
        return ExpenseInsert(
            valor = valor,
            dataDespesa = dataDespesa,
            idSubcategoria = idSubcategoria,
            local = local,
            detalhe = detalhe,
            visto = visto
        )
    }
}

@Serializable
data class ExpenseInsert(
    val valor: Double? = null,
    @SerialName("data_despesa") val dataDespesa: String? = null,
    @SerialName("id_subcategoria") val idSubcategoria: String? = null,
    val local: String? = null,
    val detalhe: String? = null,
    val visto: Boolean? = false
)

@Serializable
data class Goal(
    @SerialName("id_meta") val idMeta: String? = null,
    @SerialName("id_categoria") val idCategoria: String? = null,
    @SerialName("valor_meta") val valorMeta: Double? = null,
    val periodo: String? = null,
    @SerialName("data_inicio") val dataInicio: String? = null,
    
    // Novo campo (após migration 005)
    @SerialName("last_notified_threshold") val lastNotifiedThreshold: Int? = 0,
    
    // Campo antigo (compatibilidade com banco antes da migration 005)
    @SerialName("notified_80_percent") val notified80Percent: Boolean? = null,
    
    // Campos derivados de JOINs
    @SerialName("nome_categoria") val nomeCategoria: String? = null
) {
    // Método auxiliar para obter o threshold correto
    fun getEffectiveThreshold(): Int {
        // Se tem o novo campo, usar ele
        if (lastNotifiedThreshold != null && lastNotifiedThreshold > 0) {
            return lastNotifiedThreshold
        }
        // Se tem o campo antigo marcado como true, assumir 80%
        if (notified80Percent == true) {
            return 80
        }
        // Nunca notificou
        return 0
    }
}

@Serializable
data class Category(
    @SerialName("id_categoria") val idCategoria: String? = null,
    @SerialName("nome_categoria") val nomeCategoria: String? = null
)

@Serializable
data class Subcategory(
    @SerialName("id_subcategoria") val idSubcategoria: String? = null,
    @SerialName("id_categoria") val idCategoria: String? = null,
    @SerialName("nome_subcategoria") val nomeSubcategoria: String? = null
)

@Serializable
data class ActivityLog(
    @SerialName("id_log") val idLog: String? = null, // uuid, nullable for insert
    @SerialName("tipo_atividade") val tipoAtividade: String, // NOT NULL - 'sms_capture', 'llm_request', 'llm_response', 'db_insert'
    @SerialName("descricao") val descricao: String? = null, // text - nullable
    @SerialName("dados") val dados: String? = null, // jsonb - nullable, dados adicionais em JSON
    @SerialName("sucesso") val sucesso: Boolean = true, // boolean - NOT NULL, default true
    @SerialName("erro") val erro: String? = null, // text - nullable, mensagem de erro se houver
    @SerialName("created_at") val createdAt: String? = null // timestamp - nullable, auto-generated
)
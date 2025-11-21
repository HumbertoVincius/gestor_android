package com.humberto.gestorfinanceiro.data.model

enum class SortOrder {
    DATE_DESC,    // Data descendente (mais recente primeiro) - DEFAULT
    DATE_ASC,     // Data ascendente (mais antigo primeiro)
    VALUE_DESC,   // Valor descendente (maior primeiro)
    VALUE_ASC,    // Valor ascendente (menor primeiro)
    NAME_ASC,     // Nome ascendente (A-Z)
    NAME_DESC,    // Nome descendente (Z-A)
    CATEGORY_ASC, // Categoria ascendente (A-Z)
    CATEGORY_DESC // Categoria descendente (Z-A)
}


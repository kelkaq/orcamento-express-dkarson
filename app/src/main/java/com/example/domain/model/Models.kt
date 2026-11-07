package com.example.domain.model

enum class QuoteStatus(val label: String, val code: String) {
    PENDING("Pendente", "PENDING"),
    APPROVED("Aprovado", "APPROVED"),
    COMPLETED("Concluído", "COMPLETED"),
    REJECTED("Recusado", "REJECTED"),
    CANCELED("Cancelado", "CANCELED");

    companion object {
        fun fromCode(code: String): QuoteStatus {
            return values().firstOrNull { it.code == code } ?: PENDING
        }
    }
}

enum class ItemType(val label: String, val code: String) {
    SERVICE("Serviço", "SERVICE"),
    PRODUCT("Produto", "PRODUCT");

    companion object {
        fun fromCode(code: String): ItemType {
            return values().firstOrNull { it.code == code } ?: SERVICE
        }
    }
}

enum class DiscountType(val label: String, val code: String) {
    FIXED("R$", "FIXED"),
    PERCENTAGE("%", "PERCENTAGE");

    companion object {
        fun fromCode(code: String): DiscountType {
            return values().firstOrNull { it.code == code } ?: FIXED
        }
    }
}

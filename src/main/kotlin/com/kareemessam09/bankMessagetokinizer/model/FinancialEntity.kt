package com.kareemessam09.bankMessagetokinizer.model

data class FinancialEntity(
    val text: String,
    val label: EntityType,
    val startPosition: Int,
    val endPosition: Int,
    val confidence: Float
)

enum class EntityType {
    ACCOUNT,
    AMOUNT,
    BANK,
    CARD,
    DATE,
    MERCHANT,
    REF,
    TRANSACTION_TYPE,
    OTHER;

    companion object {
        fun fromBIOLabel(bioLabel: String): EntityType {
            return when (bioLabel.removePrefix("B-").removePrefix("I-")) {
                "ACCOUNT" -> ACCOUNT
                "AMOUNT" -> AMOUNT
                "BANK" -> BANK
                "CARD" -> CARD
                "DATE" -> DATE
                "MERCHANT" -> MERCHANT
                "REF" -> REF
                "TRANSACTION_TYPE" -> TRANSACTION_TYPE
                else -> OTHER
            }
        }
    }
}
package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "quotes")
data class QuoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val quoteNumber: String, // e.g. #ORC-2026-0001
    val clientId: Long? = null,
    val clientName: String = "",
    val clientDocument: String = "",
    val clientPhone: String = "",
    val clientEmail: String = "",
    val clientAddress: String = "",
    val issueDate: Long = System.currentTimeMillis(),
    val validUntil: Long = System.currentTimeMillis() + (15L * 24 * 60 * 60 * 1000),
    val status: String = "PENDING", // PENDING, APPROVED, COMPLETED, REJECTED, CANCELED
    val subtotal: Double = 0.0,
    val discountType: String = "FIXED", // FIXED or PERCENTAGE
    val discountValue: Double = 0.0,
    val discountCalculated: Double = 0.0,
    val shippingFee: Double = 0.0,
    val total: Double = 0.0,
    val paymentTerms: String = "",
    val paymentMethod: String = "PIX",
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

package com.example.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Custos ficam no dispositivo, nunca são impressos no orçamento do cliente. */
@Entity(
    tableName = "quote_costs",
    foreignKeys = [ForeignKey(
        entity = QuoteEntity::class, parentColumns = ["id"],
        childColumns = ["quoteId"], onDelete = ForeignKey.CASCADE
    )]
)
data class QuoteCostEntity(
    @PrimaryKey val quoteId: Long,
    val materialsCents: Long = 0,
    val laborCents: Long = 0,
    val travelCents: Long = 0,
    val otherCents: Long = 0,
    val updatedAt: Long = System.currentTimeMillis()
) {
    val totalCents: Long get() = materialsCents + laborCents + travelCents + otherCents
}

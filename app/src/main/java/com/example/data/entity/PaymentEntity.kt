package com.example.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Recebimento confirmado manualmente pelo prestador de serviço.
 * Cada lançamento possui um recibo independente e valor exato em centavos.
 * Não representa confirmação bancária automática.
 */
@Entity(
    tableName = "payments",
    foreignKeys = [ForeignKey(
        entity = QuoteEntity::class,
        parentColumns = ["id"],
        childColumns = ["quoteId"],
        onDelete = ForeignKey.RESTRICT
    )],
    indices = [Index(value = ["quoteId"])]
)
data class PaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val quoteId: Long,
    val amountCents: Long,
    val paidAt: Long = System.currentTimeMillis(),
    val method: String = "PIX",
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

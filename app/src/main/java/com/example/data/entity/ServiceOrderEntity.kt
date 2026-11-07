package com.example.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "service_orders",
    foreignKeys = [ForeignKey(
        entity = QuoteEntity::class,
        parentColumns = ["id"],
        childColumns = ["quoteId"],
        onDelete = ForeignKey.RESTRICT
    )],
    indices = [Index(value = ["quoteId"], unique = true)]
)
data class ServiceOrderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val quoteId: Long,
    val status: String = "SCHEDULED",
    val scheduledAt: Long? = null,
    val completedAt: Long? = null,
    val responsible: String = "",
    val description: String = "",
    val materials: String = "",
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

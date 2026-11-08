package com.example.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Compromissos e retornos comerciais persistidos offline, inclusive sem Pro. */
@Entity(tableName = "agenda_events", indices = [Index("scheduledAt"), Index("quoteId")])
data class AgendaEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val quoteId: Long? = null,
    val kind: String = "FOLLOW_UP", // FOLLOW_UP ou SERVICE
    val title: String,
    val clientName: String = "",
    val clientPhone: String = "",
    val scheduledAt: Long,
    val remindAt: Long? = null,
    val remindedAt: Long? = null,
    val status: String = "PENDING", // PENDING, DONE, CANCELED
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

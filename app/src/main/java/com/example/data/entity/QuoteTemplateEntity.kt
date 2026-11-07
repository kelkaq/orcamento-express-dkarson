package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "quote_templates")
data class QuoteTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val itemsJson: String,
    val paymentTerms: String = "",
    val paymentMethod: String = "PIX",
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

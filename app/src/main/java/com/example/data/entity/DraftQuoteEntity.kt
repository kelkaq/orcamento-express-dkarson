package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "quote_drafts")
data class DraftQuoteEntity(
    @PrimaryKey val draftKey: String,
    val payloadJson: String,
    val updatedAt: Long = System.currentTimeMillis()
)

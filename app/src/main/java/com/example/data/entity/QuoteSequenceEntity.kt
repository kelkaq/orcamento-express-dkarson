package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "quote_sequences")
data class QuoteSequenceEntity(
    @PrimaryKey val year: Int,
    val lastSequence: Int
)

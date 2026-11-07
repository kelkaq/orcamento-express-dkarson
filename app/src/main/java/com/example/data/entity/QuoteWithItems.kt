package com.example.data.entity

import androidx.room.Embedded
import androidx.room.Relation

data class QuoteWithItems(
    @Embedded val quote: QuoteEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "quoteId"
    )
    val items: List<QuoteItemEntity>
)

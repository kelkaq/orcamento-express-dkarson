package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "products_services")
data class ProductServiceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val type: String = "SERVICE", // SERVICE or PRODUCT
    val unit: String = "un", // un, hr, m², kg, dia, pç, etc.
    val unitPrice: Double = 0.0,
    val category: String = "Geral",
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

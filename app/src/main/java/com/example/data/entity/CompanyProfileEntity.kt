package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "company_profile")
data class CompanyProfileEntity(
    @PrimaryKey val id: Int = 1,
    val tradeName: String = "",
    val legalName: String = "",
    val document: String = "",
    val phone: String = "",
    val email: String = "",
    val address: String = "",
    val pixKey: String = "",
    val pixKeyType: String = "Chave",
    val logoPath: String? = null,
    val defaultNotes: String = "",
    val defaultPaymentTerms: String = "",
    val defaultValidityDays: Int = 15,
    val updatedAt: Long = System.currentTimeMillis()
)

package com.example.util

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Formatters {
    private val brazilianLocale = Locale.forLanguageTag("pt-BR")
    private val currencyFormat = NumberFormat.getCurrencyInstance(brazilianLocale)
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", brazilianLocale)
    private val dateTimeFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", brazilianLocale)

    fun formatCurrency(value: Double): String {
        return currencyFormat.format(value)
    }

    fun formatDate(timestamp: Long): String {
        return if (timestamp <= 0) "-" else dateFormat.format(Date(timestamp))
    }

    fun formatDateTime(timestamp: Long): String {
        return if (timestamp <= 0) "-" else dateTimeFormat.format(Date(timestamp))
    }

    fun formatPhone(phone: String): String {
        val digits = phone.filter { it.isDigit() }
        return when (digits.length) {
            11 -> "(${digits.substring(0, 2)}) ${digits.substring(2, 7)}-${digits.substring(7)}"
            10 -> "(${digits.substring(0, 2)}) ${digits.substring(2, 6)}-${digits.substring(6)}"
            else -> phone
        }
    }

    fun formatDocument(doc: String): String {
        val digits = doc.filter { it.isDigit() }
        return when (digits.length) {
            11 -> "${digits.substring(0, 3)}.${digits.substring(3, 6)}.${digits.substring(6, 9)}-${digits.substring(9)}"
            14 -> "${digits.substring(0, 2)}.${digits.substring(2, 5)}.${digits.substring(5, 8)}/${digits.substring(8, 12)}-${digits.substring(12)}"
            else -> doc
        }
    }
}

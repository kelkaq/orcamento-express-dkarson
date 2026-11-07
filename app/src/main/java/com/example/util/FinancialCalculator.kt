package com.example.util

import java.math.BigDecimal
import java.math.RoundingMode

object FinancialCalculator {
    private val ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
    private val HUNDRED = BigDecimal("100.00")

    fun calculateItemTotal(quantity: Double, unitPrice: Double): Double {
        if (quantity <= 0.0 || unitPrice <= 0.0) return 0.0
        val qtyBd = BigDecimal.valueOf(quantity)
        val priceBd = BigDecimal.valueOf(unitPrice)
        return qtyBd.multiply(priceBd).setScale(2, RoundingMode.HALF_UP).toDouble()
    }

    fun calculateSubtotal(itemTotals: List<Double>): Double {
        var sum = BigDecimal.ZERO
        for (item in itemTotals) {
            if (item > 0.0) {
                sum = sum.add(BigDecimal.valueOf(item))
            }
        }
        return sum.setScale(2, RoundingMode.HALF_UP).toDouble()
    }

    fun calculateDiscount(subtotal: Double, discountType: String, discountValue: Double): Double {
        if (subtotal <= 0.0 || discountValue <= 0.0) return 0.0
        val subtotalBd = BigDecimal.valueOf(subtotal).setScale(2, RoundingMode.HALF_UP)
        val discountValBd = BigDecimal.valueOf(discountValue)

        return if (discountType == "PERCENTAGE") {
            val cappedPercent = discountValBd.min(HUNDRED).max(BigDecimal.ZERO)
            subtotalBd.multiply(cappedPercent)
                .divide(HUNDRED, 2, RoundingMode.HALF_UP)
                .toDouble()
        } else {
            val fixedDiscount = discountValBd.setScale(2, RoundingMode.HALF_UP)
            fixedDiscount.min(subtotalBd).max(ZERO).toDouble()
        }
    }

    fun calculateTotal(subtotal: Double, discountCalculated: Double, shippingFee: Double): Double {
        val subtotalBd = BigDecimal.valueOf(maxOf(0.0, subtotal)).setScale(2, RoundingMode.HALF_UP)
        val discountBd = BigDecimal.valueOf(maxOf(0.0, discountCalculated)).setScale(2, RoundingMode.HALF_UP)
        val shippingBd = BigDecimal.valueOf(maxOf(0.0, shippingFee)).setScale(2, RoundingMode.HALF_UP)

        val netSubtotal = subtotalBd.subtract(discountBd).max(ZERO)
        return netSubtotal.add(shippingBd).setScale(2, RoundingMode.HALF_UP).toDouble()
    }
}

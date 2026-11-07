package com.example

import com.example.domain.model.QuoteStatus
import com.example.util.FinancialCalculator
import com.example.util.Formatters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testCurrencyFormatting() {
        val formatted = Formatters.formatCurrency(1250.50)
        assertTrue(formatted.contains("1.250,50") || formatted.contains("1250,50"))
    }

    @Test
    fun testPhoneFormatting() {
        assertEquals("(11) 98765-4321", Formatters.formatPhone("11987654321"))
        assertEquals("(11) 3333-4444", Formatters.formatPhone("1133334444"))
        assertEquals("123", Formatters.formatPhone("123"))
    }

    @Test
    fun testCpfCnpjFormatting() {
        assertEquals("123.456.789-01", Formatters.formatDocument("12345678901"))
        assertEquals("12.345.678/0001-90", Formatters.formatDocument("12345678000190"))
        assertEquals("invalid", Formatters.formatDocument("invalid"))
    }

    @Test
    fun testFinancialCalculatorItemTotal() {
        // Normal decimal precision
        assertEquals(308.63, FinancialCalculator.calculateItemTotal(2.5, 123.45), 0.001)
        // Fractional cents half-up rounding
        assertEquals(2.05, FinancialCalculator.calculateItemTotal(1.0, 2.045), 0.001)
        // Zero or negative handling
        assertEquals(0.0, FinancialCalculator.calculateItemTotal(0.0, 100.0), 0.001)
        assertEquals(0.0, FinancialCalculator.calculateItemTotal(2.0, -10.0), 0.001)
    }

    @Test
    fun testFinancialCalculatorSubtotal() {
        val items = listOf(10.50, 20.30, 45.20)
        assertEquals(76.00, FinancialCalculator.calculateSubtotal(items), 0.001)
        assertEquals(0.0, FinancialCalculator.calculateSubtotal(emptyList()), 0.001)
    }

    @Test
    fun testFinancialCalculatorDiscounts() {
        val subtotal = 500.00

        // Percentage Discount: 10%
        val discount10Pct = FinancialCalculator.calculateDiscount(subtotal, "PERCENTAGE", 10.0)
        assertEquals(50.00, discount10Pct, 0.001)

        // Percentage Discount: 100%
        val discount100Pct = FinancialCalculator.calculateDiscount(subtotal, "PERCENTAGE", 100.0)
        assertEquals(500.00, discount100Pct, 0.001)

        // Percentage Discount: capped at 100% (e.g. 150% typed by accident)
        val discountOverPct = FinancialCalculator.calculateDiscount(subtotal, "PERCENTAGE", 150.0)
        assertEquals(500.00, discountOverPct, 0.001)

        // Fixed Discount: R$ 80.00
        val discountFixed = FinancialCalculator.calculateDiscount(subtotal, "FIXED", 80.0)
        assertEquals(80.00, discountFixed, 0.001)

        // Fixed Discount: capped at subtotal (cannot be greater than subtotal)
        val discountFixedOver = FinancialCalculator.calculateDiscount(subtotal, "FIXED", 600.0)
        assertEquals(500.00, discountFixedOver, 0.001)

        // Negative discount input
        val discountNegative = FinancialCalculator.calculateDiscount(subtotal, "FIXED", -50.0)
        assertEquals(0.00, discountNegative, 0.001)
    }

    @Test
    fun testFinancialCalculatorTotal() {
        val subtotal = 500.00
        val discount = 50.00
        val shipping = 25.50

        val total = FinancialCalculator.calculateTotal(subtotal, discount, shipping)
        assertEquals(475.50, total, 0.001)

        // If discount equals subtotal, total is just shipping
        val totalFree = FinancialCalculator.calculateTotal(subtotal, 500.0, shipping)
        assertEquals(25.50, totalFree, 0.001)

        // If discount exceeds subtotal, total cannot be negative
        val totalCap = FinancialCalculator.calculateTotal(100.0, 150.0, 0.0)
        assertEquals(0.00, totalCap, 0.001)
    }

    @Test
    fun testQuoteStatusEnum() {
        assertEquals(QuoteStatus.PENDING, QuoteStatus.fromCode("PENDING"))
        assertEquals(QuoteStatus.APPROVED, QuoteStatus.fromCode("APPROVED"))
        assertEquals(QuoteStatus.COMPLETED, QuoteStatus.fromCode("COMPLETED"))
        assertEquals(QuoteStatus.REJECTED, QuoteStatus.fromCode("REJECTED"))
        assertEquals(QuoteStatus.CANCELED, QuoteStatus.fromCode("CANCELED"))
        assertEquals(QuoteStatus.PENDING, QuoteStatus.fromCode("UNKNOWN"))
    }
}

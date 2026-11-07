package com.example.util

import java.math.BigDecimal
import java.math.RoundingMode

/** Motor da calculadora, independente da interface e sem aritmética binária de valores monetários. */
data class CalculatorEngine(
    val display: String = "0",
    val stored: BigDecimal? = null,
    val operator: String? = null,
    val startNewEntry: Boolean = false,
    val error: String? = null
) {
    fun value(): BigDecimal? = if (error == null) display.replace(',', '.').toBigDecimalOrNull() else null

    fun press(key: String): CalculatorEngine {
        if (key == "C") return CalculatorEngine()
        if (error != null) return if (key.length == 1 && key[0].isDigit()) CalculatorEngine(display = key) else this

        if (key.length == 1 && key[0].isDigit()) {
            val next = if (startNewEntry || display == "0") key else if (display.count { it.isDigit() } < 15) display + key else display
            return copy(display = next, startNewEntry = false)
        }
        if (key == ",") {
            if (startNewEntry) return copy(display = "0,", startNewEntry = false)
            return if (display.contains(',')) this else copy(display = "$display,")
        }
        if (key == "⌫") {
            return if (startNewEntry) copy(display = "0", startNewEntry = false)
            else copy(display = display.dropLast(1).ifEmpty { "0" })
        }
        if (key == "±") {
            return if (display == "0") this else copy(display = if (display.startsWith('-')) display.drop(1) else "-$display")
        }
        if (key in listOf("+", "−", "×", "÷")) {
            val current = value() ?: return this
            val nextValue = if (stored != null && operator != null && !startNewEntry) {
                calculate(stored, current, operator) ?: return copy(display = "Erro", error = "Divisão por zero")
            } else current
            return copy(display = pretty(nextValue), stored = nextValue, operator = key, startNewEntry = true)
        }
        if (key == "=") {
            if (stored == null || operator == null) return this
            val current = value() ?: return this
            val result = calculate(stored, current, operator)
                ?: return copy(display = "Erro", error = "Divisão por zero")
            return copy(display = pretty(result), stored = null, operator = null, startNewEntry = true)
        }
        return this
    }

    companion object {
        private fun calculate(a: BigDecimal, b: BigDecimal, operator: String): BigDecimal? = when (operator) {
            "+" -> a.add(b)
            "−" -> a.subtract(b)
            "×" -> a.multiply(b)
            "÷" -> if (b.compareTo(BigDecimal.ZERO) == 0) null else a.divide(b, 8, RoundingMode.HALF_UP)
            else -> null
        }

        private fun pretty(value: BigDecimal): String = value.stripTrailingZeros().toPlainString().replace('.', ',')
    }
}

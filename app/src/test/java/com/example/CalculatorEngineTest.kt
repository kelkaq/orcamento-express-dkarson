package com.example

import com.example.util.CalculatorEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class CalculatorEngineTest {
    private fun calculate(vararg keys: String): CalculatorEngine =
        keys.fold(CalculatorEngine()) { state, key -> state.press(key) }

    @Test fun addThreeCosts() {
        assertEquals("275", calculate("1", "5", "0", "+", "8", "0", "+", "4", "5", "=").value()?.toPlainString())
    }

    @Test fun decimalMultiplication() {
        assertEquals("3", calculate("1", ",", "5", "×", "2", "=").value()?.toPlainString())
    }

    @Test fun divisionByZeroIsBlocked() {
        assertEquals("Divisão por zero", calculate("1", "÷", "0", "=").error)
    }

    @Test fun editingAndReset() {
        assertEquals("13", calculate("1", "2", "⌫", "3").value()?.toPlainString())
        assertEquals("0", calculate("1", "2", "C").display)
    }

    @Test fun subtractionAndNegativeResult() {
        assertEquals("-5", calculate("5", "−", "1", "0", "=").value()?.toPlainString())
    }
}

package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.util.CalculatorEngine
import java.math.BigDecimal
import java.math.RoundingMode

/** Calculadora local: o resultado volta diretamente para o campo que abriu o diálogo. */
@Composable
fun CalculatorDialog(
    onDismiss: () -> Unit,
    onUseResult: (Double) -> Unit
) {
    var calc by remember { mutableStateOf(CalculatorEngine()) }
    val rows = listOf(
        listOf("C", "⌫", "÷", "×"),
        listOf("7", "8", "9", "−"),
        listOf("4", "5", "6", "+"),
        listOf("1", "2", "3", "="),
        listOf("±", "0", ",", "")
    )
    val result = calc.value()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Calculadora rápida") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = calc.error ?: calc.display,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth()
                )
                if (calc.operator != null) Text("Operação: ${calc.operator}")
                Spacer(Modifier.height(4.dp))
                rows.forEach { keys ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        keys.forEach { key ->
                            if (key.isBlank()) {
                                Spacer(Modifier.weight(1f))
                            } else {
                                OutlinedButton(
                                    onClick = { calc = calc.press(key) },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                                ) { Text(key) }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = result != null && result.toDouble().isFinite() && result >= BigDecimal.ZERO,
                onClick = {
                    result?.let { onUseResult(it.setScale(2, RoundingMode.HALF_UP).toDouble()) }
                }
            ) { Text("Usar resultado") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

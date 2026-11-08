package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.example.pricing.PricingBreakdown
import com.example.pricing.PricingEngine
import com.example.pricing.TradeCatalog
import com.example.util.Formatters

data class PricedItemSuggestion(
    val description: String,
    val unit: String,
    val calculation: PricingBreakdown
)

/**
 * Profissões sugerem descrição/unidade. O usuário informa seus PRÓPRIOS custos.
 * Nenhum preço de referência inventado é apresentado.
 */
@Composable
fun PricingAssistantDialog(onDismiss: () -> Unit, onApply: (PricedItemSuggestion) -> Unit) {
    var selectedIndex by remember { mutableStateOf(0) }
    var expanded by remember { mutableStateOf(false) }
    val trade = TradeCatalog.templates[selectedIndex]
    var description by remember(selectedIndex) { mutableStateOf(trade.itemDescription) }
    var unit by remember(selectedIndex) { mutableStateOf(trade.unit) }
    var quantity by remember { mutableStateOf("1") }
    var materials by remember { mutableStateOf("") }
    var labor by remember { mutableStateOf("") }
    var travel by remember { mutableStateOf("") }
    var other by remember { mutableStateOf("") }
    var margin by remember { mutableStateOf("25") }

    fun number(s: String): Double? = s.trim().let {
        if (it.isBlank()) 0.0 else
            if (',' in it) it.replace(".", "").replace(',', '.').toDoubleOrNull()
            else it.toDoubleOrNull()
    }
    val result = runCatching {
        val q = number(quantity) ?: error("Quantidade inválida")
        val m = number(materials) ?: error("Materiais inválidos")
        val l = number(labor) ?: error("Mão de obra inválida")
        val t = number(travel) ?: error("Deslocamento inválido")
        val o = number(other) ?: error("Outros custos inválidos")
        val pct = number(margin) ?: error("Margem inválida")
        PricingEngine.calculate(q, m, l, t, o, pct)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Preço e lucro do serviço") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 530.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Text("Escolha sua atividade e informe seus próprios custos. O valor é calculado automaticamente.")
                OutlinedButton(
                    onClick = { expanded = true },
                    modifier = Modifier.fillMaxWidth().testTag("pricing_profession")
                ) { Text("Profissão: ${trade.name}") }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    TradeCatalog.templates.forEachIndexed { i, t ->
                        DropdownMenuItem(text = { Text(t.name) }, onClick = {
                            selectedIndex = i
                            expanded = false
                        })
                    }
                }
                OutlinedTextField(
                    value = description, onValueChange = { description = it },
                    label = { Text("Descrição do serviço") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = quantity, onValueChange = { quantity = it },
                        label = { Text("Quantidade") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true, modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = unit, onValueChange = { unit = it },
                        label = { Text("Unidade") },
                        singleLine = true, modifier = Modifier.weight(1f)
                    )
                }
                listOf(
                    Triple("Materiais (R$)", materials, { v: String -> materials = v }),
                    Triple("Mão de obra (R$)", labor, { v: String -> labor = v }),
                    Triple("Deslocamento (R$)", travel, { v: String -> travel = v }),
                    Triple("Outras despesas (R$)", other, { v: String -> other = v }),
                    Triple("Margem desejada (%)", margin, { v: String -> margin = v })
                ).forEach { (label, value, setter) ->
                    OutlinedTextField(
                        value = value, onValueChange = setter,
                        label = { Text(label) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                }
                result.onSuccess {
                    Text("Custo total: ${Formatters.formatCurrency(it.totalCost)}")
                    Text("Preço sugerido: ${Formatters.formatCurrency(it.recommendedTotal)}",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("pricing_recommended"))
                    Text("Lucro estimado: ${Formatters.formatCurrency(it.estimatedProfit)}")
                    Text("Preço unitário: ${Formatters.formatCurrency(it.unitPrice)}")
                    Text("Margem calculada sobre o preço de venda. Lucro antes de tributos e custos não informados.")
                }.onFailure {
                    Text(it.localizedMessage ?: "Informe custos para calcular.")
                }
            }
        },
        confirmButton = {
            Button(
                enabled = result.isSuccess && description.isNotBlank() && unit.isNotBlank(),
                onClick = {
                    result.getOrNull()?.let { calculation ->
                        onApply(PricedItemSuggestion(description.trim(), unit.trim(), calculation))
                    }
                },
                modifier = Modifier.testTag("pricing_apply_to_quote")
            ) { Text("Adicionar ao orçamento") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

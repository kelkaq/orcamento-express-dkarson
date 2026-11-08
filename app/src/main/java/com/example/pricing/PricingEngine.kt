package com.example.pricing

import java.math.BigDecimal
import java.math.RoundingMode

data class TradeTemplate(
    val id: String,
    val name: String,
    val itemDescription: String,
    val unit: String,
    val example: String
)

/** Apenas categorias/medidas; não há tabela de preços inventada para o usuário. */
object TradeCatalog {
    val templates = listOf(
        TradeTemplate("painting","Pintura","Pintura de paredes","m²","Informe a área em m²"),
        TradeTemplate("electric","Elétrica","Instalação de ponto elétrico","ponto","Informe quantidade de pontos"),
        TradeTemplate("plumbing","Hidráulica","Instalação hidráulica","ponto","Informe os pontos atendidos"),
        TradeTemplate("hvac","Ar-condicionado","Instalação de ar-condicionado","un","Informe quantidade de aparelhos"),
        TradeTemplate("clean","Limpeza","Limpeza técnica","m²","Informe a área"),
        TradeTemplate("welding","Solda","Serviço de soldagem","h","Informe horas de serviço"),
        TradeTemplate("repair","Manutenção","Manutenção e reparos","h","Informe as horas"),
        TradeTemplate("custom","Outro serviço","Serviço personalizado","un","Digite a descrição")
    )
}

data class PricingBreakdown(
    val quantity: Double,
    val materials: Double,
    val labor: Double,
    val travel: Double,
    val other: Double,
    val marginPercent: Double,
    val totalCost: Double,
    val recommendedTotal: Double,
    val unitPrice: Double,
    val estimatedProfit: Double
)

/**
 * Margem de lucro calculada SOBRE o preço da venda, e não markup sobre o custo.
 * Todos os valores são arredondados a centavos de forma determinística.
 */
object PricingEngine {
    fun cents(value: Double): Long {
        require(value.isFinite() && value >= 0 && value <= 100_000_000.0) {
            "Informe um custo válido entre R$ 0 e R$ 100 milhões."
        }
        return BigDecimal.valueOf(value).movePointRight(2)
            .setScale(0, RoundingMode.HALF_UP).longValueExact()
    }

    fun calculate(
        quantity: Double,
        materials: Double,
        labor: Double,
        travel: Double,
        other: Double,
        marginPercent: Double
    ): PricingBreakdown {
        require(quantity.isFinite() && quantity > 0 && quantity <= 1_000_000.0) {
            "Quantidade inválida."
        }
        require(marginPercent.isFinite() && marginPercent in 0.0..90.0) {
            "Use margem entre 0% e 90%."
        }
        val materialCents = cents(materials)
        val laborCents = cents(labor)
        val travelCents = cents(travel)
        val otherCents = cents(other)
        val costCents = Math.addExact(Math.addExact(materialCents, laborCents),
            Math.addExact(travelCents, otherCents))
        require(costCents > 0) { "Informe pelo menos um custo para calcular o preço." }

        val divisor = BigDecimal.ONE.subtract(
            BigDecimal.valueOf(marginPercent).divide(BigDecimal.valueOf(100))
        )
        val recommendedCents = BigDecimal.valueOf(costCents)
            .divide(divisor, 0, RoundingMode.CEILING).longValueExact()
        // Unidades arredondadas para cima evitam vender abaixo do preço calculado.
        val unitPrice = BigDecimal.valueOf(recommendedCents, 2)
            .divide(BigDecimal.valueOf(quantity), 2, RoundingMode.CEILING)
            .toDouble()
        val finalTotal = BigDecimal.valueOf(unitPrice)
            .multiply(BigDecimal.valueOf(quantity))
            .setScale(2, RoundingMode.HALF_UP)
            .toDouble()
        return PricingBreakdown(
            quantity, materials, labor, travel, other, marginPercent,
            costCents / 100.0, finalTotal, unitPrice,
            finalTotal - costCents / 100.0
        )
    }
}

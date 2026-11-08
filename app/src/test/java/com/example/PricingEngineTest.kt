package com.example

import com.example.pricing.PricingEngine
import com.example.pricing.TradeCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PricingEngineTest {
    @Test fun `margin is on sale price rather than a markup on cost`() {
        val result=PricingEngine.calculate(
            quantity=1.0,materials=180.0,labor=420.0,
            travel=50.0,other=0.0,marginPercent=25.0
        )
        assertEquals(650.0,result.totalCost,0.001)
        assertEquals(866.67,result.recommendedTotal,0.001)
        assertEquals(216.67,result.estimatedProfit,0.001)
    }
    @Test fun `unit rounding avoids selling below recommended total`() {
        val result=PricingEngine.calculate(3.0,10.0,0.0,0.0,0.0,20.0)
        assertEquals(12.51,result.recommendedTotal,0.001)
        assertTrue(result.unitPrice*3>=12.5)
    }
    @Test fun `invalid inputs and unsafe margins are rejected`() {
        assertTrue(runCatching { PricingEngine.calculate(0.0,100.0,0.0,0.0,0.0,25.0) }.isFailure)
        assertTrue(runCatching { PricingEngine.calculate(1.0,-1.0,0.0,0.0,0.0,25.0) }.isFailure)
        assertTrue(runCatching { PricingEngine.calculate(1.0,100.0,0.0,0.0,0.0,99.0) }.isFailure)
        assertEquals(8,TradeCatalog.templates.size)
    }
}

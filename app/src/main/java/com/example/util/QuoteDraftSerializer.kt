package com.example.util

import com.example.data.entity.QuoteItemEntity
import com.example.data.entity.QuoteCostEntity
import com.example.ui.viewmodel.QuoteEditorUiState
import org.json.JSONArray
import org.json.JSONObject

/**
 * Serialização local do formulário para recuperar rascunhos após interrupções.
 * Não armazena senhas nem envia dados para a rede.
 */
object QuoteDraftSerializer {
    fun encode(s: QuoteEditorUiState): String = JSONObject().apply {
        put("version", 1)
        put("quoteId", s.quoteId)
        put("quoteNumber", s.quoteNumber)
        put("clientId", s.clientId ?: JSONObject.NULL)
        put("clientName", s.clientName)
        put("clientDocument", s.clientDocument)
        put("clientPhone", s.clientPhone)
        put("clientEmail", s.clientEmail)
        put("clientAddress", s.clientAddress)
        put("issueDate", s.issueDate)
        put("validUntil", s.validUntil)
        put("status", s.status)
        put("discountType", s.discountType)
        put("discountValue", s.discountValue)
        put("shippingFee", s.shippingFee)
        put("paymentTerms", s.paymentTerms)
        put("paymentMethod", s.paymentMethod)
        put("notes", s.notes)
        put("estimatedCosts", s.estimatedCosts?.let { costs ->
            JSONObject().apply {
                put("materialsCents", costs.materialsCents)
                put("laborCents", costs.laborCents)
                put("travelCents", costs.travelCents)
                put("otherCents", costs.otherCents)
            }
        } ?: JSONObject.NULL)
        put("items", JSONArray().also { arr ->
            s.items.forEach { item ->
                arr.put(JSONObject().apply {
                    put("description", item.description)
                    put("type", item.type)
                    put("unit", item.unit)
                    put("quantity", item.quantity)
                    put("unitPrice", item.unitPrice)
                    put("productId", item.productId ?: JSONObject.NULL)
                })
            }
        })
    }.toString()

    fun decode(json: String, fallback: QuoteEditorUiState): QuoteEditorUiState {
        val root = JSONObject(json)
        require(root.getInt("version") == 1)
        // Evita aplicar rascunho de um orçamento sobre outro.
        require(root.getLong("quoteId") == fallback.quoteId)
        val itemsJson = root.getJSONArray("items")
        val items = (0 until itemsJson.length()).map { i ->
            val item = itemsJson.getJSONObject(i)
            QuoteItemEntity(
                quoteId = fallback.quoteId,
                productId = if (item.isNull("productId")) null else item.optLong("productId"),
                description = item.optString("description", ""),
                type = item.optString("type", "SERVICE"),
                unit = item.optString("unit", "un"),
                quantity = item.optDouble("quantity", 1.0),
                unitPrice = item.optDouble("unitPrice", 0.0),
                totalPrice = FinancialCalculator.calculateItemTotal(
                    item.optDouble("quantity", 1.0), item.optDouble("unitPrice", 0.0)
                )
            )
        }
        return fallback.copy(
            quoteNumber = root.optString("quoteNumber", fallback.quoteNumber),
            clientId = if (root.isNull("clientId")) null else root.optLong("clientId"),
            clientName = root.optString("clientName", ""),
            clientDocument = root.optString("clientDocument", ""),
            clientPhone = root.optString("clientPhone", ""),
            clientEmail = root.optString("clientEmail", ""),
            clientAddress = root.optString("clientAddress", ""),
            issueDate = root.optLong("issueDate", fallback.issueDate),
            validUntil = root.optLong("validUntil", fallback.validUntil),
            status = root.optString("status", "PENDING"),
            items = items,
            estimatedCosts = root.optJSONObject("estimatedCosts")?.let { obj ->
                QuoteCostEntity(
                    quoteId = fallback.quoteId,
                    materialsCents = obj.optLong("materialsCents", 0L),
                    laborCents = obj.optLong("laborCents", 0L),
                    travelCents = obj.optLong("travelCents", 0L),
                    otherCents = obj.optLong("otherCents", 0L)
                )
            },
            discountType = root.optString("discountType", "FIXED"),
            discountValue = root.optDouble("discountValue", 0.0),
            shippingFee = root.optDouble("shippingFee", 0.0),
            paymentTerms = root.optString("paymentTerms", ""),
            paymentMethod = root.optString("paymentMethod", "PIX"),
            notes = root.optString("notes", ""),
            draftRecovered = true
        )
    }
}

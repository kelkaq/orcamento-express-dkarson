package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.entity.ClientEntity
import com.example.data.entity.CompanyProfileEntity
import com.example.data.entity.ProductServiceEntity
import com.example.data.entity.QuoteEntity
import com.example.data.entity.QuoteItemEntity
import com.example.data.entity.QuoteCostEntity
import com.example.ui.components.PricedItemSuggestion
import com.example.pricing.PricingEngine
import com.example.billing.FreePlanLimitException
import com.example.billing.ProRequiredException
import com.example.data.entity.QuoteTemplateEntity
import com.example.util.QuoteDraftSerializer
import org.json.JSONArray
import com.example.data.entity.QuoteWithItems
import com.example.data.repository.QuoteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

data class QuoteEditorUiState(
    val quoteId: Long = 0,
    val quoteNumber: String = "",
    val clientId: Long? = null,
    val clientName: String = "",
    val clientDocument: String = "",
    val clientPhone: String = "",
    val clientEmail: String = "",
    val clientAddress: String = "",
    val issueDate: Long = System.currentTimeMillis(),
    val validUntil: Long = System.currentTimeMillis() + (15L * 24 * 60 * 60 * 1000),
    val status: String = "PENDING",
    val items: List<QuoteItemEntity> = emptyList(),
    val estimatedCosts: QuoteCostEntity? = null,
    val discountType: String = "FIXED", // FIXED or PERCENTAGE
    val discountValue: Double = 0.0,
    val shippingFee: Double = 0.0,
    val paymentTerms: String = "",
    val paymentMethod: String = "PIX",
    val notes: String = "",
    val subtotal: Double = 0.0,
    val discountCalculated: Double = 0.0,
    val total: Double = 0.0,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val requiresProUpgrade: Boolean = false,
    val isSavedSuccess: Boolean = false,
    val draftRecovered: Boolean = false,
    val savedQuoteId: Long? = null
)

@OptIn(kotlinx.coroutines.FlowPreview::class)
class QuoteEditorViewModel(
    private val repository: QuoteRepository,
    private val initialQuoteId: Long?
) : ViewModel() {

    private val _uiState = MutableStateFlow(QuoteEditorUiState())
    val uiState: StateFlow<QuoteEditorUiState> = _uiState.asStateFlow()

    val availableClients: StateFlow<List<ClientEntity>> = repository.allClients
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val availableCatalog: StateFlow<List<ProductServiceEntity>> = repository.allProductsServices
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val availableTemplates: StateFlow<List<QuoteTemplateEntity>> = repository.templates
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val draftKey = if (initialQuoteId != null && initialQuoteId > 0L)
        "edit_${initialQuoteId}" else "new"

    init {
        viewModelScope.launch {
            loadInitialData()
            repository.loadDraft(draftKey)?.let { json ->
                runCatching { QuoteDraftSerializer.decode(json, _uiState.value) }
                    .onSuccess { restored -> _uiState.value = restored }
            }
            recalculateTotals()
            // Coalesce rapid typing; preserve unfinished quote and avoid saving after final submit.
            _uiState.debounce(850).collectLatest { state ->
                if (!state.isSavedSuccess) {
                    repository.saveDraft(draftKey, QuoteDraftSerializer.encode(state))
                }
            }
        }
    }

    private suspend fun loadInitialData() {
            val company = repository.getCompanyProfile()
            if (initialQuoteId != null && initialQuoteId > 0) {
                val existing = repository.getQuoteWithItems(initialQuoteId)
                if (existing != null) {
                    val q = existing.quote
                    _uiState.value = QuoteEditorUiState(
                        quoteId = q.id,
                        quoteNumber = q.quoteNumber,
                        clientId = q.clientId,
                        clientName = q.clientName,
                        clientDocument = q.clientDocument,
                        clientPhone = q.clientPhone,
                        clientEmail = q.clientEmail,
                        clientAddress = q.clientAddress,
                        issueDate = q.issueDate,
                        validUntil = q.validUntil,
                        status = q.status,
                        items = existing.items,
                        estimatedCosts = repository.getQuoteCosts(q.id),
                        discountType = q.discountType,
                        discountValue = q.discountValue,
                        shippingFee = q.shippingFee,
                        paymentTerms = q.paymentTerms,
                        paymentMethod = q.paymentMethod,
                        notes = q.notes
                    )
                    recalculateTotals()
                    return
                }
            }

            // New Quote Setup
            val nextNumber = repository.generateNextQuoteNumber()
            val validDays = if (company.defaultValidityDays > 0) company.defaultValidityDays else 15
            val validUntilTime = System.currentTimeMillis() + (validDays.toLong() * 24 * 60 * 60 * 1000)

            _uiState.value = QuoteEditorUiState(
                quoteNumber = nextNumber,
                issueDate = System.currentTimeMillis(),
                validUntil = validUntilTime,
                paymentTerms = company.defaultPaymentTerms,
                notes = company.defaultNotes,
                items = listOf(
                    QuoteItemEntity(
                        quoteId = 0,
                        description = "",
                        type = "SERVICE",
                        unit = "un",
                        quantity = 1.0,
                        unitPrice = 0.0,
                        totalPrice = 0.0
                    )
                )
            )
            recalculateTotals()
    }

    fun onContactImported(name: String, phone: String) {
        _uiState.value = _uiState.value.copy(
            clientId = null,
            clientName = name.ifBlank { _uiState.value.clientName },
            clientPhone = phone.ifBlank { _uiState.value.clientPhone }
        )
    }

    fun applyTemplate(template: QuoteTemplateEntity) {
        val arr = runCatching { JSONArray(template.itemsJson) }.getOrNull() ?: return
        val items = mutableListOf<QuoteItemEntity>()
        for (i in 0 until arr.length()) {
            val row = arr.optJSONObject(i) ?: continue
            val quantity = row.optDouble("quantity", 1.0).coerceAtLeast(0.01)
            val price = row.optDouble("unitPrice", 0.0).coerceAtLeast(0.0)
            items.add(QuoteItemEntity(
                quoteId = _uiState.value.quoteId,
                description = row.optString("description", ""),
                type = row.optString("type", "SERVICE"),
                unit = row.optString("unit", "un"),
                quantity = quantity,
                unitPrice = price,
                totalPrice = com.example.util.FinancialCalculator.calculateItemTotal(quantity, price)
            ))
        }
        if (items.isEmpty()) return
        _uiState.value = _uiState.value.copy(
            items = items,
            paymentTerms = template.paymentTerms,
            paymentMethod = template.paymentMethod,
            notes = template.notes
        )
        recalculateTotals()
    }

    fun onClientSelected(client: ClientEntity) {
        _uiState.value = _uiState.value.copy(
            clientId = client.id,
            clientName = client.name,
            clientDocument = client.document,
            clientPhone = client.phone,
            clientEmail = client.email,
            clientAddress = client.address
        )
    }

    fun onClientNameChanged(name: String) {
        _uiState.value = _uiState.value.copy(clientName = name)
    }

    fun onClientDocumentChanged(doc: String) {
        _uiState.value = _uiState.value.copy(clientDocument = doc)
    }

    fun onClientPhoneChanged(phone: String) {
        _uiState.value = _uiState.value.copy(clientPhone = phone)
    }

    fun onClientEmailChanged(email: String) {
        _uiState.value = _uiState.value.copy(clientEmail = email)
    }

    fun onClientAddressChanged(address: String) {
        _uiState.value = _uiState.value.copy(clientAddress = address)
    }

    fun onQuoteNumberChanged(num: String) {
        _uiState.value = _uiState.value.copy(quoteNumber = num)
    }

    fun onValidityDaysPresetSelected(days: Int) {
        val cal = Calendar.getInstance()
        cal.timeInMillis = _uiState.value.issueDate
        cal.add(Calendar.DAY_OF_YEAR, days)
        _uiState.value = _uiState.value.copy(validUntil = cal.timeInMillis)
    }

    fun onCustomValidityDateSelected(timestamp: Long) {
        _uiState.value = _uiState.value.copy(validUntil = timestamp)
    }

    fun onStatusChanged(status: String) {
        _uiState.value = _uiState.value.copy(status = status)
    }

    fun onPaymentMethodChanged(method: String) {
        _uiState.value = _uiState.value.copy(paymentMethod = method)
    }

    fun onPaymentTermsChanged(terms: String) {
        _uiState.value = _uiState.value.copy(paymentTerms = terms)
    }

    fun onNotesChanged(notes: String) {
        _uiState.value = _uiState.value.copy(notes = notes)
    }

    fun onDiscountTypeChanged(type: String) {
        _uiState.value = _uiState.value.copy(discountType = type)
        recalculateTotals()
    }

    fun onDiscountValueChanged(value: Double) {
        _uiState.value = _uiState.value.copy(discountValue = maxOf(0.0, value))
        recalculateTotals()
    }

    fun onShippingFeeChanged(fee: Double) {
        _uiState.value = _uiState.value.copy(shippingFee = maxOf(0.0, fee))
        recalculateTotals()
    }

    /**
     * Cria item completo usando os custos informados pelo prestador,
     * acumulando estimativas que nunca aparecem no PDF do cliente.
     */
    fun addPricedItem(suggestion: PricedItemSuggestion) {
        val calculation = suggestion.calculation
        val items = _uiState.value.items.toMutableList()
        if (items.size == 1 && items.first().description.isBlank()) items.clear()
        items.add(QuoteItemEntity(
            quoteId = _uiState.value.quoteId,
            description = suggestion.description,
            unit = suggestion.unit,
            type = "SERVICE",
            quantity = calculation.quantity,
            unitPrice = calculation.unitPrice,
            totalPrice = com.example.util.FinancialCalculator.calculateItemTotal(
                calculation.quantity, calculation.unitPrice
            )
        ))
        val previous = _uiState.value.estimatedCosts
            ?: QuoteCostEntity(quoteId = _uiState.value.quoteId)
        _uiState.value = _uiState.value.copy(
            items = items,
            estimatedCosts = previous.copy(
                materialsCents = Math.addExact(previous.materialsCents, PricingEngine.cents(calculation.materials)),
                laborCents = Math.addExact(previous.laborCents, PricingEngine.cents(calculation.labor)),
                travelCents = Math.addExact(previous.travelCents, PricingEngine.cents(calculation.travel)),
                otherCents = Math.addExact(previous.otherCents, PricingEngine.cents(calculation.other))
            )
        )
        recalculateTotals()
    }

    // Items Manipulation
    fun addItem() {
        val currentItems = _uiState.value.items.toMutableList()
        currentItems.add(
            QuoteItemEntity(
                quoteId = _uiState.value.quoteId,
                description = "",
                type = "SERVICE",
                unit = "un",
                quantity = 1.0,
                unitPrice = 0.0,
                totalPrice = 0.0
            )
        )
        _uiState.value = _uiState.value.copy(items = currentItems)
        recalculateTotals()
    }

    fun addCatalogItem(catalogItem: ProductServiceEntity) {
        val currentItems = _uiState.value.items.toMutableList()
        // If single blank item exists, replace it
        if (currentItems.size == 1 && currentItems[0].description.isBlank() && currentItems[0].unitPrice == 0.0) {
            currentItems.clear()
        }
        currentItems.add(
            QuoteItemEntity(
                quoteId = _uiState.value.quoteId,
                productId = catalogItem.id,
                description = catalogItem.title,
                type = catalogItem.type,
                unit = catalogItem.unit,
                quantity = 1.0,
                unitPrice = catalogItem.unitPrice,
                totalPrice = catalogItem.unitPrice
            )
        )
        _uiState.value = _uiState.value.copy(items = currentItems)
        recalculateTotals()
    }

    fun updateItem(index: Int, updatedItem: QuoteItemEntity) {
        val currentItems = _uiState.value.items.toMutableList()
        if (index in currentItems.indices) {
            val safeQty = maxOf(0.01, updatedItem.quantity)
            val safePrice = maxOf(0.0, updatedItem.unitPrice)
            val total = com.example.util.FinancialCalculator.calculateItemTotal(safeQty, safePrice)
            currentItems[index] = updatedItem.copy(quantity = safeQty, unitPrice = safePrice, totalPrice = total)
            _uiState.value = _uiState.value.copy(items = currentItems)
            recalculateTotals()
        }
    }

    fun removeItem(index: Int) {
        val currentItems = _uiState.value.items.toMutableList()
        if (index in currentItems.indices) {
            currentItems.removeAt(index)
            if (currentItems.isEmpty()) {
                currentItems.add(
                    QuoteItemEntity(
                        quoteId = _uiState.value.quoteId,
                        description = "",
                        type = "SERVICE",
                        unit = "un",
                        quantity = 1.0,
                        unitPrice = 0.0,
                        totalPrice = 0.0
                    )
                )
            }
            _uiState.value = _uiState.value.copy(items = currentItems)
            recalculateTotals()
        }
    }

    private fun recalculateTotals() {
        val items = _uiState.value.items
        val itemTotals = items.map { com.example.util.FinancialCalculator.calculateItemTotal(it.quantity, it.unitPrice) }
        val subtotal = com.example.util.FinancialCalculator.calculateSubtotal(itemTotals)
        val discountCalc = com.example.util.FinancialCalculator.calculateDiscount(
            subtotal,
            _uiState.value.discountType,
            _uiState.value.discountValue
        )
        val total = com.example.util.FinancialCalculator.calculateTotal(
            subtotal,
            discountCalc,
            _uiState.value.shippingFee
        )

        _uiState.value = _uiState.value.copy(
            subtotal = subtotal,
            discountCalculated = discountCalc,
            total = total
        )
    }

    fun saveQuote(onSuccess: (Long) -> Unit) {
        val state = _uiState.value

        if (state.clientName.isBlank()) {
            _uiState.value = state.copy(errorMessage = "Por favor, informe o nome do cliente.")
            return
        }

        val validItems = state.items.filter { it.description.isNotBlank() }
        if (validItems.isEmpty()) {
            _uiState.value = state.copy(errorMessage = "Adicione pelo menos um item com descrição ao orçamento.")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, errorMessage = null)
            try {
                val quoteEntity = QuoteEntity(
                    id = state.quoteId,
                    quoteNumber = if (state.quoteNumber.isNotBlank()) state.quoteNumber else repository.generateNextQuoteNumber(),
                    clientId = state.clientId,
                    clientName = state.clientName.trim(),
                    clientDocument = state.clientDocument.trim(),
                    clientPhone = state.clientPhone.trim(),
                    clientEmail = state.clientEmail.trim(),
                    clientAddress = state.clientAddress.trim(),
                    issueDate = state.issueDate,
                    validUntil = state.validUntil,
                    status = state.status,
                    subtotal = state.subtotal,
                    discountType = state.discountType,
                    discountValue = state.discountValue,
                    discountCalculated = state.discountCalculated,
                    shippingFee = state.shippingFee,
                    total = state.total,
                    paymentTerms = state.paymentTerms.trim(),
                    paymentMethod = state.paymentMethod.trim(),
                    notes = state.notes.trim()
                )

                val savedId = repository.saveQuoteWithItems(quoteEntity, validItems, state.estimatedCosts)
                repository.clearDraft(draftKey)
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    isSavedSuccess = true,
                    savedQuoteId = savedId
                )
                onSuccess(savedId)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    requiresProUpgrade = e is FreePlanLimitException || e is ProRequiredException,
                    errorMessage = e.localizedMessage ?: "Não foi possível salvar o orçamento."
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
    fun clearProUpgrade() {
        _uiState.value = _uiState.value.copy(requiresProUpgrade = false)
    }
}

class QuoteEditorViewModelFactory(
    private val repository: QuoteRepository,
    private val initialQuoteId: Long?
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(QuoteEditorViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return QuoteEditorViewModel(repository, initialQuoteId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

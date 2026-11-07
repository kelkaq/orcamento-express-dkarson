package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.entity.ProductServiceEntity
import com.example.data.repository.QuoteRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CatalogFormState(
    val id: Long = 0,
    val title: String = "",
    val type: String = "SERVICE", // SERVICE or PRODUCT
    val unit: String = "un",
    val unitPrice: Double = 0.0,
    val category: String = "Geral",
    val description: String = "",
    val isOpen: Boolean = false,
    val errorMessage: String? = null
)

class CatalogViewModel(private val repository: QuoteRepository) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _selectedType = MutableStateFlow("ALL")
    val selectedType = _selectedType.asStateFlow()

    private val _formState = MutableStateFlow(CatalogFormState())
    val formState = _formState.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val itemsList: StateFlow<List<ProductServiceEntity>> = combine(
        _selectedType,
        _searchQuery
    ) { type, query ->
        Pair(type, query)
    }.flatMapLatest { (type, query) ->
        if (query.isNotBlank()) {
            repository.searchProductsServices(query)
        } else {
            repository.getProductsServicesByType(type)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun onTypeFilterChanged(type: String) {
        _selectedType.value = type
    }

    fun openNewItemDialog(defaultType: String = "SERVICE") {
        _formState.value = CatalogFormState(
            type = defaultType,
            unit = if (defaultType == "SERVICE") "hr" else "un",
            isOpen = true
        )
    }

    fun openEditItemDialog(item: ProductServiceEntity) {
        _formState.value = CatalogFormState(
            id = item.id,
            title = item.title,
            type = item.type,
            unit = item.unit,
            unitPrice = item.unitPrice,
            category = item.category,
            description = item.description,
            isOpen = true
        )
    }

    fun closeDialog() {
        _formState.value = CatalogFormState(isOpen = false)
    }

    fun updateForm(
        title: String = _formState.value.title,
        type: String = _formState.value.type,
        unit: String = _formState.value.unit,
        unitPrice: Double = _formState.value.unitPrice,
        category: String = _formState.value.category,
        description: String = _formState.value.description
    ) {
        _formState.value = _formState.value.copy(
            title = title,
            type = type,
            unit = unit,
            unitPrice = unitPrice,
            category = category,
            description = description,
            errorMessage = null
        )
    }

    fun saveItem(onSuccess: (() -> Unit)? = null) {
        val form = _formState.value
        if (form.title.isBlank()) {
            _formState.value = form.copy(errorMessage = "Título do item é obrigatório.")
            return
        }
        if (form.unitPrice < 0) {
            _formState.value = form.copy(errorMessage = "Preço unitário não pode ser negativo.")
            return
        }

        viewModelScope.launch {
            val entity = ProductServiceEntity(
                id = form.id,
                title = form.title.trim(),
                type = form.type,
                unit = form.unit.trim().ifBlank { "un" },
                unitPrice = form.unitPrice,
                category = form.category.trim().ifBlank { "Geral" },
                description = form.description.trim()
            )
            repository.saveProductService(entity)
            closeDialog()
            onSuccess?.invoke()
        }
    }

    fun deleteItem(item: ProductServiceEntity) {
        viewModelScope.launch {
            repository.deleteProductService(item)
        }
    }
}

class CatalogViewModelFactory(private val repository: QuoteRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CatalogViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return CatalogViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

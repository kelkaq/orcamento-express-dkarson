package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.entity.QuoteEntity
import com.example.data.entity.QuoteWithItems
import com.example.data.repository.DashboardMetrics
import com.example.data.repository.QuoteRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class QuotesViewModel(private val repository: QuoteRepository) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _selectedStatus = MutableStateFlow("ALL")
    val selectedStatus = _selectedStatus.asStateFlow()

    private val _period = MutableStateFlow("ALL")
    val period = _period.asStateFlow()
    @OptIn(ExperimentalCoroutinesApi::class)
    val metrics: StateFlow<DashboardMetrics> = _period.flatMapLatest { currentPeriod ->
        repository.dashboardMetricsByPeriod(currentPeriod)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardMetrics()
    )

    val paidQuoteIds: StateFlow<Set<Long>> = repository.allPayments
        .map { entries -> entries.map { it.quoteId }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    private val _operationError = MutableStateFlow<String?>(null)
    val operationError = _operationError.asStateFlow()
    fun clearOperationError() { _operationError.value = null }
    fun onPeriodChanged(value: String) {
        if (value in listOf("ALL", "MONTH", "LAST_MONTH", "YEAR")) _period.value = value
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val quotesList: StateFlow<List<QuoteWithItems>> = combine(
        _selectedStatus,
        _searchQuery
    ) { status, query ->
        Pair(status, query)
    }.flatMapLatest { (status, query) ->
        repository.getFilteredQuotes(status, query)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun onSearchQueryChanged(newQuery: String) {
        _searchQuery.value = newQuery
    }

    fun onStatusFilterChanged(status: String) {
        _selectedStatus.value = status
    }

    // Mantido para compatibilidade com a navegação existente. Métricas agora são reativas.
    fun refreshMetrics() = Unit

    fun updateStatus(quoteId: Long, newStatus: String) {
        viewModelScope.launch {
            try {
                repository.updateQuoteStatus(quoteId, newStatus)
                refreshMetrics()
            } catch (e: Exception) {
                _operationError.value = e.localizedMessage ?: "Não foi possível mudar a situação."
            }
        }
    }

    fun deleteQuote(quote: QuoteEntity) {
        viewModelScope.launch {
            try {
                repository.deleteQuote(quote)
                refreshMetrics()
            } catch (e: Exception) {
                _operationError.value = e.localizedMessage ?: "Não foi possível excluir o orçamento."
            }
        }
    }

    fun duplicateQuote(sourceQuoteId: Long, onDuplicated: (Long) -> Unit) {
        viewModelScope.launch {
            try {
                val newId = repository.duplicateQuote(sourceQuoteId)
                refreshMetrics()
                if (newId > 0) onDuplicated(newId)
            } catch (e: Exception) {
                _operationError.value = e.localizedMessage ?: "Não foi possível duplicar o orçamento."
            }
        }
    }
}

class QuotesViewModelFactory(private val repository: QuoteRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(QuotesViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return QuotesViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

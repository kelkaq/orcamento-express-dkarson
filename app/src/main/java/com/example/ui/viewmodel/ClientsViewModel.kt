package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.entity.ClientEntity
import com.example.data.repository.QuoteRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ClientFormState(
    val id: Long = 0,
    val name: String = "",
    val document: String = "",
    val phone: String = "",
    val email: String = "",
    val address: String = "",
    val notes: String = "",
    val isOpen: Boolean = false,
    val errorMessage: String? = null
)

class ClientsViewModel(private val repository: QuoteRepository) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _formState = MutableStateFlow(ClientFormState())
    val formState = _formState.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val clientsList: StateFlow<List<ClientEntity>> = _searchQuery.flatMapLatest { query ->
        if (query.isNotBlank()) repository.searchClients(query)
        else repository.allClients
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun openNewClientDialog() {
        _formState.value = ClientFormState(isOpen = true)
    }

    fun openEditClientDialog(client: ClientEntity) {
        _formState.value = ClientFormState(
            id = client.id,
            name = client.name,
            document = client.document,
            phone = client.phone,
            email = client.email,
            address = client.address,
            notes = client.notes,
            isOpen = true
        )
    }

    fun closeDialog() {
        _formState.value = ClientFormState(isOpen = false)
    }

    fun updateForm(
        name: String = _formState.value.name,
        document: String = _formState.value.document,
        phone: String = _formState.value.phone,
        email: String = _formState.value.email,
        address: String = _formState.value.address,
        notes: String = _formState.value.notes
    ) {
        _formState.value = _formState.value.copy(
            name = name,
            document = document,
            phone = phone,
            email = email,
            address = address,
            notes = notes,
            errorMessage = null
        )
    }

    fun saveClient(onSuccess: (() -> Unit)? = null) {
        val form = _formState.value
        if (form.name.isBlank()) {
            _formState.value = form.copy(errorMessage = "Nome do cliente é obrigatório.")
            return
        }

        viewModelScope.launch {
            val entity = ClientEntity(
                id = form.id,
                name = form.name.trim(),
                document = form.document.trim(),
                phone = form.phone.trim(),
                email = form.email.trim(),
                address = form.address.trim(),
                notes = form.notes.trim()
            )
            repository.saveClient(entity)
            closeDialog()
            onSuccess?.invoke()
        }
    }

    fun deleteClient(client: ClientEntity) {
        viewModelScope.launch {
            repository.deleteClient(client)
        }
    }
}

class ClientsViewModelFactory(private val repository: QuoteRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ClientsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ClientsViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

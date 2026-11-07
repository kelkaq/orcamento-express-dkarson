package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.entity.CompanyProfileEntity
import com.example.data.repository.QuoteRepository
import com.example.util.BackupManager
import com.example.util.RestoreResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class CompanyViewModel(private val repository: QuoteRepository) : ViewModel() {

    val companyProfile: StateFlow<CompanyProfileEntity> = repository.companyProfileFlow
        .map { it ?: CompanyProfileEntity() }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            CompanyProfileEntity()
        )

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage = _statusMessage.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing = _isProcessing.asStateFlow()

    fun updateProfile(profile: CompanyProfileEntity) {
        viewModelScope.launch {
            repository.updateCompanyProfile(profile)
            _statusMessage.value = "Dados da empresa atualizados com sucesso!"
        }
    }

    fun saveLogoFromUri(context: Context, imageUri: Uri) {
        viewModelScope.launch {
            try {
                _isProcessing.value = true
                val logoDir = File(context.filesDir, "branding").apply { mkdirs() }
                val targetFile = File(logoDir, "company_logo_${System.currentTimeMillis()}.png")

                context.contentResolver.openInputStream(imageUri)?.use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }

                val current = repository.getCompanyProfile()
                repository.updateCompanyProfile(current.copy(logoPath = targetFile.absolutePath))
                _statusMessage.value = "Logotipo atualizado com sucesso!"
            } catch (e: Exception) {
                _errorMessage.value = "Falha ao salvar logotipo: ${e.localizedMessage}"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun removeLogo() {
        viewModelScope.launch {
            val current = repository.getCompanyProfile()
            current.logoPath?.let { path ->
                try { File(path).delete() } catch (_: Exception) {}
            }
            repository.updateCompanyProfile(current.copy(logoPath = null))
            _statusMessage.value = "Logotipo removido."
        }
    }

    fun exportBackup(context: Context, onReadyToShare: (File, Uri) -> Unit) {
        viewModelScope.launch {
            try {
                _isProcessing.value = true
                val file = BackupManager.exportBackup(context, repository)
                val uri = BackupManager.getBackupUri(context, file)
                _statusMessage.value = "Backup exportado com sucesso!"
                onReadyToShare(file, uri)
            } catch (e: Exception) {
                _errorMessage.value = "Falha ao gerar backup: ${e.localizedMessage}"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun validateBackup(context: Context, uri: Uri): com.example.util.BackupValidationResult {
        return BackupManager.validateBackup(context, uri)
    }

    fun restoreBackup(
        context: Context,
        backupUri: Uri,
        replaceExisting: Boolean,
        onComplete: (RestoreResult) -> Unit
    ) {
        viewModelScope.launch {
            try {
                _isProcessing.value = true
                val result = BackupManager.restoreFromUri(context, backupUri, repository, replaceExisting)
                if (result.success) {
                    val modeStr = if (replaceExisting) "substituídos" else "mesclados"
                    val prevMsg = if (result.preventiveBackupPath != null) " (Backup preventivo de segurança criado)" else ""
                    _statusMessage.value = "Dados $modeStr com sucesso: ${result.quotesCount} orçamentos, ${result.clientsCount} clientes, ${result.productsCount} itens.$prevMsg"
                } else {
                    _errorMessage.value = result.errorMessage ?: "Erro desconhecido ao restaurar backup."
                }
                onComplete(result)
            } catch (e: Exception) {
                _errorMessage.value = "Erro ao processar arquivo: ${e.localizedMessage}"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun clearMessages() {
        _statusMessage.value = null
        _errorMessage.value = null
    }
}

class CompanyViewModelFactory(private val repository: QuoteRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CompanyViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return CompanyViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

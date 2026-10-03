package com.gitcodera.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gitcodera.data.model.CodespacesSecret
import com.gitcodera.domain.repository.GitHubRepository
import com.gitcodera.presentation.toDisplayMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val secrets: List<CodespacesSecret> = emptyList(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: GitHubRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = mutableState.asStateFlow()

    fun loadSecrets() {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(isLoading = true, error = null)
            try {
                mutableState.value = mutableState.value.copy(
                    secrets = repository.getCodespacesSecrets(),
                    isLoading = false,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.value = mutableState.value.copy(
                    isLoading = false,
                    error = error.toDisplayMessage("Could not load Codespaces secrets."),
                )
            }
        }
    }

    fun saveSecret(name: String, value: String, repositoryId: Long) {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(isSaving = true, error = null, message = null)
            try {
                repository.saveCodespacesSecret(name, value, repositoryId)
                mutableState.value = mutableState.value.copy(
                    isSaving = false,
                    message = "$name is now available to Codespaces for the selected repository.",
                )
                loadSecrets()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.value = mutableState.value.copy(
                    isSaving = false,
                    error = error.toDisplayMessage("Could not save Codespaces secret."),
                )
            }
        }
    }

    fun deleteSecret(name: String) {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(isSaving = true, error = null, message = null)
            try {
                repository.deleteCodespacesSecret(name)
                mutableState.value = mutableState.value.copy(
                    secrets = mutableState.value.secrets.filterNot { it.name == name },
                    isSaving = false,
                    message = "$name was deleted.",
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.value = mutableState.value.copy(
                    isSaving = false,
                    error = error.toDisplayMessage("Could not delete Codespaces secret."),
                )
            }
        }
    }

    fun clearMessage() {
        mutableState.value = mutableState.value.copy(message = null, error = null)
    }
}

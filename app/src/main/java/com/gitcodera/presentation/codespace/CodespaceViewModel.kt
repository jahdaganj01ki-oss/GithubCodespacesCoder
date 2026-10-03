package com.gitcodera.presentation.codespace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gitcodera.data.model.Codespace
import com.gitcodera.domain.repository.GitHubRepository
import com.gitcodera.presentation.toDisplayMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CodespaceState(
    val codespaces: List<Codespace> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val message: String? = null,
)

@HiltViewModel
class CodespaceViewModel @Inject constructor(
    private val repository: GitHubRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(CodespaceState())
    val state: StateFlow<CodespaceState> = mutableState.asStateFlow()

    fun load() {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(isLoading = true, error = null)
            try {
                mutableState.value = mutableState.value.copy(
                    codespaces = repository.getCodespaces(),
                    isLoading = false,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.value = mutableState.value.copy(
                    isLoading = false,
                    error = error.toDisplayMessage("Could not load Codespaces."),
                )
            }
        }
    }

    fun create(repositoryFullName: String) {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(isLoading = true, error = null, message = null)
            try {
                val created = repository.createCodespace(repositoryFullName)
                mutableState.value = mutableState.value.copy(
                    codespaces = listOf(created) + mutableState.value.codespaces,
                    isLoading = false,
                    message = "Codespace creation requested for $repositoryFullName.",
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.value = mutableState.value.copy(
                    isLoading = false,
                    error = error.toDisplayMessage("Could not create Codespace."),
                )
            }
        }
    }

    fun delete(name: String) {
        viewModelScope.launch {
            try {
                mutableState.value = mutableState.value.copy(isLoading = true, error = null, message = null)
                repository.deleteCodespace(name)
                mutableState.value = mutableState.value.copy(message = "Deletion requested for $name.")
                load()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.value = mutableState.value.copy(
                    error = error.toDisplayMessage("Could not delete Codespace."),
                )
            }
        }
    }

    fun start(name: String) {
        changeState(name) { repository.startCodespace(it) }
    }

    fun stop(name: String) {
        changeState(name) { repository.stopCodespace(it) }
    }

    private fun changeState(name: String, action: suspend (String) -> Unit) {
        viewModelScope.launch {
            try {
                mutableState.value = mutableState.value.copy(isLoading = true, error = null, message = null)
                action(name)
                mutableState.value = mutableState.value.copy(message = "Lifecycle action requested for $name.")
                load()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.value = mutableState.value.copy(
                    error = error.toDisplayMessage("Could not update Codespace."),
                )
            }
        }
    }

    fun clearError() {
        mutableState.value = mutableState.value.copy(error = null, message = null)
    }

    fun resetAll() {
        mutableState.value = CodespaceState()
    }
}

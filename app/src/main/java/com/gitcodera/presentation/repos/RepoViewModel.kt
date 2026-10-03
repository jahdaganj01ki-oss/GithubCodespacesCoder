package com.gitcodera.presentation.repos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gitcodera.data.model.Repository
import com.gitcodera.domain.repository.GitHubRepository
import com.gitcodera.presentation.toDisplayMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RepoState(
    val repositories: List<Repository> = emptyList(),
    val isLoading: Boolean = false,
    val isCreating: Boolean = false,
    val error: String? = null,
    val message: String? = null,
)

@HiltViewModel
class RepoViewModel @Inject constructor(
    private val repository: GitHubRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(RepoState())
    val state: StateFlow<RepoState> = mutableState.asStateFlow()

    fun load() {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(isLoading = true, error = null)
            try {
                mutableState.value = mutableState.value.copy(
                    repositories = repository.getRepositories(),
                    isLoading = false,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.value = mutableState.value.copy(
                    isLoading = false,
                    error = error.toDisplayMessage("Could not load repositories."),
                )
            }
        }
    }

    fun create(
        name: String,
        description: String,
        isPrivate: Boolean,
        initializeReadme: Boolean,
        gitignore: String,
        license: String,
    ) {
        if (!isValidRepositoryName(name.trim())) {
            mutableState.value = mutableState.value.copy(
                error = "Repository name must be 1-100 characters and use only letters, numbers, '.', '_' or '-'.",
            )
            return
        }
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(isCreating = true, error = null)
            try {
                val created = repository.createRepository(
                    name.trim(),
                    description.trim(),
                    isPrivate,
                    initializeReadme,
                    gitignore.trim(),
                    license.trim(),
                )
                mutableState.value = mutableState.value.copy(
                    repositories = listOf(created) + mutableState.value.repositories,
                    isCreating = false,
                    message = "Created ${created.fullName}",
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.value = mutableState.value.copy(
                    isCreating = false,
                    error = error.toDisplayMessage("Could not create repository."),
                )
            }
        }
    }

    fun clearMessages() {
        mutableState.value = mutableState.value.copy(error = null, message = null)
    }

    fun resetAll() {
        mutableState.value = RepoState()
    }
}

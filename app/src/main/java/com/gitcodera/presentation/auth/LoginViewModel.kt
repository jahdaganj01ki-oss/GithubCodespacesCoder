package com.gitcodera.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gitcodera.data.model.User
import com.gitcodera.domain.repository.DeviceAuthorization
import com.gitcodera.domain.repository.GitHubRepository
import com.gitcodera.presentation.toDisplayMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LoginState(
    val isLoading: Boolean = true,
    val user: User? = null,
    val authorization: DeviceAuthorization? = null,
    val isAuthorizing: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val repository: GitHubRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(LoginState())
    val state: StateFlow<LoginState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
                try {
                    repository.restoreSession()
                    mutableState.value = LoginState(
                        isLoading = false,
                        user = repository.currentUser.value,
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    mutableState.value = LoginState(
                        isLoading = false,
                        error = error.toDisplayMessage("Could not restore GitHub session."),
                    )
                }
                repository.currentUser.collect { user ->
                    mutableState.value = mutableState.value.copy(user = user)
            }
        }
    }

    fun startLogin() {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(isLoading = true, error = null)
            try {
                val authorization = repository.requestDeviceCode()
                mutableState.value = mutableState.value.copy(
                    isLoading = false,
                    authorization = authorization,
                    isAuthorizing = true,
                )
                repository.waitForDeviceAuthorization(
                    authorization.deviceCode,
                    authorization.expiresIn,
                    authorization.interval,
                )
                mutableState.value = mutableState.value.copy(
                    user = repository.currentUser.value,
                    isAuthorizing = false,
                    authorization = null,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.value = mutableState.value.copy(
                    isLoading = false,
                    isAuthorizing = false,
                    authorization = null,
                    error = error.toDisplayMessage("GitHub sign-in failed."),
                )
            }
        }
    }

    fun dismissError() {
        mutableState.value = mutableState.value.copy(error = null)
    }

    fun signOut() {
        viewModelScope.launch {
            try {
                repository.signOut()
                mutableState.value = LoginState(isLoading = false)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.value = mutableState.value.copy(error = error.toDisplayMessage("Could not sign out."))
            }
        }
    }
}

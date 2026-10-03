package com.gitcodera.presentation.auth

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gitcodera.data.local.SecureStorage
import com.gitcodera.data.model.User
import com.gitcodera.domain.repository.DeviceAuthorization
import com.gitcodera.domain.repository.GitHubRepository
import com.gitcodera.presentation.toDisplayMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
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
    val browserAuthorizationUrl: String? = null,
    val backendHandoffPending: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val repository: GitHubRepository,
    private val secureStorage: SecureStorage,
) : ViewModel() {
    val backendOAuthConfigured: Boolean
        get() = repository.isOAuthBackendConfigured

    private val mutableState = MutableStateFlow(LoginState())
    val state: StateFlow<LoginState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                repository.restoreSession()
                mutableState.value = LoginState(
                    isLoading = false,
                    user = repository.currentUser.value,
                    backendHandoffPending = secureStorage.getPendingOAuthTicket() != null,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.value = LoginState(
                    isLoading = false,
                    backendHandoffPending = secureStorage.getPendingOAuthTicket() != null,
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
                if (repository.isOAuthBackendConfigured) {
                    startBackendLogin()
                    return@launch
                }
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
                if (repository.isOAuthBackendConfigured) secureStorage.clearPendingOAuth()
                mutableState.value = mutableState.value.copy(
                    isLoading = false,
                    isAuthorizing = false,
                    authorization = null,
                    error = error.toDisplayMessage("GitHub sign-in failed."),
                )
            }
        }
    }

    private suspend fun startBackendLogin() {
        val state = randomUrlSafeBytes()
        val verifier = randomUrlSafeBytes()
        val challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
            MessageDigest.getInstance("SHA-256")
                .digest(verifier.toByteArray(StandardCharsets.US_ASCII)),
        )
        secureStorage.savePendingOAuth(state, verifier)
        val response = repository.startBackendAuthorization(state, challenge)
        val authorizationUri = Uri.parse(response.authorizationUrl)
        check(
            authorizationUri.scheme == "https" &&
                authorizationUri.host == "github.com" &&
                authorizationUri.path == "/login/oauth/authorize",
        ) {
            "OAuth backend returned an untrusted authorization URL."
        }
        mutableState.value = mutableState.value.copy(
            isLoading = false,
            isAuthorizing = true,
            backendHandoffPending = true,
            browserAuthorizationUrl = response.authorizationUrl,
        )
    }

    fun clearBrowserAuthorizationUrl() {
        mutableState.value = mutableState.value.copy(browserAuthorizationUrl = null)
    }

    fun authorizationLaunchFailed(message: String) {
        viewModelScope.launch {
            secureStorage.clearPendingOAuth()
            mutableState.value = mutableState.value.copy(
                isLoading = false,
                isAuthorizing = false,
                browserAuthorizationUrl = null,
                backendHandoffPending = false,
                error = message,
            )
        }
    }

    fun handleBackendCallback(callback: Uri) {
        viewModelScope.launch {
            try {
                require(
                    callback.scheme == OAUTH_SCHEME &&
                        callback.host == OAUTH_HOST &&
                        callback.path == OAUTH_PATH,
                ) { "OAuth callback URI is invalid." }
                val state = callback.uniqueQueryParameter("state")
                val expectedState = secureStorage.getPendingOAuthState()
                    ?: error("No GitHub sign-in is pending.")
                check(MessageDigest.isEqual(
                    state.toByteArray(StandardCharsets.US_ASCII),
                    expectedState.toByteArray(StandardCharsets.US_ASCII),
                )) { "OAuth callback state did not match. Please start sign-in again." }

                val oauthError = callback.getQueryParameter("error")
                if (oauthError != null) {
                    secureStorage.clearPendingOAuth()
                    mutableState.value = mutableState.value.copy(
                        isLoading = false,
                        isAuthorizing = false,
                        backendHandoffPending = false,
                        error = when (oauthError) {
                            "authorization_denied" -> "GitHub authorization was cancelled or denied."
                            else -> "The OAuth server could not complete GitHub sign-in. Please try again."
                        },
                    )
                    return@launch
                }
                val ticket = callback.uniqueQueryParameter("ticket")
                require(ticket.matches(Regex("[A-Za-z0-9_-]{43}"))) {
                    "OAuth callback handoff is invalid."
                }
                val verifier = secureStorage.getPendingOAuthVerifier()
                    ?: error("OAuth verifier is missing. Please start sign-in again.")
                secureStorage.savePendingOAuth(state, verifier, ticket)
                exchangePendingHandoff()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.value = mutableState.value.copy(
                    isLoading = false,
                    isAuthorizing = false,
                    error = error.toDisplayMessage("GitHub sign-in failed."),
                )
            }
        }
    }

    fun retryBackendHandoff() {
        viewModelScope.launch {
            try {
                exchangePendingHandoff()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.value = mutableState.value.copy(
                    isLoading = false,
                    isAuthorizing = false,
                    error = error.toDisplayMessage("Could not finish GitHub sign-in."),
                )
            }
        }
    }

    private suspend fun exchangePendingHandoff() {
        val ticket = secureStorage.getPendingOAuthTicket()
            ?: error("OAuth handoff is missing. Please sign in again.")
        val verifier = secureStorage.getPendingOAuthVerifier()
            ?: error("OAuth verifier is missing. Please sign in again.")
        mutableState.value = mutableState.value.copy(
            isLoading = true,
            isAuthorizing = true,
            error = null,
        )
        val tokens = repository.exchangeBackendHandoff(ticket, verifier)
        repository.acceptBackendSession(tokens)
        secureStorage.clearPendingOAuth()
        mutableState.value = mutableState.value.copy(
            isLoading = false,
            user = repository.currentUser.value,
            authorization = null,
            isAuthorizing = false,
            browserAuthorizationUrl = null,
            backendHandoffPending = false,
            error = null,
        )
    }

    fun dismissError() {
        mutableState.value = mutableState.value.copy(error = null)
    }

    fun signOut() {
        viewModelScope.launch {
            try {
                repository.signOut()
                secureStorage.clearPendingOAuth()
                mutableState.value = LoginState(isLoading = false)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.value = mutableState.value.copy(error = error.toDisplayMessage("Could not sign out."))
            }
        }
    }

    private fun randomUrlSafeBytes(): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(SecureRandom()::nextBytes))

    private fun Uri.uniqueQueryParameter(name: String): String {
        val values = getQueryParameters(name)
        require(values.size == 1 && values.single().isNotBlank()) {
            "OAuth callback must contain exactly one $name value."
        }
        return values.single()
    }

    private companion object {
        const val OAUTH_SCHEME = "gitcodera"
        const val OAUTH_HOST = "oauth"
        const val OAUTH_PATH = "/callback"
    }
}

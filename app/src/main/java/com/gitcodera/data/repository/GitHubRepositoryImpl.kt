package com.gitcodera.data.repository

import com.gitcodera.BuildConfig
import com.gitcodera.data.api.GitHubApiService
import com.gitcodera.data.api.GitHubAuthService
import com.gitcodera.data.local.SecureStorage
import com.gitcodera.data.model.Codespace
import com.gitcodera.data.model.CodespacesSecret
import com.gitcodera.data.model.isValidCodespacesSecretName
import com.gitcodera.data.model.CreateCodespaceRequest
import com.gitcodera.data.model.CreateRepositoryRequest
import com.gitcodera.data.model.PutCodespaceSecretRequest
import com.gitcodera.data.model.Repository
import com.gitcodera.data.model.User
import com.gitcodera.domain.repository.DeviceAuthorization
import com.gitcodera.domain.repository.GitHubRepository
import kotlinx.coroutines.delay
import android.os.SystemClock
import com.goterl.lazysodium.LazySodiumAndroid
import com.goterl.lazysodium.SodiumAndroid
import com.goterl.lazysodium.utils.Base64
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GitHubRepositoryImpl @Inject constructor(
    private val api: GitHubApiService,
    private val authApi: GitHubAuthService,
    private val secureStorage: SecureStorage,
) : GitHubRepository {
    private val mutableCurrentUser = MutableStateFlow<User?>(null)
    private val tokenRefreshMutex = Mutex()
    override val currentUser: StateFlow<User?> = mutableCurrentUser.asStateFlow()

    override suspend fun restoreSession() {
        if (secureStorage.getToken() != null) {
            ensureFreshAccessToken()
            mutableCurrentUser.value = api.getUser()
        }
    }

    override suspend fun requestDeviceCode(): DeviceAuthorization {
        check(BuildConfig.GITHUB_CLIENT_ID.isNotBlank()) {
            "Configure GITHUB_CLIENT_ID to enable GitHub sign-in."
        }
        val response = authApi.requestDeviceCode(
            BuildConfig.GITHUB_CLIENT_ID,
            "repo codespace read:user offline_access",
        )
        require(response.verificationUri == DEVICE_VERIFICATION_URI) {
            "GitHub returned an unexpected device verification URL."
        }
        return DeviceAuthorization(
            response.deviceCode,
            response.userCode,
            response.verificationUri,
            response.expiresIn,
            response.interval,
        )
    }

    override suspend fun waitForDeviceAuthorization(
        deviceCode: String,
        expiresIn: Int,
        interval: Int,
    ) {
        val deadline = SystemClock.elapsedRealtime() + expiresIn * 1_000L
        var pollInterval = interval.coerceAtLeast(1)
        while (SystemClock.elapsedRealtime() < deadline) {
            delay(pollInterval * 1_000L)
            val response = authApi.exchangeDeviceCode(BuildConfig.GITHUB_CLIENT_ID, deviceCode)
            when (response.error) {
                null -> {
                    persistOAuthSession(response)
                    mutableCurrentUser.value = api.getUser()
                    return
                }
                "authorization_pending" -> Unit
                "slow_down" -> pollInterval += 5
                "expired_token" -> error("GitHub sign-in expired. Please try again.")
                "access_denied" -> error("GitHub sign-in was denied.")
                else -> error(response.errorDescription ?: "GitHub sign-in failed: ${response.error}")
            }
        }
        error("GitHub sign-in expired. Please try again.")
    }

    override suspend fun signOut() {
        tokenRefreshMutex.withLock { secureStorage.clear() }
        mutableCurrentUser.value = null
    }

    override suspend fun getRepositories(): List<Repository> {
        ensureFreshAccessToken()
        return buildList {
            var page = 1
            do {
                val nextPage = api.getRepositories(page = page++)
                addAll(nextPage)
            } while (nextPage.size == PAGE_SIZE)
        }
    }

    override suspend fun createRepository(
        name: String,
        description: String,
        isPrivate: Boolean,
        initializeReadme: Boolean,
        gitignoreTemplate: String?,
        licenseTemplate: String?,
    ): Repository {
        ensureFreshAccessToken()
        return api.createRepository(
            CreateRepositoryRequest(
                name,
                description,
                isPrivate,
                initializeReadme,
                gitignoreTemplate?.ifBlank { null },
                licenseTemplate?.ifBlank { null },
            ),
        )
    }

    override suspend fun getCodespaces(): List<Codespace> {
        ensureFreshAccessToken()
        return buildList {
            var page = 1
            do {
                val nextPage = api.getCodespaces(page = page++).codespaces
                addAll(nextPage)
            } while (nextPage.size == PAGE_SIZE)
        }
    }

    override suspend fun createCodespace(repositoryFullName: String): Codespace {
        ensureFreshAccessToken()
        val parts = repositoryFullName.split("/")
        require(parts.size == 2 && parts.all(String::isNotBlank)) {
            "A repository must be selected before creating a Codespace."
        }
        return api.createCodespace(parts[0], parts[1], CreateCodespaceRequest())
    }

    override suspend fun deleteCodespace(name: String) {
        ensureFreshAccessToken()
        api.deleteCodespace(name)
    }

    override suspend fun startCodespace(name: String) {
        ensureFreshAccessToken()
        api.startCodespace(name)
    }

    override suspend fun stopCodespace(name: String) {
        ensureFreshAccessToken()
        api.stopCodespace(name)
    }

    override suspend fun saveCodespacesSecret(name: String, value: String, repositoryId: Long) {
        ensureFreshAccessToken()
        require(isValidCodespacesSecretName(name)) {
            "Secret names must use uppercase letters, digits, and underscores."
        }
        require(value.isNotBlank()) { "Secret values cannot be empty." }
        val publicKey = api.getCodespacesSecretsPublicKey()
        val message = value.toByteArray(StandardCharsets.UTF_8)
        try {
            val cipher = ByteArray(message.size + SodiumAndroid.CRYPTO_BOX_SEAL_BYTES)
            val sodium = LazySodiumAndroid(SodiumAndroid())
            val encrypted = sodium.cryptoBoxSeal(
                cipher,
                message,
                message.size.toLong(),
                Base64.decode(publicKey.key),
            )
            check(encrypted) { "Could not encrypt the Codespaces secret." }
            api.putCodespacesSecret(
                name,
                PutCodespacesSecretRequest(
                    encryptedValue = Base64.encode(cipher),
                    keyId = publicKey.keyId,
                    selectedRepositoryIds = listOf(repositoryId),
                ),
            )
        } finally {
            message.fill(0)
        }
    }

    override suspend fun deleteCodespacesSecret(name: String) {
        ensureFreshAccessToken()
        require(isValidCodespacesSecretName(name)) {
            "Secret names must use uppercase letters, digits, and underscores."
        }
        api.deleteCodespacesSecret(name)
    }

    override suspend fun getCodespacesSecrets(): List<CodespacesSecret> {
        ensureFreshAccessToken()
        return buildList {
            var page = 1
            do {
                val response = api.getCodespacesSecrets(page = page++)
                addAll(response.secrets)
            } while (response.secrets.size == PAGE_SIZE)
        }
    }

    private suspend fun ensureFreshAccessToken() {
        check(secureStorage.getToken() != null) { "GitHub session is missing. Sign in again." }
        tokenRefreshMutex.withLock {
            val expiry = secureStorage.getAccessTokenExpiryMillis() ?: return@withLock
            if (expiry > System.currentTimeMillis() + TOKEN_REFRESH_SKEW_MILLIS) return@withLock

            val refreshToken = secureStorage.getRefreshToken()
                ?: expireSession("GitHub session expired. Sign in again.")
            val refreshTokenExpiry = secureStorage.getRefreshTokenExpiryMillis()
            if (refreshTokenExpiry != null &&
                refreshTokenExpiry <= System.currentTimeMillis() + TOKEN_REFRESH_SKEW_MILLIS
            ) {
                expireSession("GitHub refresh token expired. Sign in again.")
            }
            val response = authApi.refreshAccessToken(
                clientId = BuildConfig.GITHUB_CLIENT_ID,
                refreshToken = refreshToken,
            )
            if (response.error != null) {
                if (response.error == "expired_token" || response.error == "invalid_grant") {
                    expireSession("GitHub session expired. Sign in again.")
                }
                error(response.errorDescription ?: "Could not refresh GitHub authorization.")
            }
            persistOAuthSession(
                response,
                previousRefreshToken = refreshToken,
                previousRefreshTokenExpiresAtMillis = refreshTokenExpiry,
            )
        }
    }

    private fun expireSession(message: String): Nothing {
        secureStorage.clear()
        mutableCurrentUser.value = null
        error(message)
    }

    private fun persistOAuthSession(
        response: com.gitcodera.data.model.AccessTokenResponse,
        previousRefreshToken: String? = null,
        previousRefreshTokenExpiresAtMillis: Long? = null,
    ) {
        val accessToken = requireNotNull(response.accessToken) {
            "GitHub returned an empty access token."
        }
        secureStorage.saveOAuthSession(
            accessToken,
            response.refreshToken ?: previousRefreshToken,
            response.expiresIn,
            response.refreshTokenExpiresIn?.let {
                System.currentTimeMillis() + it * 1_000L
            } ?: previousRefreshTokenExpiresAtMillis,
        )
    }

    private companion object {
        const val PAGE_SIZE = 100
        const val TOKEN_REFRESH_SKEW_MILLIS = 60_000L
        const val DEVICE_VERIFICATION_URI = "https://github.com/login/device"
    }
}

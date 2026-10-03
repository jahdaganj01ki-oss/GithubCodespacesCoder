package com.gitcodera.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecureStorage @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences = EncryptedSharedPreferences.create(
        context,
        "github_secure_storage",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun getToken(): String? = preferences.getString(TOKEN_KEY, null)

    fun saveOAuthSession(
        accessToken: String,
        refreshToken: String?,
        expiresInSeconds: Long?,
        refreshTokenExpiresAtMillis: Long?,
    ) {
        val editor = preferences.edit()
            .putString(TOKEN_KEY, accessToken)
            .putString(REFRESH_TOKEN_KEY, refreshToken)
        if (expiresInSeconds != null) {
            editor.putLong(ACCESS_TOKEN_EXPIRY_KEY, System.currentTimeMillis() + expiresInSeconds * 1_000L)
        } else {
            editor.remove(ACCESS_TOKEN_EXPIRY_KEY)
        }
        if (refreshToken == null) {
            editor.remove(REFRESH_TOKEN_EXPIRY_KEY)
        } else if (refreshTokenExpiresAtMillis != null) {
            editor.putLong(REFRESH_TOKEN_EXPIRY_KEY, refreshTokenExpiresAtMillis)
        }
        check(editor.commit()) { "Could not save the GitHub session securely." }
    }

    fun getRefreshToken(): String? = preferences.getString(REFRESH_TOKEN_KEY, null)

    fun getAccessTokenExpiryMillis(): Long? =
        if (preferences.contains(ACCESS_TOKEN_EXPIRY_KEY)) {
            preferences.getLong(ACCESS_TOKEN_EXPIRY_KEY, 0L)
        } else {
            null
        }

    fun getRefreshTokenExpiryMillis(): Long? =
        if (preferences.contains(REFRESH_TOKEN_EXPIRY_KEY)) {
            preferences.getLong(REFRESH_TOKEN_EXPIRY_KEY, 0L)
        } else {
            null
        }

    fun savePendingOAuth(state: String, codeVerifier: String, ticket: String? = null) {
        check(
            preferences.edit()
                .putString(PENDING_OAUTH_STATE_KEY, state)
                .putString(PENDING_OAUTH_VERIFIER_KEY, codeVerifier)
                .putString(PENDING_OAUTH_TICKET_KEY, ticket)
                .commit(),
        ) {
            "Could not save pending OAuth state securely."
        }
    }

    fun getPendingOAuthState(): String? = preferences.getString(PENDING_OAUTH_STATE_KEY, null)

    fun getPendingOAuthVerifier(): String? = preferences.getString(PENDING_OAUTH_VERIFIER_KEY, null)

    fun getPendingOAuthTicket(): String? = preferences.getString(PENDING_OAUTH_TICKET_KEY, null)

    fun clearPendingOAuth() {
        check(
            preferences.edit()
                .remove(PENDING_OAUTH_STATE_KEY)
                .remove(PENDING_OAUTH_VERIFIER_KEY)
                .remove(PENDING_OAUTH_TICKET_KEY)
                .commit(),
        ) {
            "Could not clear pending OAuth state."
        }
    }

    fun clear() {
        check(preferences.edit().clear().commit()) {
            "Could not clear secure credentials."
        }
    }

    private companion object {
        const val TOKEN_KEY = "github_access_token"
        const val REFRESH_TOKEN_KEY = "github_refresh_token"
        const val ACCESS_TOKEN_EXPIRY_KEY = "github_access_token_expiry"
        const val REFRESH_TOKEN_EXPIRY_KEY = "github_refresh_token_expiry"
        const val PENDING_OAUTH_STATE_KEY = "pending_oauth_state"
        const val PENDING_OAUTH_VERIFIER_KEY = "pending_oauth_verifier"
        const val PENDING_OAUTH_TICKET_KEY = "pending_oauth_ticket"
    }
}

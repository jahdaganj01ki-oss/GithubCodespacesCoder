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
    }
}

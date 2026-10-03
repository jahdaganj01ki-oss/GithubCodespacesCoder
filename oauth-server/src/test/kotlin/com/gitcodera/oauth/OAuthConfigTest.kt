package com.gitcodera.oauth

import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OAuthConfigTest {
    @Test
    fun requiresHttpsCallbackServerAndAValidEncryptionKey() {
        val base = mapOf(
            "GITHUB_CLIENT_ID" to "client-id",
            "GITHUB_CLIENT_SECRET" to "client-secret",
            "PUBLIC_BASE_URL" to "https://oauth.example.test",
            "APP_REDIRECT_URI" to "gitcodera://oauth/callback",
            "TOKEN_ENCRYPTION_KEY" to Base64.getEncoder().encodeToString(ByteArray(32)),
        )
        assertEquals(8080, OAuthConfig.fromEnvironment(base).port)
        assertFailsWith<IllegalArgumentException> {
            OAuthConfig.fromEnvironment(base + ("PUBLIC_BASE_URL" to "http://oauth.example.test"))
        }
        assertFailsWith<IllegalArgumentException> {
            OAuthConfig.fromEnvironment(base + ("TOKEN_ENCRYPTION_KEY" to "invalid"))
        }
        assertFailsWith<IllegalArgumentException> {
            OAuthConfig.fromEnvironment(base + ("APP_REDIRECT_URI" to "https://attacker.example/callback"))
        }
    }
}

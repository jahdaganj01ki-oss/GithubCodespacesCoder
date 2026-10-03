package com.gitcodera.oauth

import java.time.Instant
import java.util.Base64
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TokenVaultTest {
    private val verifier = "a".repeat(43)
    private val challenge = TokenVault.codeChallenge(verifier)

    @Test
    fun exchangesTicketOnceAndOnlyForThePkceVerifier() {
        val vault = newVault()
        val githubState = vault.createAuthorization("b".repeat(43), challenge)
        assertTrue(vault.hasPendingAuthorization(githubState))
        val tokens = OAuthTokenResponse(
            accessToken = "github-access",
            tokenType = "bearer",
            scope = "repo codespace",
            expiresIn = 3_600,
            refreshToken = "github-refresh",
            refreshTokenExpiresIn = 15_552_000,
        )

        val handoff = vault.issueTicket(githubState, tokens)
        assertEquals("b".repeat(43), handoff.appState)
        assertFalse(vault.hasPendingAuthorization(githubState))
        assertFailsWith<OAuthRequestException> {
            vault.exchange(handoff.ticket, "z".repeat(43))
        }
        assertEquals(tokens, vault.exchange(handoff.ticket, verifier))
        assertFailsWith<OAuthRequestException> {
            vault.exchange(handoff.ticket, verifier)
        }
    }

    @Test
    fun expiresAuthorizationAndTicket() {
        var now = Instant.parse("2026-01-01T00:00:00Z")
        val vault = newVault(clock = { now })
        val state = vault.createAuthorization("c".repeat(43), challenge)
        val handoff = vault.issueTicket(
            state,
            OAuthTokenResponse("token", "bearer", "repo", null, null, null),
        )
        now = now.plusSeconds(TokenVault.TICKET_TTL_SECONDS + 1)
        assertFalse(vault.hasPendingAuthorization(state))
        assertNull(vault.consumePendingState(state))
        assertFailsWith<OAuthRequestException> { vault.exchange(handoff.ticket, verifier) }
    }

    @Test
    fun validatesVerifierFormat() {
        assertFailsWith<IllegalArgumentException> { TokenVault.codeChallenge("short") }
        assertTrue(challenge.isNotEmpty())
    }

    private fun newVault(clock: () -> Instant = Instant::now): TokenVault {
        val key = ByteArray(32) { it.toByte() }
        val databaseName = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(9).also {
            java.security.SecureRandom().nextBytes(it)
        })
        return TokenVault(
            databaseUrl = "jdbc:h2:mem:$databaseName;DB_CLOSE_DELAY=-1",
            encryptionKey = SecretKeySpec(key, "AES"),
            clock = clock,
        )
    }
}

package com.gitcodera.oauth

import com.google.gson.Gson
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.time.Instant
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

internal data class PendingAuthorization(
    val appState: String,
    val codeChallenge: String,
    val expiresAt: Instant,
)

internal data class PendingTicket(
    val codeChallenge: String,
    val encryptedTokens: String,
    val expiresAt: Instant,
)

data class OAuthHandoff(
    val ticket: String,
    val appState: String,
)

class TokenVault(
    databaseUrl: String,
    private val encryptionKey: SecretKeySpec,
    private val gson: Gson = Gson(),
    private val clock: () -> Instant = Instant::now,
    private val random: SecureRandom = SecureRandom(),
) {
    private val connectionUrl = databaseUrl

    init {
        DriverManager.getConnection(connectionUrl).use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    """
                    CREATE TABLE IF NOT EXISTS oauth_pending (
                        github_state VARCHAR(128) PRIMARY KEY,
                        app_state VARCHAR(128) NOT NULL,
                        code_challenge VARCHAR(128) NOT NULL,
                        expires_at BIGINT NOT NULL
                    )
                    """.trimIndent(),
                )
                statement.execute(
                    """
                    CREATE TABLE IF NOT EXISTS oauth_tickets (
                        ticket VARCHAR(128) PRIMARY KEY,
                        code_challenge VARCHAR(128) NOT NULL,
                        encrypted_tokens CLOB NOT NULL,
                        expires_at BIGINT NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }
    }

    fun createAuthorization(appState: String, codeChallenge: String): String {
        val githubState = randomToken()
        val expiresAt = clock().plusSeconds(AUTHORIZATION_TTL_SECONDS)
        withConnection { connection ->
            connection.prepareStatement(
                "INSERT INTO oauth_pending (github_state, app_state, code_challenge, expires_at) VALUES (?, ?, ?, ?)",
            ).use { statement ->
                statement.setString(1, githubState)
                statement.setString(2, appState)
                statement.setString(3, codeChallenge)
                statement.setLong(4, expiresAt.toEpochMilli())
                statement.executeUpdate()
            }
        }
        return githubState
    }

    fun hasPendingAuthorization(githubState: String): Boolean =
        withConnection { connection ->
            connection.prepareStatement(
                "SELECT expires_at FROM oauth_pending WHERE github_state = ?",
            ).use { statement ->
                statement.setString(1, githubState)
                statement.executeQuery().use { result ->
                    result.next() && result.getLong("expires_at") > clock().toEpochMilli()
                }
            }
        }

    fun issueTicket(githubState: String, tokens: OAuthTokenResponse): OAuthHandoff {
        val ticket = randomToken()
        val encryptedTokens = encrypt(gson.toJson(tokens))
        val appState = withConnection { connection ->
            connection.autoCommit = false
            try {
                val pending = connection.prepareStatement(
                    "SELECT app_state, code_challenge, expires_at FROM oauth_pending WHERE github_state = ? FOR UPDATE",
                ).use { statement ->
                    statement.setString(1, githubState)
                    statement.executeQuery().use { result ->
                        if (result.next()) result.toPendingAuthorization() else null
                    }
                } ?: throw OAuthRequestException("OAuth state is invalid or expired.")

                if (pending.expiresAt <= clock()) {
                    throw OAuthRequestException("OAuth state is invalid or expired.")
                }
                connection.prepareStatement("DELETE FROM oauth_pending WHERE github_state = ?").use {
                    it.setString(1, githubState)
                    it.executeUpdate()
                }
                connection.prepareStatement(
                    "INSERT INTO oauth_tickets (ticket, code_challenge, encrypted_tokens, expires_at) VALUES (?, ?, ?, ?)",
                ).use { statement ->
                    statement.setString(1, ticket)
                    statement.setString(2, pending.codeChallenge)
                    statement.setString(3, encryptedTokens)
                    statement.setLong(4, clock().plusSeconds(TICKET_TTL_SECONDS).toEpochMilli())
                    statement.executeUpdate()
                }
                connection.commit()
                pending.appState
            } catch (error: Exception) {
                connection.rollback()
                throw error
            } finally {
                connection.autoCommit = true
            }
        }
        return OAuthHandoff(ticket, appState)
    }

    fun consumePendingState(githubState: String): String? = withConnection { connection ->
        connection.autoCommit = false
        try {
            val state = connection.prepareStatement(
                "SELECT app_state, expires_at FROM oauth_pending WHERE github_state = ? FOR UPDATE",
            ).use { statement ->
                statement.setString(1, githubState)
                statement.executeQuery().use { result ->
                    if (result.next() && result.getLong("expires_at") > clock().toEpochMilli()) {
                        result.getString("app_state")
                    } else {
                        null
                    }
                }
            }
            connection.prepareStatement("DELETE FROM oauth_pending WHERE github_state = ?").use {
                it.setString(1, githubState)
                it.executeUpdate()
            }
            connection.commit()
            state
        } catch (error: Exception) {
            connection.rollback()
            throw error
        } finally {
            connection.autoCommit = true
        }
    }

    fun exchange(ticket: String, codeVerifier: String): OAuthTokenResponse {
        val submittedChallenge = codeChallenge(codeVerifier)
        return withConnection { connection ->
            connection.autoCommit = false
            try {
                val pending = connection.prepareStatement(
                    "SELECT code_challenge, encrypted_tokens, expires_at FROM oauth_tickets WHERE ticket = ? FOR UPDATE",
                ).use { statement ->
                    statement.setString(1, ticket)
                    statement.executeQuery().use { result ->
                        if (result.next()) result.toPendingTicket() else null
                    }
                } ?: throw OAuthRequestException("OAuth handoff is invalid, expired, or already used.")

                if (pending.expiresAt <= clock() ||
                    !constantTimeEquals(pending.codeChallenge, submittedChallenge)
                ) {
                    throw OAuthRequestException("OAuth handoff is invalid, expired, or already used.")
                }
                connection.prepareStatement("DELETE FROM oauth_tickets WHERE ticket = ?").use {
                    it.setString(1, ticket)
                    check(it.executeUpdate() == 1) { "OAuth handoff was already used." }
                }
                connection.commit()
                gson.fromJson(decrypt(pending.encryptedTokens), OAuthTokenResponse::class.java)
            } catch (error: Exception) {
                connection.rollback()
                throw error
            } finally {
                connection.autoCommit = true
            }
        }
    }

    fun removeExpired() {
        val now = clock().toEpochMilli()
        withConnection { connection ->
            connection.prepareStatement("DELETE FROM oauth_pending WHERE expires_at <= ?").use {
                it.setLong(1, now)
                it.executeUpdate()
            }
            connection.prepareStatement("DELETE FROM oauth_tickets WHERE expires_at <= ?").use {
                it.setLong(1, now)
                it.executeUpdate()
            }
        }
    }

    private fun encrypt(plaintext: String): String {
        val nonce = ByteArray(GCM_NONCE_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, GCMParameterSpec(GCM_TAG_BITS, nonce))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))
        return Base64.getEncoder().encodeToString(nonce + ciphertext)
    }

    private fun decrypt(value: String): String {
        val packed = Base64.getDecoder().decode(value)
        require(packed.size > GCM_NONCE_BYTES) { "Stored OAuth token is invalid." }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            encryptionKey,
            GCMParameterSpec(GCM_TAG_BITS, packed.copyOfRange(0, GCM_NONCE_BYTES)),
        )
        return cipher.doFinal(packed.copyOfRange(GCM_NONCE_BYTES, packed.size))
            .toString(StandardCharsets.UTF_8)
    }

    private fun randomToken(): String {
        val bytes = ByteArray(32).also(random::nextBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun <T> withConnection(block: (Connection) -> T): T =
        DriverManager.getConnection(connectionUrl).use(block)

    private fun constantTimeEquals(expected: String, actual: String): Boolean {
        val expectedBytes = expected.toByteArray(StandardCharsets.US_ASCII)
        val actualBytes = actual.toByteArray(StandardCharsets.US_ASCII)
        if (expectedBytes.size != actualBytes.size) return false
        return java.security.MessageDigest.isEqual(expectedBytes, actualBytes)
    }

    companion object {
        const val AUTHORIZATION_TTL_SECONDS = 600L
        const val TICKET_TTL_SECONDS = 120L
        private const val GCM_NONCE_BYTES = 12
        private const val GCM_TAG_BITS = 128

        fun codeChallenge(codeVerifier: String): String {
            require(codeVerifier.matches(Regex("[A-Za-z0-9._~-]{43,128}"))) {
                "PKCE code verifier is invalid."
            }
            val hash = java.security.MessageDigest.getInstance("SHA-256")
                .digest(codeVerifier.toByteArray(StandardCharsets.US_ASCII))
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash)
        }
    }
}

class OAuthRequestException(message: String) : RuntimeException(message)

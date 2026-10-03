package com.gitcodera.oauth

import com.google.gson.Gson
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.Base64
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OAuthServerTest {
    @Test
    fun startEndpointCreatesGitHubAuthorizationUrlAndExchangeNeverExposesUnknownTickets() {
        val port = ServerSocket(0).use { it.localPort }
        val config = OAuthConfig.fromEnvironment(
            mapOf(
                "GITHUB_CLIENT_ID" to "client-id",
                "GITHUB_CLIENT_SECRET" to "client-secret",
                "PUBLIC_BASE_URL" to "https://oauth.example.test",
                "APP_REDIRECT_URI" to "gitcodera://oauth/callback",
                "TOKEN_ENCRYPTION_KEY" to Base64.getEncoder().encodeToString(ByteArray(32)),
                "DATABASE_URL" to "jdbc:h2:mem:oauth-http-$port;DB_CLOSE_DELAY=-1",
                "PORT" to port.toString(),
            ),
        )
        val oauthServer = OAuthServer(config, TokenVault(config.databaseUrl, config.encryptionKey))
        val client = HttpClient.newHttpClient()
        try {
            oauthServer.start()
            val state = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32) { 1 })
            val verifier = "a".repeat(43)
            val challenge = TokenVault.codeChallenge(verifier)
            val startBody = Gson().toJson(
                OAuthStartRequest(state, challenge, "gitcodera://oauth/callback"),
            )
            val startResponse = client.send(
                HttpRequest.newBuilder(URI("http://127.0.0.1:$port/oauth/start"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(startBody))
                    .build(),
                HttpResponse.BodyHandlers.ofString(),
            )
            assertEquals(HttpURLConnection.HTTP_OK, startResponse.statusCode())
            val authorizationUrl = URI(
                Gson().fromJson(startResponse.body(), OAuthStartResponse::class.java).authorizationUrl,
            )
            assertEquals("https", authorizationUrl.scheme)
            assertEquals("github.com", authorizationUrl.host)
            assertEquals("/login/oauth/authorize", authorizationUrl.path)
            assertTrue(authorizationUrl.rawQuery.orEmpty().contains("state="))

            val badExchange = client.send(
                HttpRequest.newBuilder(URI("http://127.0.0.1:$port/oauth/exchange"))
                    .header("Content-Type", "application/json")
                    .POST(
                        HttpRequest.BodyPublishers.ofString(
                            Gson().toJson(OAuthExchangeRequest("x".repeat(43), verifier)),
                        ),
                    )
                    .build(),
                HttpResponse.BodyHandlers.ofString(),
            )
            assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, badExchange.statusCode())
            assertTrue(badExchange.body().contains("invalid, expired, or already used"))
        } finally {
            oauthServer.stop(0)
        }
    }
}

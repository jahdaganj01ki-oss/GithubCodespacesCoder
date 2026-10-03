package com.gitcodera.oauth

import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.IOException
import java.net.InetSocketAddress
import java.net.URI
import java.net.URLEncoder
import java.net.URLDecoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.Executors

class OAuthServer(
    private val config: OAuthConfig,
    private val vault: TokenVault,
    private val githubClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build(),
    private val gson: Gson = Gson(),
) {
    private val executor = Executors.newFixedThreadPool(8)
    private var server: HttpServer? = null

    fun start(): HttpServer {
        check(server == null) { "OAuth callback server is already running." }
        val httpServer = HttpServer.create(InetSocketAddress("0.0.0.0", config.port), 64)
        httpServer.executor = executor
        httpServer.createContext("/health") { exchange ->
            if (exchange.requestURI.path != "/health") {
                respond(exchange, 404, OAuthError("not_found"))
                exchange.close()
                return@createContext
            }
            if (exchange.requestMethod != "GET") {
                respond(exchange, 405, OAuthError("method_not_allowed"))
            } else {
                respond(exchange, 200, mapOf("status" to "ok"))
            }
        }
        httpServer.createContext("/oauth/start") { exchange ->
            handle(exchange) {
                requireMethod(exchange, "POST")
                requirePath(exchange, "/oauth/start")
                val body = parseJsonBody(exchange, OAuthStartRequest::class.java)
                requireRandomState(body.state, "state")
                require(body.codeChallenge.matches(PKCE_CHALLENGE_PATTERN)) {
                    "PKCE code challenge is invalid."
                }
                require(body.redirectUri == config.appRedirectUri.toString()) {
                    "OAuth callback URI does not match the configured app callback."
                }
                vault.removeExpired()
                val githubState = vault.createAuthorization(body.state, body.codeChallenge)
                val authorizationUrl = URI(
                    "https://github.com/login/oauth/authorize" +
                        "?client_id=${encode(config.clientId)}" +
                        "&redirect_uri=${encode(config.callbackUrl)}" +
                        "&scope=${encode(OAUTH_SCOPE)}" +
                        "&state=${encode(githubState)}",
                )
                respond(exchange, 200, OAuthStartResponse(authorizationUrl.toString()))
            }
        }
        httpServer.createContext("/oauth/exchange") { exchange ->
            handle(exchange) {
                requireMethod(exchange, "POST")
                requirePath(exchange, "/oauth/exchange")
                val body = parseJsonBody(exchange, OAuthExchangeRequest::class.java)
                require(body.ticket.matches(TICKET_PATTERN)) { "OAuth handoff is invalid." }
                require(body.codeVerifier.matches(PKCE_VERIFIER_PATTERN)) {
                    "PKCE code verifier is invalid."
                }
                val tokens = vault.exchange(body.ticket, body.codeVerifier)
                respond(exchange, 200, tokens)
            }
        }
        httpServer.createContext("/oauth/callback") { exchange ->
            handle(exchange) {
                requireMethod(exchange, "GET")
                requirePath(exchange, "/oauth/callback")
                val query = parseQuery(exchange.requestURI.rawQuery.orEmpty())
                val githubState = query["state"]
                if (githubState == null || !githubState.matches(TICKET_PATTERN)) {
                    respond(exchange, 400, OAuthError("invalid_oauth_state"))
                    return@handle
                }
                if (!vault.hasPendingAuthorization(githubState)) {
                    respond(exchange, 400, OAuthError("invalid_oauth_state"))
                    return@handle
                }
                val providerError = query["error"]
                if (providerError != null) {
                    val appState = vault.consumePendingState(githubState)
                    if (appState == null) {
                        respond(exchange, 400, OAuthError("invalid_oauth_state"))
                    } else {
                        redirectToApp(appState, error = "authorization_denied", exchange = exchange)
                    }
                    return@handle
                }
                val code = query["code"]
                require(!code.isNullOrBlank() && code.length <= MAX_OAUTH_CODE_LENGTH) {
                    "GitHub authorization code is missing or invalid."
                }
                val tokenResponse = try {
                    val tokens = exchangeGitHubCode(code)
                    OAuthTokenResponse(
                        accessToken = tokens.access_token
                            ?: throw OAuthRequestException("GitHub did not return an access token."),
                        tokenType = tokens.token_type ?: "bearer",
                        scope = tokens.scope.orEmpty(),
                        expiresIn = tokens.expires_in,
                        refreshToken = tokens.refresh_token,
                        refreshTokenExpiresIn = tokens.refresh_token_expires_in,
                    )
                } catch (_: OAuthRequestException) {
                    val appState = vault.consumePendingState(githubState)
                    if (appState == null) {
                        respond(exchange, 400, OAuthError("invalid_oauth_state"))
                    } else {
                        redirectToApp(
                            appState = appState,
                            error = "authorization_failed",
                            exchange = exchange,
                        )
                    }
                    return@handle
                }
                val handoff = vault.issueTicket(githubState, tokenResponse)
                redirectToApp(handoff.appState, handoff.ticket, exchange = exchange)
            }
        }
        httpServer.start()
        server = httpServer
        return httpServer
    }

    fun stop(delaySeconds: Int = 1) {
        server?.stop(delaySeconds)
        server = null
        executor.shutdown()
    }

    private fun exchangeGitHubCode(code: String): GitHubTokenResponse {
        val form = listOf(
            "client_id" to config.clientId,
            "client_secret" to config.clientSecret,
            "code" to code,
            "redirect_uri" to config.callbackUrl,
        ).joinToString("&") { (key, value) -> "${encode(key)}=${encode(value)}" }
        val request = HttpRequest.newBuilder(URI("https://github.com/login/oauth/access_token"))
            .timeout(Duration.ofSeconds(20))
            .header("Accept", "application/json")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(form))
            .build()
        val response = try {
            githubClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
        } catch (error: IOException) {
            throw OAuthRequestException("GitHub token exchange is temporarily unavailable.")
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            throw OAuthRequestException("GitHub token exchange was interrupted.")
        }
        if (response.statusCode() !in 200..299) {
            throw OAuthRequestException("GitHub token exchange failed.")
        }
        val tokens = gson.fromJson(response.body(), GitHubTokenResponse::class.java)
            ?: throw OAuthRequestException("GitHub returned an invalid token response.")
        if (tokens.error != null) {
            throw OAuthRequestException(tokens.error_description ?: "GitHub rejected OAuth authorization.")
        }
        return tokens
    }

    private fun <T> parseJsonBody(exchange: HttpExchange, type: Class<T>): T {
        if (!exchange.requestHeaders.getFirst("Content-Type").orEmpty().startsWith("application/json")) {
            throw OAuthRequestException("Content-Type must be application/json.")
        }
        val body = exchange.requestBody.use { it.readNBytes(MAX_BODY_BYTES + 1) }
        if (body.size > MAX_BODY_BYTES) throw OAuthRequestException("Request body is too large.")
        return try {
            gson.fromJson(String(body, StandardCharsets.UTF_8), type)
                ?: throw OAuthRequestException("Request body is empty.")
        } catch (error: JsonParseException) {
            throw OAuthRequestException("Request body is not valid JSON.")
        }
    }

    private fun handle(exchange: HttpExchange, action: () -> Unit) {
        exchange.responseHeaders.set("Cache-Control", "no-store")
        exchange.responseHeaders.set("X-Content-Type-Options", "nosniff")
        exchange.responseHeaders.set("Referrer-Policy", "no-referrer")
        try {
            action()
        } catch (error: OAuthRequestException) {
            if (!exchange.responseHeaders.containsKey("Location")) {
                respond(exchange, 400, OAuthError(error.message ?: "invalid_request"))
            }
        } catch (error: IllegalArgumentException) {
            if (!exchange.responseHeaders.containsKey("Location")) {
                respond(exchange, 400, OAuthError(error.message ?: "invalid_request"))
            }
        } catch (error: Exception) {
            System.err.println(
                "OAuth server request failed (${exchange.requestMethod} ${exchange.requestURI.path}): " +
                    "${error.javaClass.simpleName}: ${error.message}",
            )
            if (!exchange.responseHeaders.containsKey("Location")) {
                respond(exchange, 500, OAuthError("server_error"))
            }
        } finally {
            exchange.close()
        }
    }

    private fun requireMethod(exchange: HttpExchange, method: String) {
        if (exchange.requestMethod != method) {
            throw OAuthRequestException("Method not allowed.")
        }
    }

    private fun requirePath(exchange: HttpExchange, path: String) {
        if (exchange.requestURI.path != path) {
            throw OAuthRequestException("Not found.")
        }
    }

    private fun redirectToApp(
        appState: String,
        ticket: String? = null,
        error: String? = null,
        exchange: HttpExchange,
    ) {
        val query = buildList {
            add("state" to appState)
            ticket?.let { add("ticket" to it) }
            error?.let { add("error" to it) }
        }.joinToString("&") { (key, value) -> "${encode(key)}=${encode(value)}" }
        val target = URI.create("${config.appRedirectUri}?$query")
        exchange.responseHeaders.set("Location", target.toString())
        exchange.responseHeaders.set("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'")
        exchange.sendResponseHeaders(302, -1)
    }

    private fun parseQuery(rawQuery: String): Map<String, String> {
        val values = mutableMapOf<String, String>()
        for (pair in rawQuery.split("&").filter(String::isNotEmpty)) {
            val parts = pair.split("=", limit = 2)
            val key = decode(parts[0])
            val value = decode(parts.getOrElse(1) { "" })
            if (values.putIfAbsent(key, value) != null) {
                throw OAuthRequestException("OAuth callback contains duplicate query parameters.")
            }
        }
        return values
    }

    private fun respond(exchange: HttpExchange, status: Int, body: Any) {
        val bytes = gson.toJson(body).toByteArray(StandardCharsets.UTF_8)
        exchange.responseHeaders.set("Content-Type", "application/json; charset=utf-8")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")

    private fun decode(value: String): String =
        URLDecoder.decode(value, StandardCharsets.UTF_8)

    private fun requireRandomState(value: String, name: String) {
        require(value.matches(TICKET_PATTERN)) { "$name must be a URL-safe random value." }
    }

    companion object {
        private const val MAX_BODY_BYTES = 16 * 1024
        private const val MAX_OAUTH_CODE_LENGTH = 4_096
        private const val OAUTH_SCOPE = "repo codespace read:user offline_access"
        private val TICKET_PATTERN = Regex("[A-Za-z0-9_-]{43}")
        private val PKCE_CHALLENGE_PATTERN = Regex("[A-Za-z0-9_-]{43}")
        private val PKCE_VERIFIER_PATTERN = Regex("[A-Za-z0-9._~-]{43,128}")
    }
}

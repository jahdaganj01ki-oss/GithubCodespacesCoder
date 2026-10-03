package com.gitcodera.oauth

import java.net.URI
import java.util.Base64
import javax.crypto.spec.SecretKeySpec

data class OAuthConfig(
    val clientId: String,
    val clientSecret: String,
    val publicBaseUrl: URI,
    val appRedirectUri: URI,
    val encryptionKey: SecretKeySpec,
    val databaseUrl: String,
    val port: Int,
) {
    val callbackUrl: String = publicBaseUrl.resolve("/oauth/callback").toString()

    companion object {
        fun fromEnvironment(environment: Map<String, String>): OAuthConfig {
            val clientId = environment.required("GITHUB_CLIENT_ID")
            val clientSecret = environment.required("GITHUB_CLIENT_SECRET")
            val publicBaseUrl = URI(environment.required("PUBLIC_BASE_URL"))
            require(publicBaseUrl.scheme == "https" && publicBaseUrl.host != null) {
                "PUBLIC_BASE_URL must be an HTTPS origin or base URL."
            }
            require(publicBaseUrl.rawPath.isNullOrEmpty() || publicBaseUrl.rawPath == "/") {
                "PUBLIC_BASE_URL must be an origin without a path prefix."
            }
            require(publicBaseUrl.userInfo == null && publicBaseUrl.query == null && publicBaseUrl.fragment == null) {
                "PUBLIC_BASE_URL must not contain credentials, a query, or a fragment."
            }

            val appRedirectUri = URI(environment["APP_REDIRECT_URI"] ?: "gitcodera://oauth/callback")
            require(appRedirectUri.scheme == "gitcodera" && appRedirectUri.host == "oauth") {
                "APP_REDIRECT_URI must use gitcodera://oauth/callback."
            }
            require(appRedirectUri.path == "/callback" && appRedirectUri.query == null && appRedirectUri.fragment == null) {
                "APP_REDIRECT_URI must use the /callback path and have no query or fragment."
            }

            val encryptionKeyBytes = runCatching {
                Base64.getDecoder().decode(environment.required("TOKEN_ENCRYPTION_KEY"))
            }.getOrElse {
                throw IllegalArgumentException("TOKEN_ENCRYPTION_KEY must be valid base64.", it)
            }
            require(encryptionKeyBytes.size == 32) {
                "TOKEN_ENCRYPTION_KEY must decode to exactly 32 bytes."
            }
            val port = (environment["PORT"] ?: "8080").toIntOrNull()
                ?: throw IllegalArgumentException("PORT must be an integer.")
            require(port in 1..65535) { "PORT must be between 1 and 65535." }

            return OAuthConfig(
                clientId = clientId,
                clientSecret = clientSecret,
                publicBaseUrl = publicBaseUrl,
                appRedirectUri = appRedirectUri,
                encryptionKey = SecretKeySpec(encryptionKeyBytes, "AES"),
                databaseUrl = environment["DATABASE_URL"] ?: "jdbc:h2:file:./data/gitcodera;DB_CLOSE_ON_EXIT=FALSE",
                port = port,
            )
        }

        private fun Map<String, String>.required(key: String): String =
            this[key]?.takeIf(String::isNotBlank)
                ?: throw IllegalArgumentException("$key must be configured.")
    }
}

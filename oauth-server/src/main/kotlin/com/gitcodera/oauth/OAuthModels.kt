package com.gitcodera.oauth

import com.google.gson.annotations.SerializedName

data class OAuthStartRequest(
    val state: String,
    val codeChallenge: String,
    val redirectUri: String,
)

data class OAuthStartResponse(
    val authorizationUrl: String,
)

data class OAuthExchangeRequest(
    val ticket: String,
    val codeVerifier: String,
)

data class OAuthTokenResponse(
    @SerializedName("access_token")
    val accessToken: String,
    @SerializedName("token_type")
    val tokenType: String,
    val scope: String,
    @SerializedName("expires_in")
    val expiresIn: Long?,
    @SerializedName("refresh_token")
    val refreshToken: String?,
    @SerializedName("refresh_token_expires_in")
    val refreshTokenExpiresIn: Long?,
)

data class GitHubTokenResponse(
    val access_token: String?,
    val token_type: String?,
    val scope: String?,
    val expires_in: Long?,
    val refresh_token: String?,
    val refresh_token_expires_in: Long?,
    val error: String?,
    val error_description: String?,
)

data class OAuthError(
    val error: String,
)

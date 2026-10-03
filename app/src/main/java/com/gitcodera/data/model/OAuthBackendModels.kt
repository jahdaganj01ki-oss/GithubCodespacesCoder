package com.gitcodera.data.model

import com.google.gson.annotations.SerializedName

data class OAuthStartRequest(
    val state: String,
    @SerializedName("codeChallenge") val codeChallenge: String,
    @SerializedName("redirectUri") val redirectUri: String,
)

data class OAuthStartResponse(
    @SerializedName("authorizationUrl") val authorizationUrl: String,
)

data class OAuthExchangeRequest(
    val ticket: String,
    @SerializedName("codeVerifier") val codeVerifier: String,
)

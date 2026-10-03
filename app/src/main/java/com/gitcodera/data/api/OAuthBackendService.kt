package com.gitcodera.data.api

import com.gitcodera.data.model.AccessTokenResponse
import com.gitcodera.data.model.OAuthExchangeRequest
import com.gitcodera.data.model.OAuthStartRequest
import com.gitcodera.data.model.OAuthStartResponse
import retrofit2.http.Body
import retrofit2.http.POST

interface OAuthBackendService {
    @POST("oauth/start")
    suspend fun startAuthorization(@Body request: OAuthStartRequest): OAuthStartResponse

    @POST("oauth/exchange")
    suspend fun exchangeHandoff(@Body request: OAuthExchangeRequest): AccessTokenResponse
}

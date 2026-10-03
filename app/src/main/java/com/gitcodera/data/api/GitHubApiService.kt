package com.gitcodera.data.api

import com.gitcodera.data.model.CodespaceListResponse
import com.gitcodera.data.model.CodespaceSecretPublicKey
import com.gitcodera.data.model.CodespacesSecretListResponse
import com.gitcodera.data.model.CreateCodespaceRequest
import com.gitcodera.data.model.CreateRepositoryRequest
import com.gitcodera.data.model.PutCodespaceSecretRequest
import com.gitcodera.data.model.Repository
import com.gitcodera.data.model.User
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.PUT
import retrofit2.http.Query

interface GitHubApiService {
    @GET("user")
    suspend fun getUser(): User

    @GET("user/repos")
    suspend fun getRepositories(
        @Query("per_page") perPage: Int = 100,
        @Query("page") page: Int = 1,
        @Query("sort") sort: String = "updated",
    ): List<Repository>

    @POST("user/repos")
    suspend fun createRepository(@Body request: CreateRepositoryRequest): Repository

    @GET("user/codespaces")
    suspend fun getCodespaces(
        @Query("per_page") perPage: Int = 100,
        @Query("page") page: Int = 1,
    ): CodespaceListResponse

    @POST("repos/{owner}/{repo}/codespaces")
    suspend fun createCodespace(
        @Path("owner") owner: String,
        @Path("repo") repository: String,
        @Body request: CreateCodespaceRequest = CreateCodespaceRequest(),
    ): Codespace

    @DELETE("user/codespaces/{name}")
    suspend fun deleteCodespace(@Path("name") name: String)

    @POST("user/codespaces/{name}/start")
    suspend fun startCodespace(@Path("name") name: String)

    @POST("user/codespaces/{name}/stop")
    suspend fun stopCodespace(@Path("name") name: String)

    @GET("user/codespaces/secrets/public-key")
    suspend fun getCodespacesSecretsPublicKey(): CodespaceSecretPublicKey

    @GET("user/codespaces/secrets")
    suspend fun getCodespacesSecrets(
        @Query("per_page") perPage: Int = 100,
        @Query("page") page: Int = 1,
    ): CodespacesSecretListResponse

    @PUT("user/codespaces/secrets/{secret_name}")
    suspend fun putCodespacesSecret(
        @Path("secret_name") name: String,
        @Body request: PutCodespaceSecretRequest,
    )

    @DELETE("user/codespaces/secrets/{secret_name}")
    suspend fun deleteCodespacesSecret(@Path("secret_name") name: String)
}

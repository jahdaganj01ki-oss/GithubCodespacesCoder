package com.gitcodera.data.model

import com.google.gson.annotations.SerializedName

data class User(
    val login: String,
    val name: String?,
    @SerializedName("avatar_url") val avatarUrl: String,
)

data class Repository(
    val id: Long,
    val name: String,
    val description: String?,
    @SerializedName("full_name") val fullName: String,
    @SerializedName("html_url") val htmlUrl: String,
    @SerializedName("private") val isPrivate: Boolean,
)

data class CreateRepositoryRequest(
    val name: String,
    val description: String,
    @SerializedName("private") val isPrivate: Boolean,
    @SerializedName("auto_init") val initializeReadme: Boolean,
    @SerializedName("gitignore_template") val gitignoreTemplate: String?,
    @SerializedName("license_template") val licenseTemplate: String?,
)

data class Codespace(
    val name: String,
    val state: String,
    @SerializedName("repository") val repository: CodespaceRepository?,
    @SerializedName("web_url") val webUrl: String?,
)

data class CodespaceListResponse(
    @SerializedName("total_count") val totalCount: Int,
    val codespaces: List<Codespace>,
)

data class CodespaceRepository(
    @SerializedName("full_name") val fullName: String?,
)

data class CreateCodespaceRequest(
    val ref: String? = null,
    val machine: String? = null,
)

data class CodespaceSecretPublicKey(
    @SerializedName("key_id") val keyId: String,
    val key: String,
)

data class PutCodespaceSecretRequest(
    @SerializedName("encrypted_value") val encryptedValue: String,
    @SerializedName("key_id") val keyId: String,
    @SerializedName("selected_repository_ids") val selectedRepositoryIds: List<Long>,
)

data class CodespacesSecretListResponse(
    @SerializedName("total_count") val totalCount: Int,
    val secrets: List<CodespacesSecret>,
)

data class CodespacesSecret(
    val name: String,
    @SerializedName("selected_repositories_url") val selectedRepositoriesUrl: String?,
)

data class DeviceCodeResponse(
    @SerializedName("device_code") val deviceCode: String,
    @SerializedName("user_code") val userCode: String,
    @SerializedName("verification_uri") val verificationUri: String,
    @SerializedName("expires_in") val expiresIn: Int,
    val interval: Int,
)

data class AccessTokenResponse(
    @SerializedName("access_token") val accessToken: String?,
    @SerializedName("token_type") val tokenType: String?,
    val scope: String?,
    val error: String?,
    @SerializedName("error_description") val errorDescription: String?,
    @SerializedName("expires_in") val expiresIn: Long? = null,
    @SerializedName("refresh_token") val refreshToken: String? = null,
    @SerializedName("refresh_token_expires_in") val refreshTokenExpiresIn: Long? = null,
)

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
    @SerializedName("default_branch") val defaultBranch: String? = null,
    val language: String? = null,
    @SerializedName("updated_at") val updatedAt: String? = null,
    val archived: Boolean = false,
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
    @SerializedName("display_name") val displayName: String? = null,
    val location: String? = null,
    val machine: CodespaceMachine? = null,
    @SerializedName("git_status") val gitStatus: CodespaceGitStatus? = null,
    @SerializedName("devcontainer_path") val devcontainerPath: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("last_used_at") val lastUsedAt: String? = null,
)

data class CodespaceMachine(
    val name: String?,
    @SerializedName("display_name") val displayName: String?,
    val cpus: Int?,
    @SerializedName("memory_in_bytes") val memoryInBytes: Long?,
    @SerializedName("storage_in_bytes") val storageInBytes: Long?,
)

data class CodespaceGitStatus(
    val ahead: Int?,
    val behind: Int?,
    @SerializedName("has_unpushed_changes") val hasUnpushedChanges: Boolean?,
    @SerializedName("has_uncommitted_changes") val hasUncommittedChanges: Boolean?,
    @SerializedName("has_untracked_files") val hasUntrackedFiles: Boolean?,
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
    @SerializedName(value = "access_token", alternate = ["accessToken"]) val accessToken: String?,
    @SerializedName(value = "token_type", alternate = ["tokenType"]) val tokenType: String?,
    val scope: String?,
    val error: String?,
    @SerializedName(value = "error_description", alternate = ["errorDescription"]) val errorDescription: String?,
    @SerializedName("expires_in") val expiresIn: Long? = null,
    @SerializedName(value = "refresh_token", alternate = ["refreshToken"]) val refreshToken: String? = null,
    @SerializedName(
        value = "refresh_token_expires_in",
        alternate = ["refreshTokenExpiresIn"],
    ) val refreshTokenExpiresIn: Long? = null,
)

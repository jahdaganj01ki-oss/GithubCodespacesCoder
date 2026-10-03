package com.gitcodera.domain.repository

import com.gitcodera.data.model.Codespace
import com.gitcodera.data.model.CodespacesSecret
import com.gitcodera.data.model.Repository
import com.gitcodera.data.model.User
import kotlinx.coroutines.flow.StateFlow

interface GitHubRepository {
    val currentUser: StateFlow<User?>
    suspend fun restoreSession()
    suspend fun requestDeviceCode(): DeviceAuthorization
    suspend fun waitForDeviceAuthorization(deviceCode: String, expiresIn: Int, interval: Int)
    suspend fun signOut()
    suspend fun getRepositories(): List<Repository>
    suspend fun createRepository(
        name: String,
        description: String,
        isPrivate: Boolean,
        initializeReadme: Boolean,
        gitignoreTemplate: String?,
        licenseTemplate: String?,
    ): Repository
    suspend fun getCodespaces(): List<Codespace>
    suspend fun createCodespace(repositoryFullName: String): Codespace
    suspend fun deleteCodespace(name: String)
    suspend fun startCodespace(name: String)
    suspend fun stopCodespace(name: String)
    suspend fun saveCodespacesSecret(name: String, value: String, repositoryId: Long)
    suspend fun deleteCodespacesSecret(name: String)
    suspend fun getCodespacesSecrets(): List<CodespacesSecret>
}

data class DeviceAuthorization(
    val deviceCode: String,
    val userCode: String,
    val verificationUri: String,
    val expiresIn: Int,
    val interval: Int,
)

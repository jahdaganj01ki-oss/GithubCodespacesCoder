package com.gitcodera.data.model

private val codespacesSecretNamePattern = Regex("[A-Z_][A-Z0-9_]{0,255}")

fun isValidCodespacesSecretName(name: String): Boolean =
    codespacesSecretNamePattern.matches(name)

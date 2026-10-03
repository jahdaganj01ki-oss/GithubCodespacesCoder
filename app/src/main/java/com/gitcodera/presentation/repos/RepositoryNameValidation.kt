package com.gitcodera.presentation.repos

private val repositoryNamePattern = Regex("[A-Za-z0-9._-]{1,100}")

fun isValidRepositoryName(name: String): Boolean =
    repositoryNamePattern.matches(name) && name != "." && name != ".."

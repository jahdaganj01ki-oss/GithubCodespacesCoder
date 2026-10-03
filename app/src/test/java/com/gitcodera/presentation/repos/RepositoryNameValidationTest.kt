package com.gitcodera.presentation.repos

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RepositoryNameValidationTest {
    @Test
    fun acceptsGitHubRepositoryNameCharacters() {
        assertTrue(isValidRepositoryName("GitCoderA_2.0-beta"))
    }

    @Test
    fun enforcesNonEmptyNameAndMaximumLength() {
        assertFalse(isValidRepositoryName(""))
        assertFalse(isValidRepositoryName("a".repeat(101)))
        assertTrue(isValidRepositoryName("a".repeat(100)))
    }

    @Test
    fun rejectsUnsupportedAndDotOnlyNames() {
        assertFalse(isValidRepositoryName("repo name"))
        assertFalse(isValidRepositoryName("../repo"))
        assertFalse(isValidRepositoryName("."))
        assertFalse(isValidRepositoryName(".."))
    }
}

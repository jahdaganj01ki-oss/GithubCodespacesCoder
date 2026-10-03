package com.gitcodera.data.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CodespacesSecretNameTest {
    @Test
    fun acceptsValidSecretNames() {
        assertTrue(isValidCodespacesSecretName("OPENAI_API_KEY"))
        assertTrue(isValidCodespacesSecretName("_CUSTOM_2"))
    }

    @Test
    fun rejectsInvalidSecretNames() {
        assertFalse(isValidCodespacesSecretName(""))
        assertFalse(isValidCodespacesSecretName("openai_api_key"))
        assertFalse(isValidCodespacesSecretName("2FACTOR"))
        assertFalse(isValidCodespacesSecretName("BAD-NAME"))
        assertFalse(isValidCodespacesSecretName("A".repeat(257)))
    }
}

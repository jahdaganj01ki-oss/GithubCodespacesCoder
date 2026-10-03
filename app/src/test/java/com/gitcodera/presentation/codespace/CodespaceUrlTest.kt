package com.gitcodera.presentation.codespace

import com.gitcodera.data.model.Codespace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CodespaceUrlTest {
    @Test
    fun acceptsHttpsCodespaceAndGitHubHostsOnly() {
        assertTrue(trustedCodespaceUrl("https://example.github.dev"))
        assertTrue(trustedCodespaceUrl("https://github.com/login"))
        assertFalse(trustedCodespaceUrl("http://example.github.dev"))
        assertFalse(trustedCodespaceUrl("https://github.dev.attacker.invalid"))
        assertFalse(trustedCodespaceUrl("https://user@example.github.dev"))
        assertFalse(trustedCodespaceUrl("https://example.github.dev:8443"))
    }

    @Test
    fun onlyUsesTheServerProvidedWebUrl() {
        val codespace = Codespace("my-space", "Available", null, "https://server.github.dev")
        assertEquals("https://server.github.dev", codespaceUrl(codespace))

        val invalidServerUrl = codespace.copy(webUrl = "https://attacker.invalid")
        assertNull(codespaceUrl(invalidServerUrl))
        assertNull(codespaceUrl(codespace.copy(name = "not/a/codespace", webUrl = null)))
    }
}

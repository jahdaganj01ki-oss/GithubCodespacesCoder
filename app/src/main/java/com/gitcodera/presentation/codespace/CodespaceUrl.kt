package com.gitcodera.presentation.codespace

import com.gitcodera.data.model.Codespace
import java.net.URI
import java.util.Locale

fun trustedCodespaceUrl(url: String): Boolean {
    val uri = runCatching { URI(url) }.getOrNull() ?: return false
    val host = uri.host?.lowercase(Locale.ROOT) ?: return false
    return uri.scheme.equals("https", ignoreCase = true) &&
        (host == "github.dev" || host.endsWith(".github.dev") ||
            host == "github.com" || host.endsWith(".github.com")) &&
        uri.userInfo == null && uri.port in listOf(-1, 443)
}

fun codespaceUrl(codespace: Codespace): String? =
    codespace.webUrl?.takeIf(::trustedCodespaceUrl)

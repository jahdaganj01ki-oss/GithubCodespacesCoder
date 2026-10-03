package com.gitcodera.presentation

import java.io.IOException
import java.net.SocketTimeoutException
import retrofit2.HttpException

fun Throwable.toDisplayMessage(fallback: String): String = when (this) {
    is SocketTimeoutException -> "GitHub took too long to respond. Please try again."
    is HttpException -> when (code()) {
        401 -> "GitHub authorization expired. Sign in again."
        403 -> "GitHub denied this request. Check your permissions or API rate limit."
        404 -> "The requested GitHub resource was not found."
        422 -> "GitHub rejected the submitted data. Check the values and try again."
        in 500..599 -> "GitHub is temporarily unavailable. Please try again later."
        else -> "GitHub request failed (HTTP ${code()})."
    }
    is IOException -> "Network request failed. Check your internet connection."
    else -> message?.takeIf(String::isNotBlank) ?: fallback
}

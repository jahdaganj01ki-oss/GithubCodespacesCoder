package com.gitcodera.presentation.codespace

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.view.KeyEvent
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

@Composable
fun CodespaceWebView(
    url: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!trustedCodespaceUrl(url)) {
        Column(
            modifier = modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text("GitHub returned an invalid Codespace URL.", color = MaterialTheme.colorScheme.error)
            Button(onClick = onClose) { Text("Back") }
        }
        return
    }

    val context = LocalContext.current
    val rootView = LocalView.current
    val webView = remember { mutableStateOf<DesktopCodespaceWebView?>(null) }
    var pageProgress by remember(url) { mutableIntStateOf(0) }
    var pageError by remember(url) { mutableStateOf<String?>(null) }
    var webViewGeneration by remember(url) { mutableIntStateOf(0) }

    DisposableEffect(rootView) {
        val window = (rootView.context as? android.app.Activity)?.window
        val controller = window?.let { WindowInsetsControllerCompat(it, rootView) }
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }

    BackHandler(enabled = true) {
        if (webView.value?.canGoBack() == true) webView.value?.goBack() else onClose()
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (pageProgress < 100) {
            LinearProgressIndicator(
                progress = { pageProgress / 100f },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        key(webViewGeneration) {
            AndroidView(
                modifier = Modifier.weight(1f),
                factory = { viewContext ->
                    DesktopCodespaceWebView(viewContext).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    settings.safeBrowsingEnabled = true
                    settings.setSupportMultipleWindows(false)
                    settings.javaScriptCanOpenWindowsAutomatically = false
                    settings.mediaPlaybackRequiresUserGesture = true
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    val chromeVersion = Regex("Chrome/([0-9.]+)")
                        .find(settings.userAgentString)
                        ?.groupValues
                        ?.get(1)
                        ?: "120.0.0.0"
                    settings.userAgentString =
                        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 " +
                            "(KHTML, like Gecko) Chrome/$chromeVersion Safari/537.36"
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView, newProgress: Int) {
                            pageProgress = newProgress
                            if (newProgress == 100) pageError = null
                        }
                    }
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(
                            view: WebView,
                            url: String,
                            favicon: android.graphics.Bitmap?,
                        ) {
                            pageError = null
                            pageProgress = 0
                        }

                        override fun onReceivedError(
                            view: WebView,
                            request: WebResourceRequest,
                            error: WebResourceError,
                        ) {
                            if (request.isForMainFrame) {
                                pageError = error.description?.toString()
                                    ?: "Could not load the Codespace page."
                            }
                        }

                        override fun onRenderProcessGone(
                            view: WebView,
                            detail: android.webkit.RenderProcessGoneDetail,
                        ): Boolean {
                            pageError = "The editor process stopped. Reload the Codespace to continue."
                            webView.value = null
                            webViewGeneration++
                            return true
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest,
                        ): Boolean {
                            val uri = request.url
                            val host = uri.host.orEmpty().lowercase()
                            val trustedHost = host == "github.com" ||
                                host.endsWith(".github.com") ||
                                host == "github.dev" ||
                                host.endsWith(".github.dev")
                            if (uri.scheme == "https" && trustedHost) return false
                            if (uri.scheme == "https" || uri.scheme == "http") {
                                try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                                } catch (_: ActivityNotFoundException) {
                                    pageError = "No installed app can open this link."
                                }
                            }
                            return true
                        }
                    }
                    loadUrl(url)
                    webView.value = this
                }
            },
            update = { if (it.url != url) it.loadUrl(url) },
            onRelease = {
                webView.value = null
                it.stopLoading()
                it.destroy()
            },
        )
        }
        if (pageError != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(pageError.orEmpty(), modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = {
                    pageError = null
                    if (webView.value == null) {
                        webViewGeneration++
                    } else {
                        webView.value?.reload()
                    }
                }) {
                    Text("Retry")
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf(
                "Ctrl" to KeyEvent.META_CTRL_ON,
                "Alt" to KeyEvent.META_ALT_ON,
                "Shift" to KeyEvent.META_SHIFT_ON,
            ).forEach { (label, modifierFlag) ->
                val enabled = remember { mutableStateOf(false) }
                Button(
                    onClick = {
                        enabled.value = !enabled.value
                        webView.value?.setModifierEnabled(modifierFlag, enabled.value)
                        webView.value?.requestFocus()
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(label, color = if (enabled.value) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onPrimary)
                }
            }
            Button(onClick = onClose) { Text("Done") }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private class DesktopCodespaceWebView(context: android.content.Context) : WebView(context) {
    private var virtualModifiers = 0

    fun setModifierEnabled(flag: Int, enabled: Boolean) {
        virtualModifiers = if (enabled) virtualModifiers or flag else virtualModifiers and flag.inv()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (virtualModifiers == 0) {
            return super.dispatchKeyEvent(event)
        }
        val modifiedEvent = KeyEvent(
            event.downTime,
            event.eventTime,
            event.action,
            event.keyCode,
            event.repeatCount,
            event.metaState or virtualModifiers,
            event.deviceId,
            event.scanCode,
            event.flags,
            event.source,
        )
        return super.dispatchKeyEvent(modifiedEvent)
    }
}

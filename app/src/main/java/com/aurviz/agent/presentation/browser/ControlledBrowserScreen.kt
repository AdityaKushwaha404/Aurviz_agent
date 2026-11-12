package com.aurviz.agent.presentation.browser

import android.graphics.Bitmap
import android.net.Uri
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import android.view.ViewGroup
import android.widget.FrameLayout
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import androidx.compose.ui.viewinterop.AndroidView
import com.aurviz.agent.service.web.BrowserAgentController
import com.aurviz.agent.service.web.BrowserCommand
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONTokener
import org.json.JSONObject
import kotlin.coroutines.resume

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlledBrowserScreen(
    initialUrl: String = "",
    controller: BrowserAgentController,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var address by remember(initialUrl) { mutableStateOf(initialUrl) }
    var currentUrl by remember { mutableStateOf("") }
    var pageTitle by remember { mutableStateOf("Enter a public website address") }
    var pageError by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var extractedText by remember { mutableStateOf("") }
    var pageProgress by remember { mutableIntStateOf(0) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    fun loadAddress(raw: String) {
        val url = raw.trim().let { if (it.contains("://")) it else "https://$it" }
        val uri = runCatching { Uri.parse(url) }.getOrNull()
        if (uri == null || !uri.isAllowedBrowserDestination()) {
            pageError = "Enter a valid public HTTPS website address."
            controller.updateState(error = pageError)
            return
        }
        pageError = null
        extractedText = ""
        address = url
        webView?.loadUrl(url)
    }

    BackHandler(enabled = webView?.canGoBack() == true) {
        webView?.goBack()
    }

    val browser = remember(context) {
        WebView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.safeBrowsingEnabled = true
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    pageProgress = newProgress
                    isLoading = newProgress < 100
                    if (newProgress == 100) pageTitle = view?.title ?: view?.url.orEmpty()
                    controller.updateState(
                        title = view?.title?.takeIf(String::isNotBlank),
                        url = view?.url,
                        isLoading = isLoading,
                        progress = newProgress
                    )
                }
            }
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val uri = request?.url ?: return true
                    return !uri.isAllowedBrowserDestination()
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    isLoading = true
                    pageError = null
                    currentUrl = url.orEmpty()
                    address = currentUrl
                    controller.updateState(
                        title = view?.title ?: "Loading page",
                        url = currentUrl,
                        isLoading = true,
                        progress = pageProgress,
                        clearError = true
                    )
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    isLoading = false
                    pageTitle = view?.title?.takeIf(String::isNotBlank) ?: url.orEmpty()
                    currentUrl = url.orEmpty()
                    controller.updateState(
                        title = pageTitle,
                        url = currentUrl,
                        isLoading = false,
                        progress = 100,
                        clearError = true
                    )
                    controller.finishNavigation("Loaded $pageTitle at $currentUrl")
                }

                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                    if (request?.isForMainFrame == true) {
                        isLoading = false
                        pageError = error?.description?.toString() ?: "The page could not be loaded."
                        controller.updateState(isLoading = false, error = pageError)
                        controller.finishNavigation("Error: $pageError")
                    }
                }

                override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: android.net.http.SslError?) {
                    handler?.cancel()
                    pageError = "Secure connection failed. The page was blocked."
                    controller.updateState(isLoading = false, error = pageError)
                    controller.finishNavigation("Error: $pageError")
                }
            }
        }
    }
    webView = browser
    LaunchedEffect(initialUrl, browser, controller) {
        controller.setHostAvailable(true)
        if (initialUrl.isNotBlank()) loadAddress(initialUrl)
        controller.commands.collect { command ->
            val result = runCatching {
                runAgentBrowserCommand(browser, controller, command, ::loadAddress)
            }.getOrElse { "Error: Browser command failed: ${it.message ?: "unknown error"}" }
            command.complete(result)
        }
    }
    DisposableEffect(browser, controller) {
        onDispose {
            controller.setHostAvailable(false)
            browser.stopLoading()
            browser.destroy()
            webView = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Browser", fontWeight = FontWeight.SemiBold)
                        if (pageTitle.isNotBlank()) Text(pageTitle, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to agent")
                    }
                }
            )
        }
    ) { insets ->
        Column(Modifier.fillMaxSize().padding(insets).padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    label = { Text("Website") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { loadAddress(address) })
                )
                IconButton(onClick = { loadAddress(address) }) {
                    Icon(Icons.Default.Search, contentDescription = "Go to website")
                }
                IconButton(onClick = { webView?.reload() }, enabled = currentUrl.isNotBlank()) {
                    Icon(Icons.Default.Refresh, contentDescription = "Reload page")
                }
            }
            if (isLoading) {
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { pageProgress / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            pageError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp)) }

            AndroidView(
                factory = { browser },
                modifier = Modifier.weight(1f).fillMaxWidth()
            )

            if (currentUrl.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Page text", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    IconButton(onClick = { webView?.goBack() }, enabled = browser.canGoBack()) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous page")
                    }
                    IconButton(onClick = { webView?.goForward() }, enabled = browser.canGoForward()) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next page")
                    }
                    Button(onClick = {
                        webView?.evaluateJavascript("JSON.stringify(document.body ? document.body.innerText : '')") { encoded ->
                            extractedText = runCatching { JSONTokener(encoded).nextValue() as? String ?: "" }
                                .getOrDefault("").take(BrowserAgentController.PAGE_TEXT_LIMIT)
                        }
                    }) { Text("Extract") }
                }
                if (extractedText.isNotBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Extracted page text", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                                IconButton(onClick = { clipboard.setText(AnnotatedString(extractedText)) }) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy extracted text")
                                }
                            }
                            Text(extractedText, style = MaterialTheme.typography.bodySmall, maxLines = 5)
                        }
                    }
                }
            }
        }
    }
}

private suspend fun runAgentBrowserCommand(
    webView: WebView,
    controller: BrowserAgentController,
    command: BrowserCommand,
    loadAddress: (String) -> Unit
): String {
    val args = command.arguments
    suspend fun pageText(): String {
        val encoded = webView.evaluateJavascriptSuspending(
            "JSON.stringify(document.body ? document.body.innerText : '')"
        )
        return (runCatching { JSONTokener(encoded).nextValue() as? String }.getOrNull() ?: "")
            .take(BrowserAgentController.PAGE_TEXT_LIMIT)
    }
    suspend fun loadAndWait(url: String): String {
        val uri = runCatching { Uri.parse(url) }.getOrNull()
            ?: return "Error: Invalid website address."
        if (!uri.isAllowedBrowserDestination()) return "Error: Only public HTTPS websites are allowed."
        val waiter = controller.beginNavigation()
        loadAddress(url)
        return withTimeoutOrNull(25_000) { waiter.await() }
            ?: "Error: The page did not finish loading before the timeout."
    }

    return when (command.name) {
        "open_url" -> loadAndWait(args["url"] as? String ?: return "Error: Missing URL.")
        "search_webpage" -> {
            val query = args["query"] as? String ?: return "Error: Missing search query."
            if (webView.url.isNullOrBlank()) {
                loadAndWait("https://www.google.com/search?q=${Uri.encode(query)}")
            } else {
                val selector = args["selector"] as? String
                    ?: "input[type=search],input[name=q],input[name=query]"
                val waiter = controller.beginNavigation()
                val script = """
                    (() => {
                      const el = document.querySelector(${JSONObject.quote(selector)});
                      if (!el) return "NO_SEARCH_FIELD";
                      el.focus();
                      el.value = ${JSONObject.quote(query)};
                      el.dispatchEvent(new Event("input", {bubbles:true}));
                      el.dispatchEvent(new Event("change", {bubbles:true}));
                      if (el.form) { el.form.requestSubmit ? el.form.requestSubmit() : el.form.submit(); return "SUBMITTED"; }
                      el.dispatchEvent(new KeyboardEvent("keydown", {key:"Enter",keyCode:13,which:13,bubbles:true}));
                      el.dispatchEvent(new KeyboardEvent("keyup", {key:"Enter",keyCode:13,which:13,bubbles:true}));
                      return "SUBMITTED";
                    })()
                """.trimIndent()
                val result = runCatching { JSONTokener(webView.evaluateJavascriptSuspending(script)).nextValue() as? String }.getOrNull()
                if (result == "NO_SEARCH_FIELD") {
                    controller.finishNavigation("")
                    "Error: No search field was found. Open a page with a search box or supply a CSS selector."
                } else {
                    withTimeoutOrNull(12_000) { waiter.await() }
                        ?: "Search submitted for “$query”. The page may have updated without a full navigation."
                }
            }
        }
        "read_page" -> {
            if (webView.url.isNullOrBlank()) return "Error: Open a webpage first."
            val text = pageText()
            if (text.isBlank()) "The page has no readable text." else
                "Title: ${webView.title.orEmpty()}\nURL: ${webView.url.orEmpty()}\n\n$text"
        }
        "click_element" -> {
            val selector = args["selector"] as? String ?: return "Error: Missing CSS selector."
            val script = """
                (() => {
                  const el = document.querySelector(${JSONObject.quote(selector)});
                  if (!el) return "NOT_FOUND";
                  el.scrollIntoView({block:"center",behavior:"instant"});
                  el.click();
                  return "CLICKED";
                })()
            """.trimIndent()
            when (runCatching { JSONTokener(webView.evaluateJavascriptSuspending(script)).nextValue() as? String }.getOrNull()) {
                "CLICKED" -> "Clicked the approved page element."
                else -> "Error: No element matched the approved selector."
            }
        }
        "enter_text" -> {
            val selector = args["selector"] as? String ?: return "Error: Missing CSS selector."
            val text = args["text"] as? String ?: return "Error: Missing text."
            val script = """
                (() => {
                  const el = document.querySelector(${JSONObject.quote(selector)});
                  if (!el) return "NOT_FOUND";
                  el.focus();
                  if (el.isContentEditable) el.textContent = ${JSONObject.quote(text)};
                  else if ("value" in el) el.value = ${JSONObject.quote(text)};
                  else return "NOT_INPUT";
                  el.dispatchEvent(new Event("input", {bubbles:true}));
                  el.dispatchEvent(new Event("change", {bubbles:true}));
                  return "ENTERED";
                })()
            """.trimIndent()
            when (runCatching { JSONTokener(webView.evaluateJavascriptSuspending(script)).nextValue() as? String }.getOrNull()) {
                "ENTERED" -> "Entered the approved text. The form was not submitted."
                "NOT_INPUT" -> "Error: The approved element is not an editable input."
                else -> "Error: No editable element matched the approved selector."
            }
        }
        "scroll_page" -> {
            val direction = args["direction"] as? String ?: return "Error: Missing scroll direction."
            val sign = if (direction == "up") -1 else 1
            webView.evaluateJavascriptSuspending("window.scrollBy({top:window.innerHeight*0.75*$sign,behavior:'smooth'}); 'SCROLLED'")
            "Scrolled $direction."
        }
        "navigate_back" -> {
            if (!webView.canGoBack()) "No previous page is available."
            else {
                val waiter = controller.beginNavigation()
                webView.goBack()
                withTimeoutOrNull(15_000) { waiter.await() } ?: "Navigating to the previous page."
            }
        }
        "navigate_forward" -> {
            if (!webView.canGoForward()) "No next page is available."
            else {
                val waiter = controller.beginNavigation()
                webView.goForward()
                withTimeoutOrNull(15_000) { waiter.await() } ?: "Navigating to the next page."
            }
        }
        "get_browser_state" -> {
            val text = pageText()
            "Title: ${webView.title.orEmpty()}\nURL: ${webView.url.orEmpty()}\nLoading: ${controller.state.value.isLoading}\nPage text: ${text.take(2_000)}"
        }
        else -> "Error: Unsupported browser command."
    }
}

private suspend fun WebView.evaluateJavascriptSuspending(script: String): String =
    suspendCancellableCoroutine { continuation ->
        evaluateJavascript(script) { result ->
            if (continuation.isActive) continuation.resume(result.orEmpty())
        }
    }

private fun Uri.isAllowedBrowserDestination(): Boolean {
    if (!scheme.equals("https", ignoreCase = true) || host.isNullOrBlank() || userInfo != null) return false
    val normalizedHost = host?.lowercase() ?: return false
    if (normalizedHost == "localhost" || normalizedHost.endsWith(".localhost") ||
        normalizedHost.endsWith(".local") || normalizedHost.endsWith(".internal") ||
        normalizedHost.endsWith(".test") || normalizedHost.endsWith(".invalid")
    ) return false
    val ipv4 = normalizedHost.split(".").mapNotNull { it.toIntOrNull() }
    if (ipv4.size == 4 && ipv4.all { it in 0..255 }) {
        val first = ipv4[0]
        val second = ipv4[1]
        if (first == 0 || first == 10 || first == 127 || first >= 224 ||
            (first == 169 && second == 254) ||
            (first == 172 && second in 16..31) ||
            (first == 192 && second == 168) ||
            (first == 100 && second in 64..127)
        ) return false
    }
    if (normalizedHost == "::1" || normalizedHost.startsWith("fe80:") ||
        normalizedHost.startsWith("fc") || normalizedHost.startsWith("fd")
    ) return false
    return true
}

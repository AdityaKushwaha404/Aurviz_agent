package com.aurviz.agent.presentation.browser

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.aurviz.agent.presentation.components.MarkdownText
import com.aurviz.agent.presentation.theme.SuccessGreen
import com.aurviz.agent.service.web.BrowserAgentController
import com.aurviz.agent.service.web.BrowserCommand
import com.aurviz.agent.service.web.BrowserStore
import com.aurviz.agent.service.web.BrowserUrl
import com.aurviz.agent.service.web.SavedPage
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.json.JSONTokener
import kotlin.coroutines.resume

private const val DESKTOP_UA =
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"

// evaluateJavascript already JSON-encodes a returned string, so scripts return plain strings.
private const val TEXT_SCRIPT = "(document.body ? document.body.innerText : '')"

private const val READER_SCRIPT = """
(() => {
  const root = document.querySelector('article') || document.querySelector('main') || document.body;
  if (!root) return '';
  const parts = [];
  root.querySelectorAll('h1,h2,h3,p,li').forEach(e => {
    const t = (e.innerText || '').trim();
    if (t.length < 2) return;
    parts.push((e.tagName[0] === 'H' ? '## ' : e.tagName === 'LI' ? '- ' : '') + t);
  });
  return parts.join('\n\n');
})()
"""

private const val LINKS_SCRIPT = """
(() => {
  const seen = new Set(); const out = [];
  document.querySelectorAll('a[href]').forEach(a => {
    const u = a.href, t = (a.innerText || a.title || '').trim().replace(/\s+/g, ' ').slice(0, 90);
    if (!u.startsWith('https://') || !t || seen.has(u)) return;
    seen.add(u); out.push({t: t, u: u});
  });
  return JSON.stringify(out.slice(0, 40));
})()
"""

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlledBrowserScreen(
    initialUrl: String = "",
    controller: BrowserAgentController,
    store: BrowserStore,
    onBack: () -> Unit,
    onAskAgent: (prompt: String, attachment: String?) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val startUrl = initialUrl.ifBlank { "https://www.google.com" }

    var address by remember(startUrl) { mutableStateOf(startUrl) }
    var editingAddress by remember { mutableStateOf(false) }
    var currentUrl by remember { mutableStateOf("") }
    var pageTitle by remember { mutableStateOf("") }
    var pageError by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var pageProgress by remember { mutableIntStateOf(0) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    var menuOpen by remember { mutableStateOf(false) }
    var desktopSite by remember { mutableStateOf(false) }
    var textZoom by remember { mutableIntStateOf(100) }
    var findOpen by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }
    var findCount by remember { mutableIntStateOf(0) }
    var findIndex by remember { mutableIntStateOf(0) }
    var showLibrary by remember { mutableStateOf(false) }
    var readerText by remember { mutableStateOf<String?>(null) }
    var askDialog by remember { mutableStateOf(false) }

    val bookmarks by store.bookmarks.collectAsState()
    val history by store.history.collectAsState()
    val isBookmarked = bookmarks.any { it.url == currentUrl }

    val quickSites = listOf(
        "Google" to "https://www.google.com",
        "Hacker News" to "https://news.ycombinator.com",
        "Wikipedia" to "https://en.wikipedia.org",
        "GitHub" to "https://github.com",
        "BBC News" to "https://www.bbc.com/news"
    )

    fun loadAddress(raw: String) {
        val url = BrowserUrl.resolve(raw)
        val uri = url?.let { runCatching { Uri.parse(it) }.getOrNull() }
        if (url == null || uri == null || !uri.isAllowedBrowserDestination()) {
            pageError = "Enter a public HTTPS website address, or type words to search."
            controller.updateState(error = pageError)
            return
        }
        pageError = null
        address = url
        webView?.loadUrl(url)
    }

    suspend fun readPageText(view: WebView?): String {
        view ?: return ""
        val encoded = view.evaluateJavascriptSuspending(TEXT_SCRIPT)
        return (runCatching { JSONTokener(encoded).nextValue() as? String }.getOrNull() ?: "")
            .take(BrowserAgentController.PAGE_TEXT_LIMIT)
    }

    fun askAgent(prompt: String) {
        scope.launch {
            val text = readPageText(webView)
            if (text.isBlank()) {
                Toast.makeText(context, "Open a page with readable text first.", Toast.LENGTH_SHORT).show()
                return@launch
            }
            onAskAgent(
                prompt,
                "Page title: $pageTitle\nURL: $currentUrl\n\nPage content:\n${text.take(8_000)}"
            )
        }
    }

    BackHandler(enabled = findOpen) { findOpen = false; webView?.clearMatches() }
    BackHandler(enabled = !findOpen && canGoBack) { webView?.goBack() }

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
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            setFindListener { active, total, _ ->
                findIndex = if (total == 0) 0 else active + 1
                findCount = total
            }
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

                override fun onReceivedTitle(view: WebView?, title: String?) {
                    if (!title.isNullOrBlank()) pageTitle = title
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
                    if (!editingAddress) address = currentUrl
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
                    canGoBack = view?.canGoBack() == true
                    canGoForward = view?.canGoForward() == true
                    store.addHistory(pageTitle, currentUrl)
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
    LaunchedEffect(startUrl, browser, controller) {
        controller.setHostAvailable(true)
        loadAddress(startUrl)
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
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 6.dp)
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to chat", tint = MaterialTheme.colorScheme.primary)
                        }
                        TextField(
                            value = if (editingAddress) address else BrowserUrl.displayHost(currentUrl.ifBlank { address }),
                            onValueChange = { address = it },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp),
                            singleLine = true,
                            shape = RoundedCornerShape(24.dp),
                            leadingIcon = {
                                Icon(
                                    if (currentUrl.startsWith("https://")) Icons.Default.Lock else Icons.Default.Search,
                                    contentDescription = if (currentUrl.startsWith("https://")) "Secure connection" else "Search",
                                    tint = if (currentUrl.startsWith("https://")) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            trailingIcon = {
                                if (isLoading) {
                                    IconButton(onClick = { webView?.stopLoading() }) {
                                        Icon(Icons.Default.Close, contentDescription = "Stop loading")
                                    }
                                } else {
                                    IconButton(onClick = { webView?.reload() }, enabled = currentUrl.isNotBlank()) {
                                        Icon(Icons.Default.Refresh, contentDescription = "Reload")
                                    }
                                }
                            },
                            placeholder = { Text("Search or type a website") },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(onGo = {
                                editingAddress = false
                                loadAddress(address)
                            }),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                                .also { source ->
                                    LaunchedEffect(source) {
                                        source.interactions.collect { interaction ->
                                            if (interaction is androidx.compose.foundation.interaction.FocusInteraction.Focus) {
                                                editingAddress = true
                                                address = currentUrl.ifBlank { address }
                                            } else if (interaction is androidx.compose.foundation.interaction.FocusInteraction.Unfocus) {
                                                editingAddress = false
                                            }
                                        }
                                    }
                                }
                        )
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More options")
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("Find in page") },
                                    leadingIcon = { Icon(Icons.Default.FindInPage, null) },
                                    onClick = { menuOpen = false; findOpen = true }
                                )
                                DropdownMenuItem(
                                    text = { Text("Reader view") },
                                    leadingIcon = { Icon(Icons.Default.Article, null) },
                                    onClick = {
                                        menuOpen = false
                                        scope.launch {
                                            val encoded = browser.evaluateJavascriptSuspending(READER_SCRIPT)
                                            readerText = runCatching { JSONTokener(encoded).nextValue() as? String }
                                                .getOrNull().orEmpty().ifBlank { "This page has no readable article text." }
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(if (desktopSite) "Mobile site" else "Desktop site") },
                                    leadingIcon = { Icon(if (desktopSite) Icons.Default.PhoneAndroid else Icons.Default.DesktopWindows, null) },
                                    onClick = {
                                        menuOpen = false
                                        desktopSite = !desktopSite
                                        browser.settings.userAgentString = if (desktopSite) DESKTOP_UA else null
                                        browser.settings.useWideViewPort = desktopSite
                                        browser.settings.loadWithOverviewMode = desktopSite
                                        browser.reload()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Larger text") },
                                    leadingIcon = { Icon(Icons.Default.TextIncrease, null) },
                                    onClick = {
                                        textZoom = (textZoom + 15).coerceAtMost(200)
                                        browser.settings.textZoom = textZoom
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Smaller text") },
                                    leadingIcon = { Icon(Icons.Default.TextDecrease, null) },
                                    onClick = {
                                        textZoom = (textZoom - 15).coerceAtLeast(60)
                                        browser.settings.textZoom = textZoom
                                    }
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Share link") },
                                    leadingIcon = { Icon(Icons.Default.Share, null) },
                                    enabled = currentUrl.isNotBlank(),
                                    onClick = {
                                        menuOpen = false
                                        val send = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_SUBJECT, pageTitle)
                                            putExtra(Intent.EXTRA_TEXT, currentUrl)
                                        }
                                        context.startActivity(Intent.createChooser(send, "Share link").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Copy link") },
                                    leadingIcon = { Icon(Icons.Default.ContentCopy, null) },
                                    enabled = currentUrl.isNotBlank(),
                                    onClick = {
                                        menuOpen = false
                                        clipboard.setText(AnnotatedString(currentUrl))
                                        Toast.makeText(context, "Link copied", Toast.LENGTH_SHORT).show()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Open in Chrome") },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null) },
                                    enabled = currentUrl.isNotBlank(),
                                    onClick = {
                                        menuOpen = false
                                        runCatching {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(currentUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                        }
                                    }
                                )
                            }
                        }
                    }
                    if (isLoading) {
                        LinearProgressIndicator(
                            progress = { pageProgress / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    AnimatedVisibility(visible = findOpen) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            OutlinedTextField(
                                value = findQuery,
                                onValueChange = {
                                    findQuery = it
                                    if (it.isBlank()) { browser.clearMatches(); findCount = 0; findIndex = 0 }
                                    else browser.findAllAsync(it)
                                },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                placeholder = { Text("Find in page") },
                                shape = RoundedCornerShape(24.dp)
                            )
                            Text(
                                if (findQuery.isBlank()) "" else "$findIndex/$findCount",
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                            IconButton(onClick = { browser.findNext(false) }) { Icon(Icons.Default.KeyboardArrowUp, "Previous match") }
                            IconButton(onClick = { browser.findNext(true) }) { Icon(Icons.Default.KeyboardArrowDown, "Next match") }
                            IconButton(onClick = { findOpen = false; browser.clearMatches() }) { Icon(Icons.Default.Close, "Close find") }
                        }
                    }
                }
            }
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
                Column {
                    // AI actions: the browser is wired to the agent.
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        item {
                            AssistChip(
                                onClick = { askAgent("Summarize this page in a few bullet points.") },
                                label = { Text("Summarize") },
                                leadingIcon = { Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(16.dp)) }
                            )
                        }
                        item {
                            AssistChip(
                                onClick = { askAgent("List the key facts and any dates, numbers or action items on this page.") },
                                label = { Text("Key facts") },
                                leadingIcon = { Icon(Icons.Default.Checklist, null, modifier = Modifier.size(16.dp)) }
                            )
                        }
                        item {
                            AssistChip(
                                onClick = { askAgent("Explain this page in simple words as if to a beginner.") },
                                label = { Text("Explain simply") },
                                leadingIcon = { Icon(Icons.Default.School, null, modifier = Modifier.size(16.dp)) }
                            )
                        }
                        item {
                            AssistChip(
                                onClick = { askDialog = true },
                                label = { Text("Ask about this page") },
                                leadingIcon = { Icon(Icons.Default.QuestionAnswer, null, modifier = Modifier.size(16.dp)) }
                            )
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp)
                    ) {
                        IconButton(onClick = { browser.goBack() }, enabled = canGoBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Previous page")
                        }
                        IconButton(onClick = { browser.goForward() }, enabled = canGoForward) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, "Next page")
                        }
                        IconButton(onClick = { loadAddress("https://www.google.com") }) {
                            Icon(Icons.Default.Home, "Home")
                        }
                        IconButton(
                            onClick = {
                                val saved = store.toggleBookmark(pageTitle, currentUrl)
                                Toast.makeText(context, if (saved) "Bookmarked" else "Bookmark removed", Toast.LENGTH_SHORT).show()
                            },
                            enabled = currentUrl.isNotBlank()
                        ) {
                            Icon(
                                if (isBookmarked) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = if (isBookmarked) "Remove bookmark" else "Add bookmark",
                                tint = if (isBookmarked) com.aurviz.agent.presentation.theme.WarningOrange else LocalContentColor.current
                            )
                        }
                        IconButton(onClick = { showLibrary = true }) {
                            Icon(Icons.Default.CollectionsBookmark, "Bookmarks and history")
                        }
                    }
                }
            }
        }
    ) { insets ->
        Column(modifier = Modifier.fillMaxSize().padding(insets)) {
            pageError?.let {
                Surface(color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f), modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(it, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        TextButton(onClick = { pageError = null; browser.reload() }) { Text("Retry") }
                    }
                }
            }
            if (currentUrl.isBlank() && !isLoading) {
                // Start page shown until the first load completes.
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(12.dp)
                ) {
                    items(quickSites) { (label, url) ->
                        AssistChip(onClick = { loadAddress(url) }, label = { Text(label) })
                    }
                }
            }
            Box(modifier = Modifier.weight(1f)) {
                AndroidView(factory = { browser }, modifier = Modifier.fillMaxSize())
            }
        }
    }

    if (askDialog) {
        var question by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { askDialog = false },
            title = { Text("Ask about this page") },
            text = {
                OutlinedTextField(
                    value = question,
                    onValueChange = { question = it },
                    placeholder = { Text("e.g. What is the deadline mentioned here?") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    enabled = question.isNotBlank(),
                    onClick = { askDialog = false; askAgent(question.trim()) }
                ) { Text("Ask") }
            },
            dismissButton = { TextButton(onClick = { askDialog = false }) { Text("Cancel") } }
        )
    }

    readerText?.let { text ->
        ModalBottomSheet(onDismissRequest = { readerText = null }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(pageTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(BrowserUrl.displayHost(currentUrl), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                MarkdownText(markdown = text.take(20_000))
                Button(
                    onClick = {
                        val content = text.take(8_000)
                        readerText = null
                        onAskAgent("Summarize this page in a few bullet points.", "Page title: $pageTitle\nURL: $currentUrl\n\nPage content:\n$content")
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Summarize with Aurviz")
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (showLibrary) {
        LibrarySheet(
            bookmarks = bookmarks,
            history = history,
            onOpen = { showLibrary = false; loadAddress(it.url) },
            onRemoveBookmark = { store.toggleBookmark(it.title, it.url) },
            onClearHistory = store::clearHistory,
            onDismiss = { showLibrary = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibrarySheet(
    bookmarks: List<SavedPage>,
    history: List<SavedPage>,
    onOpen: (SavedPage) -> Unit,
    onRemoveBookmark: (SavedPage) -> Unit,
    onClearHistory: () -> Unit,
    onDismiss: () -> Unit
) {
    var tab by remember { mutableIntStateOf(0) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp)) {
            TabRow(selectedTabIndex = tab, containerColor = Color.Transparent) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Bookmarks (${bookmarks.size})") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("History (${history.size})") })
            }
            val items = if (tab == 0) bookmarks else history
            if (items.isEmpty()) {
                Text(
                    if (tab == 0) "No bookmarks yet. Tap the star while viewing a page." else "No pages visited yet.",
                    modifier = Modifier.padding(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                    items(items, key = { it.url }) { page ->
                        ListItem(
                            headlineContent = { Text(page.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = { Text(BrowserUrl.displayHost(page.url), maxLines = 1) },
                            leadingContent = { Icon(if (tab == 0) Icons.Default.Star else Icons.Default.History, null) },
                            trailingContent = {
                                if (tab == 0) IconButton(onClick = { onRemoveBookmark(page) }) {
                                    Icon(Icons.Default.Delete, "Remove bookmark")
                                }
                            },
                            modifier = Modifier.clickable { onOpen(page) }
                        )
                    }
                }
                if (tab == 1) TextButton(onClick = onClearHistory, modifier = Modifier.padding(horizontal = 12.dp)) {
                    Text("Clear history")
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
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
        val encoded = webView.evaluateJavascriptSuspending(TEXT_SCRIPT)
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
        "extract_links" -> {
            if (webView.url.isNullOrBlank()) return "Error: Open a webpage first."
            val encoded = webView.evaluateJavascriptSuspending(LINKS_SCRIPT)
            val json = runCatching { JSONTokener(encoded).nextValue() as? String }.getOrNull().orEmpty()
            val links = runCatching { org.json.JSONArray(json) }.getOrNull()
            if (links == null || links.length() == 0) "No links were found on this page."
            else buildString {
                appendLine("Links on ${webView.title.orEmpty()} (${links.length()}):")
                for (n in 0 until links.length()) {
                    val link = links.getJSONObject(n)
                    appendLine("${n + 1}. ${link.optString("t")} - ${link.optString("u")}")
                }
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

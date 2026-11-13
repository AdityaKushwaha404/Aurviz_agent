package com.aurviz.agent.data.tools

import com.aurviz.agent.domain.model.SensitiveAction
import com.aurviz.agent.domain.model.ToolExecutionResponse
import com.aurviz.agent.domain.tool.AgentTool
import com.aurviz.agent.service.web.BrowserAgentController
import java.net.InetAddress
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BrowserAgentTool(
    private val browser: BrowserAgentController,
    override val name: String,
    override val description: String
) : AgentTool {
    private var pendingInteraction: SensitiveAction.BrowserInteraction? = null

    override val isSensitive: Boolean
        get() = name == "click_element" || name == "enter_text"

    override val requiresForeground: Boolean = true

    override suspend fun execute(arguments: Map<String, Any?>): ToolExecutionResponse {
        val validated = validate(arguments)
            ?: return failure("The browser command has missing or invalid parameters.")

        if (isSensitive) {
            val state = browser.state.value
            val interaction = SensitiveAction.BrowserInteraction(
                description = if (name == "click_element") {
                    "Click the selected page element. This may submit a form or perform an external action."
                } else {
                    "Enter the provided text into the selected page field. It will not submit the form."
                },
                operation = name,
                selector = validated["selector"] as String,
                text = validated["text"] as? String,
                pageTitle = state.title,
                pageUrl = state.url
            )
            pendingInteraction = interaction
            return ToolExecutionResponse(
                toolName = name,
                isSuccess = false,
                output = "Review and approve the browser interaction before it runs.",
                sensitiveActionRequired = interaction
            )
        }

        return runCommand(validated)
    }

    override suspend fun executeConfirmed(
        actionId: String,
        payload: Map<String, Any?>
    ): ToolExecutionResponse {
        val pending = pendingInteraction
        if (pending == null || pending.actionId != actionId || pending.operation != name) {
            return failure("The browser approval expired. Request the action again.")
        }
        pendingInteraction = null

        val selector = (payload["selector"] as? String ?: pending.selector).trim()
        val text = payload["text"] as? String ?: pending.text
        if (selector.isBlank() || selector.length > BrowserAgentController.SELECTOR_LIMIT) {
            return failure("The page selector is invalid or too long.")
        }
        if (name == "enter_text" && (text == null || text.length > BrowserAgentController.INPUT_LIMIT)) {
            return failure("The page input is missing or too long.")
        }
        return runCommand(buildMap {
            put("selector", selector)
            if (text != null) put("text", text)
        })
    }

    private suspend fun runCommand(arguments: Map<String, Any?>): ToolExecutionResponse {
        val output = runCatching { browser.execute(name, arguments) }
            .getOrElse { return failure(it.message ?: "Browser command failed.") }
        return ToolExecutionResponse(name, !output.startsWith("Error:"), output)
    }

    private suspend fun validate(arguments: Map<String, Any?>): Map<String, Any?>? {
        return when (name) {
            "open_url" -> {
                val url = (arguments["url"] as? String)?.trim()?.let(::normalizeUrl) ?: return null
                if (!url.isSafePublicHttpsUrl()) return null
                mapOf("url" to url)
            }
            "search_webpage" -> {
                val query = (arguments["query"] as? String)?.trim()?.takeIf { it.isNotEmpty() && it.length <= 500 }
                    ?: return null
                val selector = (arguments["selector"] as? String)
                    ?.takeIf { it.length <= BrowserAgentController.SELECTOR_LIMIT }
                buildMap {
                    put("query", query)
                    if (selector != null) put("selector", selector)
                }
            }
            "read_page", "navigate_back", "navigate_forward", "get_browser_state", "extract_links" -> emptyMap()
            "scroll_page" -> {
                val direction = (arguments["direction"] as? String)?.lowercase()
                    ?.takeIf { it == "up" || it == "down" }
                    ?: return null
                mapOf("direction" to direction)
            }
            "click_element" -> {
                val selector = (arguments["selector"] as? String)?.trim()
                    ?.takeIf { it.isNotEmpty() && it.length <= BrowserAgentController.SELECTOR_LIMIT }
                    ?: return null
                mapOf("selector" to selector)
            }
            "enter_text" -> {
                val selector = (arguments["selector"] as? String)?.trim()
                    ?.takeIf { it.isNotEmpty() && it.length <= BrowserAgentController.SELECTOR_LIMIT }
                    ?: return null
                val text = (arguments["text"] as? String)
                    ?.takeIf { it.length <= BrowserAgentController.INPUT_LIMIT }
                    ?: return null
                mapOf("selector" to selector, "text" to text)
            }
            else -> null
        }
    }

    private fun normalizeUrl(raw: String): String {
        val withScheme = if (raw.contains("://")) raw else "https://$raw"
        return runCatching {
            val uri = URI(withScheme)
            if (uri.scheme.equals("http", true)) URI("https", uri.userInfo, uri.host, uri.port, uri.path, uri.query, uri.fragment).toString()
            else uri.toString()
        }.getOrDefault(withScheme)
    }

    private suspend fun String.isSafePublicHttpsUrl(): Boolean = withContext(Dispatchers.IO) {
        val uri = runCatching { URI(this@isSafePublicHttpsUrl) }.getOrNull() ?: return@withContext false
        val host = uri.host?.lowercase() ?: return@withContext false
        if (!uri.scheme.equals("https", true) || uri.userInfo != null) return@withContext false
        if (host == "localhost" || host.endsWith(".localhost") || host.endsWith(".local") ||
            host.endsWith(".internal") || host.endsWith(".test") || host.endsWith(".invalid")
        ) return@withContext false
        runCatching {
            val addresses = InetAddress.getAllByName(host)
            addresses.isNotEmpty() && addresses.none { address ->
                val bytes = address.address
                val uniqueLocalIpv6 = bytes.size == 16 && (bytes[0].toInt() and 0xfe) == 0xfc
                address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
                    address.isSiteLocalAddress || address.isMulticastAddress || uniqueLocalIpv6
            }
        }.getOrDefault(false)
    }

    private fun failure(message: String) = ToolExecutionResponse(name, false, message)
}
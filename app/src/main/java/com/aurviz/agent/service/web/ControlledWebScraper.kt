package com.aurviz.agent.service.web

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Dns
import okhttp3.ResponseBody
import java.util.concurrent.TimeUnit
import java.net.InetAddress
import java.net.URI
import java.net.UnknownHostException
import java.io.ByteArrayOutputStream
import java.util.regex.Pattern

class ControlledWebScraper {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .dns(object : Dns {
            override fun lookup(hostname: String): List<InetAddress> {
                val addresses = InetAddress.getAllByName(hostname).toList()
                if (addresses.any(::isPrivateAddress)) {
                    throw UnknownHostException("Private and local network destinations are blocked")
                }
                return addresses
            }
        })
        .build()

    suspend fun browseAndExtract(
        url: String,
        action: String = "extract_summary",
        maxChars: Int = 2000
    ): String = withContext(Dispatchers.IO) {
        val normalizedUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
            "https://$url"
        } else url

        try {
            val uri = URI(normalizedUrl)
            val host = uri.host?.lowercase()
                ?: return@withContext "Error: Invalid website address."
            if (isPrivateOrLocalHost(host)) {
                return@withContext "Error: Requests to private or local network addresses are blocked."
            }
            val resolvedAddresses = runCatching { InetAddress.getAllByName(host) }.getOrElse {
                return@withContext "Error: The website address could not be resolved."
            }
            if (resolvedAddresses.any(::isPrivateAddress)) {
                return@withContext "Error: Requests to private or local network addresses are blocked."
            }
            if (uri.scheme != "https") {
                return@withContext "Error: Only public HTTPS websites can be fetched."
            }

            val request = Request.Builder()
                .url(normalizedUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) Mobile Safari/537.36 AurvizAgent/1.0")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext "Error: HTTP ${response.code} ${response.message} while accessing $normalizedUrl"
                }
                if ((response.body?.contentLength() ?: 0L) > MAX_RESPONSE_BYTES) {
                    return@withContext "Error: The page is too large to process safely."
                }
                val body = response.body ?: return@withContext "Error: Empty response received from $normalizedUrl"
                val html = try {
                    readBodyLimited(body)
                } catch (_: ResponseTooLargeException) {
                    return@withContext "Error: The page is too large to process safely."
                }
                when (action.lowercase()) {
                    "extract_headlines" -> extractHeadlines(html)
                    "extract_links" -> extractLinks(html, normalizedUrl)
                    else -> cleanHtmlToPlainText(html, maxChars)
                }
            }
        } catch (e: Exception) {
            "Error: Could not fetch $normalizedUrl: ${e.localizedMessage ?: e.javaClass.simpleName}"
        }
    }

    private fun isPrivateOrLocalHost(host: String): Boolean =
        host == "localhost" || host.endsWith(".localhost") || host.endsWith(".local") ||
            host.endsWith(".internal") || host.endsWith(".test") || host.endsWith(".invalid")

    private fun isPrivateAddress(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
            address.isSiteLocalAddress || address.isMulticastAddress
        ) return true
        val bytes = address.address.map { it.toInt() and 0xff }
        return if (bytes.size == 4) {
            bytes[0] == 0 || bytes[0] == 10 || bytes[0] == 127 ||
                (bytes[0] == 169 && bytes[1] == 254) ||
                (bytes[0] == 172 && bytes[1] in 16..31) ||
                (bytes[0] == 192 && bytes[1] == 168) ||
                (bytes[0] == 100 && bytes[1] in 64..127) || bytes[0] >= 224
        } else {
            bytes[0] and 0xfe == 0xfc
        }
    }

    private fun readBodyLimited(body: ResponseBody): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        body.byteStream().use { input ->
            while (true) {
                val remaining = (MAX_RESPONSE_BYTES + 1 - output.size()).toInt()
                if (remaining <= 0) throw ResponseTooLargeException()
                val count = input.read(buffer, 0, minOf(buffer.size, remaining))
                if (count < 0) break
                output.write(buffer, 0, count)
                if (output.size() > MAX_RESPONSE_BYTES) throw ResponseTooLargeException()
            }
        }
        return output.toByteArray().toString(Charsets.UTF_8)
    }

    private class ResponseTooLargeException : Exception()

    private fun extractHeadlines(html: String): String {
        val headlines = mutableListOf<String>()
        val headingRegex = Regex("""<h[1-3][^>]*>(.*?)</h[1-3]>""", RegexOption.IGNORE_CASE)
        headingRegex.findAll(html).take(10).forEach { match ->
            val text = stripTags(match.groupValues[1]).trim()
            if (text.isNotBlank() && text.length > 5) {
                headlines.add("• $text")
            }
        }
        return if (headlines.isEmpty()) "No explicit headlines found." else headlines.joinToString("\n")
    }

    private fun extractLinks(html: String, baseUrl: String): String {
        val links = mutableListOf<String>()
        val linkRegex = Regex("""<a\s+[^>]*href=["']([^"']+)["'][^>]*>(.*?)</a>""", RegexOption.IGNORE_CASE)
        linkRegex.findAll(html).take(8).forEach { match ->
            val href = match.groupValues[1]
            val text = stripTags(match.groupValues[2]).trim()
            if (text.isNotBlank() && !href.startsWith("#") && !href.startsWith("javascript")) {
                links.add("• [$text]($href)")
            }
        }
        return if (links.isEmpty()) "No links found." else links.joinToString("\n")
    }

    private fun cleanHtmlToPlainText(html: String, maxChars: Int): String {
        // Remove scripts, styles, comments
        var clean = html
            .replace(Regex("""<script[\s\S]*?</script>""", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("""<style[\s\S]*?</style>""", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("""<!--[\s\S]*?-->"""), " ")

        // Replace paragraph and break tags with newlines
        clean = clean.replace(Regex("""<(p|div|br|h[1-6]|li)[^>]*>""", RegexOption.IGNORE_CASE), "\n")

        // Strip remaining HTML tags
        clean = stripTags(clean)

        // Collapse whitespace
        clean = clean.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")

        return if (clean.length > maxChars) {
            clean.take(maxChars) + "\n...[truncated for agent context]"
        } else {
            clean
        }
    }

    private fun stripTags(input: String): String {
        return input.replace(Regex("""<[^>]*>"""), " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
    }

    companion object {
        private const val MAX_RESPONSE_BYTES = 2L * 1024 * 1024
    }
}

package com.aurviz.agent.service.web

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class ControlledWebScraper {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
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
            val request = Request.Builder()
                .url(normalizedUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) Mobile Safari/537.36 AurvizAgent/1.0")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext "HTTP error: ${response.code} ${response.message} while accessing $normalizedUrl"
            }

            val html = response.body?.string() ?: return@withContext "Empty response received from $normalizedUrl"

            when (action.lowercase()) {
                "extract_headlines" -> extractHeadlines(html)
                "extract_links" -> extractLinks(html, normalizedUrl)
                else -> cleanHtmlToPlainText(html, maxChars)
            }
        } catch (e: Exception) {
            "Error navigating to $normalizedUrl: ${e.localizedMessage ?: e.javaClass.simpleName}"
        }
    }

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
}

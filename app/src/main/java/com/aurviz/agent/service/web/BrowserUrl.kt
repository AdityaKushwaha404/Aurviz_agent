package com.aurviz.agent.service.web

import java.net.URLEncoder

/** Turns whatever the user typed into the address bar into a URL to load. */
object BrowserUrl {

    private val HOST_LIKE = Regex("^[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)+(:\\d+)?([/?#].*)?$")

    /** Returns an https URL, a web-search URL for plain text, or null for blank input. */
    fun resolve(input: String): String? {
        val text = input.trim()
        if (text.isEmpty()) return null
        return when {
            text.startsWith("http://", ignoreCase = true) -> "https://" + text.substring(7)
            text.contains("://") -> text
            !text.contains(' ') && HOST_LIKE.matches(text) -> "https://$text"
            else -> "https://www.google.com/search?q=" + URLEncoder.encode(text, "UTF-8")
        }
    }

    /** Short host for display, without scheme or "www.". */
    fun displayHost(url: String): String = runCatching {
        java.net.URI(url).host.orEmpty().removePrefix("www.")
    }.getOrDefault("").ifBlank { url }
}

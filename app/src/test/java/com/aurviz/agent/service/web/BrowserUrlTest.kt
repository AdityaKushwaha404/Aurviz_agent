package com.aurviz.agent.service.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserUrlTest {
    @Test fun domainsGetHttps() {
        assertEquals("https://news.ycombinator.com", BrowserUrl.resolve("news.ycombinator.com"))
        assertEquals("https://github.com/a/b?x=1", BrowserUrl.resolve("github.com/a/b?x=1"))
    }

    @Test fun httpIsUpgradedAndHttpsKept() {
        assertEquals("https://example.org/a", BrowserUrl.resolve("http://example.org/a"))
        assertEquals("https://example.org/a", BrowserUrl.resolve("https://example.org/a"))
    }

    @Test fun plainTextBecomesASearch() {
        val url = BrowserUrl.resolve("best android jobs in pune")!!
        assertTrue(url.startsWith("https://www.google.com/search?q=best+android+jobs"))
    }

    @Test fun blankIsNull() = assertNull(BrowserUrl.resolve("   "))

    @Test fun displayHostStripsWww() {
        assertEquals("wikipedia.org", BrowserUrl.displayHost("https://www.wikipedia.org/wiki/Android"))
    }
}

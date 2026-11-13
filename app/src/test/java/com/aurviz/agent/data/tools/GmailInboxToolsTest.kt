package com.aurviz.agent.data.tools

import com.aurviz.agent.data.remote.BackendApiException
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class GmailInboxToolsTest {

    @Test
    fun formatSearch_listsIdsSendersAndSnippets() {
        val result = JSONObject()
            .put("query", "subject:interview")
            .put(
                "messages", JSONArray().put(
                    JSONObject().put("id", "m1").put("from", "HR <hr@co.com>").put("subject", "Interview invite")
                        .put("date", "Mon").put("snippet", "Please confirm your slot").put("unread", true)
                )
            )
        val text = GmailSearchTool.formatSearch(result)
        assertTrue(text.contains("[id=m1]"))
        assertTrue(text.contains("(unread)"))
        assertTrue(text.contains("HR <hr@co.com>"))
        assertTrue(text.contains("Please confirm your slot"))
    }

    @Test
    fun formatSearch_reportsNoMatchesWithoutInventingData() {
        val text = GmailSearchTool.formatSearch(JSONObject().put("query", "is:important").put("messages", JSONArray()))
        assertEquals("No emails matched 'is:important'.", text)
    }

    @Test
    fun errors_classifyTransientVersusPermanent() {
        assertTrue(GmailToolErrors.failure("search_emails", IOException("offline")).retryable)
        assertTrue(GmailToolErrors.failure("search_emails", BackendApiException(503, "down")).retryable)
        val scope = GmailToolErrors.failure("search_emails", BackendApiException(403, "Reconnect Gmail to allow reading"))
        assertFalse(scope.retryable)
        assertTrue(scope.output.contains("Reconnect"))
        assertFalse(GmailToolErrors.failure("search_emails", BackendApiException(401, "x")).retryable)
    }
}

package com.aurviz.agent.presentation.agent

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Drafts
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.ui.graphics.vector.ImageVector

/** Human-friendly names, icons and progress text for agent tools (instead of raw ids). */
object ToolPresentation {

    fun title(toolName: String): String = when (toolName) {
        "search_emails" -> "Search Gmail"
        "read_email" -> "Read email"
        "create_email_draft" -> "Save Gmail draft"
        "send_email" -> "Send email"
        "manage_memory" -> "Memory"
        "schedule_task" -> "Schedule task"
        "browse_web" -> "Fetch web page"
        "open_url" -> "Open website"
        "search_webpage" -> "Search page"
        "read_page" -> "Read page"
        "click_element" -> "Click on page"
        "enter_text" -> "Type on page"
        "scroll_page" -> "Scroll page"
        "navigate_back", "navigate_forward" -> "Navigate"
        "get_browser_state" -> "Check browser"
        else -> toolName.replace('_', ' ').replaceFirstChar { it.uppercase() }
    }

    /** Shown while the tool runs, e.g. in the top bar and the typing indicator. */
    fun runningLabel(toolName: String): String = when (toolName) {
        "search_emails" -> "Searching your inbox…"
        "read_email" -> "Reading an email…"
        "create_email_draft" -> "Saving a draft…"
        "send_email" -> "Preparing your email…"
        "manage_memory" -> "Updating memory…"
        "schedule_task" -> "Scheduling your task…"
        "browse_web", "open_url" -> "Opening the website…"
        "search_webpage" -> "Searching the page…"
        "read_page" -> "Reading the page…"
        else -> "Working on it…"
    }

    fun icon(toolName: String): ImageVector = when (toolName) {
        "search_emails" -> Icons.Default.Search
        "read_email" -> Icons.Default.MarkEmailRead
        "create_email_draft" -> Icons.Default.Drafts
        "send_email" -> Icons.Default.Send
        "manage_memory" -> Icons.Default.Psychology
        "schedule_task" -> Icons.Default.Schedule
        "browse_web", "open_url", "search_webpage", "read_page", "click_element",
        "enter_text", "scroll_page", "navigate_back", "navigate_forward", "get_browser_state" -> Icons.Default.Language
        else -> if (toolName.contains("email")) Icons.Default.Email else Icons.Default.AutoAwesome
    }
}

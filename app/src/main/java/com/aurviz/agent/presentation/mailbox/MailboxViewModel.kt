package com.aurviz.agent.presentation.mailbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aurviz.agent.core.security.SecurePreferences
import com.aurviz.agent.data.mail.MailSourceManager
import com.aurviz.agent.domain.mail.MailGateway
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

enum class MailFolder(val label: String, val query: String) {
    INBOX("Inbox", "in:inbox"),
    SENT("Sent", "in:sent"),
    DRAFTS("Drafts", "in:drafts")
}

data class MailItem(
    val id: String,
    val from: String,
    val to: String,
    val subject: String,
    val date: String,
    val snippet: String,
    val unread: Boolean
)

data class MailDetail(val item: MailItem, val body: String)

data class MailboxUiState(
    val folder: MailFolder = MailFolder.INBOX,
    val items: List<MailItem> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val detail: MailDetail? = null,
    val detailLoading: Boolean = false
)

/** Shows whichever mailbox is selected (shared test inbox, demo mailbox or the user's Gmail) and refreshes live. */
class MailboxViewModel(
    private val mail: MailGateway,
    private val preferences: SecurePreferences
) : ViewModel() {

    private val _state = MutableStateFlow(MailboxUiState())
    val state: StateFlow<MailboxUiState> = _state.asStateFlow()
    val source: StateFlow<String> = preferences.mailSource
    val isDemo: Boolean get() = mail.isDemo

    init {
        viewModelScope.launch {
            // Reload when the agent saves a draft, sends mail, or the demo is reset.
            mail.revision.collect { refresh() }
        }
    }

    fun selectFolder(folder: MailFolder) {
        _state.value = _state.value.copy(folder = folder, items = emptyList())
        refresh()
    }

    fun refresh() {
        val folder = _state.value.folder
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            try {
                val result = mail.search(folder.query, 15)
                val messages = result.optJSONArray("messages")
                val items = (0 until (messages?.length() ?: 0)).map { toItem(messages!!.getJSONObject(it)) }
                if (_state.value.folder == folder) {
                    _state.value = _state.value.copy(items = items, loading = false)
                }
            } catch (error: Exception) {
                _state.value = _state.value.copy(loading = false, error = friendly(error))
            }
        }
    }

    fun open(item: MailItem) {
        _state.value = _state.value.copy(detail = MailDetail(item, ""), detailLoading = true)
        viewModelScope.launch {
            try {
                val full = mail.read(item.id)
                _state.value = _state.value.copy(
                    detail = MailDetail(toItem(full), full.optString("body")),
                    detailLoading = false
                )
            } catch (error: Exception) {
                _state.value = _state.value.copy(
                    detail = MailDetail(item, friendly(error)),
                    detailLoading = false
                )
            }
        }
    }

    fun closeDetail() {
        _state.value = _state.value.copy(detail = null)
    }

    fun resetDemo() = mail.resetDemo()

    private fun toItem(o: JSONObject) = MailItem(
        id = o.optString("id"),
        from = o.optString("from"),
        to = o.optString("to"),
        subject = o.optString("subject").ifBlank { "(no subject)" },
        date = o.optString("date"),
        snippet = o.optString("snippet"),
        unread = o.optBoolean("unread")
    )

    private fun friendly(error: Exception): String = when {
        error.message?.contains("reconnect", ignoreCase = true) == true ||
            error.message?.contains("not granted", ignoreCase = true) == true ->
            "Gmail access needs to be reconnected. Open Settings, Mailbox."
        error is java.io.IOException -> "Could not reach the mail service. Check your connection and refresh."
        else -> error.message ?: "Could not load this mailbox."
    }

    companion object {
        val SHARED_EMAIL = MailSourceManager.SHARED_TEST_EMAIL
    }
}

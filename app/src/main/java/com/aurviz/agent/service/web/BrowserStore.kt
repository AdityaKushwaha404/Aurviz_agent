package com.aurviz.agent.service.web

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class SavedPage(val title: String, val url: String, val savedAt: Long)

/** Bookmarks and browsing history for the controlled browser, persisted locally. */
class BrowserStore(context: Context) {

    private val prefs = context.getSharedPreferences("aurviz_browser", Context.MODE_PRIVATE)

    private val _bookmarks = MutableStateFlow(read(KEY_BOOKMARKS))
    val bookmarks: StateFlow<List<SavedPage>> = _bookmarks.asStateFlow()

    private val _history = MutableStateFlow(read(KEY_HISTORY))
    val history: StateFlow<List<SavedPage>> = _history.asStateFlow()

    fun isBookmarked(url: String): Boolean = _bookmarks.value.any { it.url == url }

    /** Adds the page, or removes it when it is already bookmarked. Returns true if it is now saved. */
    fun toggleBookmark(title: String, url: String): Boolean {
        if (url.isBlank()) return false
        val current = _bookmarks.value
        val updated = if (current.any { it.url == url }) current.filterNot { it.url == url }
        else listOf(SavedPage(title.ifBlank { url }, url, System.currentTimeMillis())) + current
        write(KEY_BOOKMARKS, updated)
        _bookmarks.value = updated
        return updated.any { it.url == url }
    }

    fun addHistory(title: String, url: String) {
        if (url.isBlank()) return
        val updated = (listOf(SavedPage(title.ifBlank { url }, url, System.currentTimeMillis())) +
            _history.value.filterNot { it.url == url }).take(MAX_HISTORY)
        write(KEY_HISTORY, updated)
        _history.value = updated
    }

    fun removeHistory(url: String) {
        val updated = _history.value.filterNot { it.url == url }
        write(KEY_HISTORY, updated)
        _history.value = updated
    }

    fun clearHistory() {
        write(KEY_HISTORY, emptyList())
        _history.value = emptyList()
    }

    private fun read(key: String): List<SavedPage> = runCatching {
        val array = JSONArray(prefs.getString(key, "[]"))
        (0 until array.length()).map {
            val o = array.getJSONObject(it)
            SavedPage(o.getString("title"), o.getString("url"), o.optLong("at"))
        }
    }.getOrDefault(emptyList())

    private fun write(key: String, pages: List<SavedPage>) {
        val array = JSONArray()
        pages.forEach { array.put(JSONObject().put("title", it.title).put("url", it.url).put("at", it.savedAt)) }
        prefs.edit().putString(key, array.toString()).apply()
    }

    private companion object {
        const val KEY_BOOKMARKS = "bookmarks"
        const val KEY_HISTORY = "history"
        const val MAX_HISTORY = 60
    }
}

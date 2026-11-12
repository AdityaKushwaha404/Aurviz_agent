package com.aurviz.agent.service.web

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.withTimeout

data class BrowserSessionState(
    val title: String = "Enter a public website address",
    val url: String = "",
    val isLoading: Boolean = false,
    val progress: Int = 0,
    val error: String? = null
)

class BrowserCommand internal constructor(
    val name: String,
    val arguments: Map<String, Any?>,
    internal val result: CompletableDeferred<String>
) {
    fun complete(output: String) {
        result.complete(output)
    }
}

class BrowserAgentController {
    private val commandChannel = Channel<BrowserCommand>(Channel.BUFFERED)
    private val browserScreenChannel = Channel<Unit>(Channel.CONFLATED)
    private val _state = MutableStateFlow(BrowserSessionState())

    val state: StateFlow<BrowserSessionState> = _state.asStateFlow()
    val commands = commandChannel.receiveAsFlow()
    val browserScreenRequests = browserScreenChannel.receiveAsFlow()

    @Volatile
    var isHostAvailable: Boolean = false
        private set

    private var navigationResult: CompletableDeferred<String>? = null

    suspend fun execute(name: String, arguments: Map<String, Any?> = emptyMap()): String {
        if (!isHostAvailable) browserScreenChannel.send(Unit)
        val result = CompletableDeferred<String>()
        commandChannel.send(BrowserCommand(name, arguments, result))
        return withTimeout(COMMAND_TIMEOUT_MS) { result.await() }
    }

    fun setHostAvailable(available: Boolean) {
        isHostAvailable = available
    }

    fun updateState(
        title: String? = null,
        url: String? = null,
        isLoading: Boolean? = null,
        progress: Int? = null,
        error: String? = null,
        clearError: Boolean = false
    ) {
        _state.value = _state.value.copy(
            title = title ?: _state.value.title,
            url = url ?: _state.value.url,
            isLoading = isLoading ?: _state.value.isLoading,
            progress = progress ?: _state.value.progress,
            error = if (clearError) null else error ?: _state.value.error
        )
    }

    fun beginNavigation(): CompletableDeferred<String> =
        CompletableDeferred<String>().also { navigationResult = it }

    fun finishNavigation(output: String) {
        navigationResult?.complete(output)
        navigationResult = null
    }

    companion object {
        const val COMMAND_TIMEOUT_MS = 40_000L
        const val PAGE_TEXT_LIMIT = 12_000
        const val SELECTOR_LIMIT = 512
        const val INPUT_LIMIT = 2_000
    }
}

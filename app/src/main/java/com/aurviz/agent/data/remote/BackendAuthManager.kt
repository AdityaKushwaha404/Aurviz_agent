package com.aurviz.agent.data.remote

import android.net.Uri
import com.aurviz.agent.core.security.SecurePreferences
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class BackendAuthManager(
    private val api: BackendApiClient,
    private val securePreferences: SecurePreferences
) {
    private val _connectionState = MutableStateFlow("Gmail not connected")
    val connectionState: StateFlow<String> = _connectionState.asStateFlow()

    fun isConnected(): Boolean = securePreferences.isSharedSource() || !securePreferences.getBackendAccessToken().isNullOrBlank()

    suspend fun sendEmail(recipient: String, subject: String, body: String, idempotencyKey: String) =
        withContext(Dispatchers.IO) {
            val token = currentAccessToken()
                ?: throw IOException("Connect Gmail in Settings before sending email.")
            api.sendEmail(token, recipient, subject, body, idempotencyKey)
        }

    suspend fun scheduleEmail(
        recipient: String,
        subject: String,
        body: String,
        scheduledAt: String,
        timeZone: String,
        idempotencyKey: String
    ) = withContext(Dispatchers.IO) {
        val token = currentAccessToken()
            ?: throw IOException("Connect Gmail in Settings before scheduling email.")
        api.scheduleEmail(token, recipient, subject, body, scheduledAt, timeZone, idempotencyKey)
    }

    suspend fun searchEmails(query: String, maxResults: Int) = withContext(Dispatchers.IO) {
        api.searchEmails(requireToken(), query, maxResults)
    }

    suspend fun readEmail(messageId: String) = withContext(Dispatchers.IO) {
        api.readEmail(requireToken(), messageId)
    }

    suspend fun createDraft(recipient: String, subject: String, body: String) = withContext(Dispatchers.IO) {
        api.createDraft(requireToken(), recipient, subject, body)
    }

    private suspend fun requireToken(): String = currentAccessToken()
        ?: throw IOException("Connect Gmail in Settings first.")

    /**
     * Connects to the shared test inbox (real Gmail, no credentials). Returns a user-readable error
     * on failure so the UI can offer the demo mailbox instead.
     */
    suspend fun connectSharedTestAccount(): Result<String> = withContext(Dispatchers.IO) {
        _connectionState.value = "Connecting to the shared test inbox…"
        try {
            val session = api.startSharedSession()
            securePreferences.saveBackendSession(session.accessToken, Instant.parse(session.expiresAt).toEpochMilli())
            _connectionState.value = "Connected as ${session.email}"
            Result.success(session.email)
        } catch (error: Exception) {
            val message = when {
                error is BackendApiException && (error.statusCode == 404 || error.statusCode >= 500 && error.statusCode != 503) ->
                    "The shared test inbox is not reachable right now. The server may be waking up or updating."
                error is BackendApiException -> error.message ?: "The shared test inbox is not available right now."
                else -> safeMessage(error)
            }
            _connectionState.value = message
            Result.failure(IOException(message))
        }
    }

    /** Signs this device out of whichever backend session it holds without touching the server-side account. */
    suspend fun disconnectKeepingAccount() = withContext(Dispatchers.IO) {
        val token = securePreferences.getBackendAccessToken()
        if (!token.isNullOrBlank()) runCatching { api.revokeSession(token) }
        securePreferences.clearBackendAccessToken()
        _connectionState.value = "Gmail not connected"
    }

    suspend fun handleOAuthCallback(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        if (uri.scheme != CALLBACK_SCHEME || uri.host != CALLBACK_HOST || uri.path != CALLBACK_PATH) {
            return@withContext false
        }
        val code = uri.getQueryParameter("code")
        if (code.isNullOrBlank()) {
            _connectionState.value = "Google authorization did not return a code. Try again."
            return@withContext true
        }

        _connectionState.value = "Completing secure Gmail connection…"
        try {
            val session = api.exchangeAuthorizationCode(code)
            securePreferences.saveBackendSession(session.accessToken, Instant.parse(session.expiresAt).toEpochMilli())
            val email = api.getAccount(session.accessToken)
            _connectionState.value = "Connected as $email"
        } catch (error: Exception) {
            if (error is BackendApiException && error.statusCode == 401) {
                securePreferences.clearBackendAccessToken()
            }
            _connectionState.value = safeMessage(error)
        }
        true
    }

    suspend fun refreshConnectionState() = withContext(Dispatchers.IO) {
        val accessToken = securePreferences.getBackendAccessToken()
        if (accessToken.isNullOrBlank()) {
            _connectionState.value = "Gmail not connected"
            return@withContext
        }
        try {
            val currentToken = currentAccessToken() ?: return@withContext
            val email = api.getAccount(currentToken)
            _connectionState.value = "Connected as $email"
        } catch (error: BackendApiException) {
            if (error.statusCode == 401) {
                securePreferences.clearBackendAccessToken()
                _connectionState.value = "Gmail session expired. Connect again."
            } else {
                _connectionState.value = safeMessage(error)
            }
        } catch (error: Exception) {
            _connectionState.value = safeMessage(error)
        }
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        val accessToken = securePreferences.getBackendAccessToken()
        try {
            if (securePreferences.isSharedSource()) {
                // The shared inbox belongs to the server; only end this device's session.
                if (!accessToken.isNullOrBlank()) runCatching { api.revokeSession(accessToken) }
            } else if (!accessToken.isNullOrBlank()) api.disconnect(accessToken)
            securePreferences.clearBackendAccessToken()
            _connectionState.value = "Gmail not connected"
        } catch (error: Exception) {
            _connectionState.value = safeMessage(error)
        }
    }

    private suspend fun currentAccessToken(): String? {
        val shared = securePreferences.isSharedSource()
        var accessToken = securePreferences.getBackendAccessToken()?.takeIf(String::isNotBlank)
        val expiresAt = securePreferences.getBackendSessionExpiresAt()
        if (shared) {
            // Shared sessions are short-lived: start a fresh one instead of rotating.
            if (accessToken == null || expiresAt <= System.currentTimeMillis() + 60_000) {
                connectSharedTestAccount().getOrNull()
                accessToken = securePreferences.getBackendAccessToken()?.takeIf(String::isNotBlank)
            }
            return accessToken
        }
        accessToken ?: return null
        if (expiresAt <= System.currentTimeMillis() + REFRESH_WINDOW_MS) {
            val refreshed = api.refreshSession(accessToken)
            securePreferences.saveBackendSession(refreshed.accessToken, Instant.parse(refreshed.expiresAt).toEpochMilli())
            return refreshed.accessToken
        }
        return accessToken
    }

    private fun safeMessage(error: Exception): String = when (error) {
        is BackendApiException -> error.message ?: "Aurviz backend request failed."
        is IOException -> "Could not reach Aurviz. Check your connection and try again."
        else -> "Gmail connection could not be completed. Try again."
    }

    companion object {
        private const val CALLBACK_SCHEME = "aurviz"
        private const val CALLBACK_HOST = "oauth"
        private const val CALLBACK_PATH = "/callback"
        private const val REFRESH_WINDOW_MS = 7L * 24 * 60 * 60 * 1000
    }
}

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
    private val _connectionState = MutableStateFlow(if (securePreferences.getBackendAccessToken().isNullOrBlank()) "Gmail not connected" else "Checking Gmail…")
    val connectionState: StateFlow<String> = _connectionState.asStateFlow()

    fun isConnected(): Boolean = !securePreferences.getBackendAccessToken().isNullOrBlank()

    suspend fun sendEmail(recipient: String, subject: String, body: String, idempotencyKey: String) =
        withContext(Dispatchers.IO) {
            val token = currentAccessToken()
                ?: throw IOException("Sign in under Settings, Mailbox before sending email.")
            api.sendEmail(token, recipient, subject, body, idempotencyKey)
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
        ?: throw IOException("Sign in under Settings, Mailbox first.")

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
            if (!accessToken.isNullOrBlank()) {
                try {
                    api.disconnect(accessToken)
                } catch (error: BackendApiException) {
                    // The shared test account can never be revoked; just end this device's session.
                    if (error.statusCode == 403) runCatching { api.revokeSession(accessToken) } else throw error
                }
            }
            securePreferences.clearBackendAccessToken()
            _connectionState.value = "Gmail not connected"
        } catch (error: Exception) {
            _connectionState.value = safeMessage(error)
        }
    }

    private suspend fun currentAccessToken(): String? {
        val accessToken = securePreferences.getBackendAccessToken()?.takeIf(String::isNotBlank) ?: return null
        if (securePreferences.getBackendSessionExpiresAt() <= System.currentTimeMillis() + REFRESH_WINDOW_MS) {
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

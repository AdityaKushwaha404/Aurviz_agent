package com.aurviz.agent.data.remote

import android.app.Activity
import android.content.Intent
import android.content.IntentSender
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds only the short-lived Gmail access token in process memory.
 * Google Identity Services manages the authorization/token cache.
 */
class GmailAccessManager {
    private val _connectionState = MutableStateFlow("Gmail not connected")
    val connectionState: StateFlow<String> = _connectionState.asStateFlow()

    @Volatile
    private var token: String? = null

    fun authorize(activity: Activity, launchResolution: (IntentSender) -> Unit) {
        _connectionState.value = "Connecting to Gmail…"
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(GMAIL_SEND_SCOPE)))
            .build()

        Identity.getAuthorizationClient(activity)
            .authorize(request)
            .addOnSuccessListener { result ->
                if (result.hasResolution()) {
                    val pendingIntent = result.pendingIntent
                    if (pendingIntent == null) {
                        _connectionState.value = "Gmail authorization could not be started"
                    } else {
                        launchResolution(pendingIntent.intentSender)
                    }
                } else {
                    acceptAuthorizationResult(result)
                }
            }
            .addOnFailureListener {
                _connectionState.value = "Gmail connection failed. Try again."
            }
    }

    fun handleAuthorizationResult(activity: Activity, data: Intent?): Boolean = try {
        val result = Identity.getAuthorizationClient(activity).getAuthorizationResultFromIntent(data)
        acceptAuthorizationResult(result)
        true
    } catch (_: Exception) {
        token = null
        _connectionState.value = "Gmail authorization was cancelled or failed"
        false
    }

    fun accessToken(): String? = token

    fun disconnect() {
        token = null
        _connectionState.value = "Gmail not connected"
    }

    private fun acceptAuthorizationResult(result: AuthorizationResult) {
        token = result.accessToken?.takeIf { it.isNotBlank() }
        _connectionState.value = if (token == null) {
            "Gmail authorization returned no access token"
        } else {
            "Gmail connected for this app session"
        }
    }

    companion object {
        const val GMAIL_SEND_SCOPE = "https://www.googleapis.com/auth/gmail.send"
    }
}

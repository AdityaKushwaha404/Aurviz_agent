package com.aurviz.agent.data.mail

import com.aurviz.agent.core.security.SecurePreferences
import com.aurviz.agent.data.remote.BackendAuthManager

/**
 * Switches between the three mailbox sources and keeps the backend session consistent:
 *  - SHARED: real Gmail on the shared test account; one tap, no credentials.
 *  - DEMO: offline sample mailbox.
 *  - OWN: the user's own Gmail through Google sign-in.
 */
class MailSourceManager(
    private val preferences: SecurePreferences,
    private val auth: BackendAuthManager
) {
    val source = preferences.mailSource

    /** Tries the shared test inbox. On failure the previous source stays selected. */
    suspend fun useShared(): Result<String> {
        val previous = preferences.mailSource.value
        if (previous == SecurePreferences.SOURCE_OWN) auth.disconnectKeepingAccount()
        val result = auth.connectSharedTestAccount()
        if (result.isSuccess) preferences.setMailSource(SecurePreferences.SOURCE_SHARED)
        return result
    }

    fun useDemo() = preferences.setMailSource(SecurePreferences.SOURCE_DEMO)

    /** Switches to the user's own Gmail. A shared session is ended so the Connect button shows. */
    suspend fun useOwn() {
        if (preferences.mailSource.value == SecurePreferences.SOURCE_SHARED) auth.disconnectKeepingAccount()
        preferences.setMailSource(SecurePreferences.SOURCE_OWN)
        auth.refreshConnectionState()
    }

    /** Called at launch: if the shared inbox is selected, make sure a session exists. */
    suspend fun ensureSession() {
        if (preferences.mailSource.value == SecurePreferences.SOURCE_SHARED &&
            preferences.getBackendAccessToken().isNullOrBlank()
        ) auth.connectSharedTestAccount()
    }

    companion object {
        /** The shared test inbox shown to reviewers. The account itself is held on the server, never in the app. */
        const val SHARED_TEST_EMAIL = "adityatesting404@gmail.com"
    }
}

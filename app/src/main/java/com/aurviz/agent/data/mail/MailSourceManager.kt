package com.aurviz.agent.data.mail

import com.aurviz.agent.core.security.SecurePreferences
import com.aurviz.agent.data.remote.BackendAuthManager

/**
 * Switches between the three mailbox sources:
 *  - TEST: real Gmail on the shared test account (reviewers sign in with the credentials shown in the app),
 *  - OWN: the user's own Gmail,
 *  - DEMO: the offline sample mailbox.
 * TEST and OWN both use Google sign-in; they differ only in the guidance shown. Switching between them
 * ends the current sign-in so the next account starts cleanly. Switching to DEMO keeps the sign-in.
 */
class MailSourceManager(
    private val preferences: SecurePreferences,
    private val auth: BackendAuthManager
) {
    val source = preferences.mailSource

    suspend fun select(target: String) {
        val current = preferences.mailSource.value
        val realAccounts = setOf(SecurePreferences.SOURCE_TEST, SecurePreferences.SOURCE_OWN)
        if (current != target && current in realAccounts && target in realAccounts) {
            auth.disconnectKeepingAccount()
        }
        preferences.setMailSource(target)
        if (target in realAccounts) auth.refreshConnectionState()
    }
}

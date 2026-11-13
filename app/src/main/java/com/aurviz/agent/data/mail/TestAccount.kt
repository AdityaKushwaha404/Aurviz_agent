package com.aurviz.agent.data.mail

import com.aurviz.agent.BuildConfig

/**
 * The throwaway Gmail account offered to reviewers. The email is public; the password is injected
 * at build time from the git-ignored local.properties (`test.account.password`) so it is shown inside
 * the app but never committed to the repository.
 */
object TestAccount {
    val email: String get() = BuildConfig.TEST_ACCOUNT_EMAIL
    val password: String get() = BuildConfig.TEST_ACCOUNT_PASSWORD
    val hasPassword: Boolean get() = password.isNotBlank()
}

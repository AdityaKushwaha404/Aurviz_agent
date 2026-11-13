package com.aurviz.agent.presentation.settings

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aurviz.agent.core.security.SecurePreferences
import com.aurviz.agent.data.mail.TestAccount
import com.aurviz.agent.presentation.theme.SuccessGreen
import com.aurviz.agent.presentation.theme.WarningOrange

/**
 * The mailbox chooser. Three options in a fixed order: the test account (with its sign-in details and
 * steps), the user's own Gmail, and the offline demo mailbox. The selected option explains itself.
 */
@Composable
fun MailboxChooser(
    source: String,
    connectionState: String,
    onSelect: (String) -> Unit,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onRetry: () -> Unit,
    onOpenDemoMailbox: () -> Unit
) {
    val connected = connectionState.startsWith("Connected as ")
    val trouble = connectionState.startsWith("Could not reach") || connectionState.contains("expired", ignoreCase = true)

    Text(
        "Choose which mailbox Aurviz reads and writes. You can switch at any time.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    // 1. Test account
    MailOption(
        selected = source == SecurePreferences.SOURCE_TEST,
        title = "Test Gmail account",
        badge = "For reviewers",
        description = "A real Gmail account made for testing. Sign in with the email and password below.",
        onClick = { onSelect(SecurePreferences.SOURCE_TEST) }
    ) {
        CredentialBox()
        Steps(
            listOf(
                "Tap \"Sign in with Google\" below. Your browser opens.",
                "Choose \"Use another account\", paste the email above, tap Next.",
                "Paste the password, tap Next.",
                "If Google says \"Google hasn't verified this app\", tap Advanced, then \"Go to Aurviz (unsafe)\". This is normal for a test app.",
                "Tick all the Gmail permissions and tap Continue. You return to Aurviz automatically.",
                "This card then shows \"Connected as ${TestAccount.email}\". Ask Aurviz anything about the mail."
            )
        )
        Hint("To see the mail itself, open Gmail with the same login. Sending is limited to the test address and example.com addresses, so it is safe to try anything.")
        Hint("If Google asks you to \"verify it's you\" or blocks the sign-in, use the Demo mailbox below.")
        SignInRow(connected, trouble, connectionState, onSignIn, onSignOut, onRetry)
    }

    // 2. Own Gmail
    MailOption(
        selected = source == SecurePreferences.SOURCE_OWN,
        title = "My own Gmail",
        badge = null,
        description = "Use your own account. Aurviz never sees your password.",
        onClick = { onSelect(SecurePreferences.SOURCE_OWN) }
    ) {
        Steps(
            listOf(
                "Tap \"Sign in with Google\" and choose your account.",
                "Approve the Gmail permissions when asked.",
                "Your account must be added as a test user by the project owner, otherwise Google blocks the sign-in. If it is blocked, use the test account or the demo mailbox."
            )
        )
        SignInRow(connected, trouble, connectionState, onSignIn, onSignOut, onRetry)
    }

    // 3. Demo mailbox
    MailOption(
        selected = source == SecurePreferences.SOURCE_DEMO,
        title = "Demo mailbox",
        badge = "Works offline, no sign-in",
        description = "Sample emails stored on this phone. Nothing is ever sent to a real address.",
        onClick = { onSelect(SecurePreferences.SOURCE_DEMO) }
    ) {
        Hint("Try: \"Summarize my emails\", \"Draft an email to Prof. Rao\" or \"Send an email to rahul@example.com\". Then open the demo mailbox to watch Inbox, Sent and Drafts change.")
        Button(onClick = onOpenDemoMailbox, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Inbox, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("View demo mailbox")
        }
    }
}

@Composable
private fun MailOption(
    selected: Boolean,
    title: String,
    badge: String?,
    description: String,
    onClick: () -> Unit,
    expandedContent: @Composable () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                RadioButton(selected = selected, onClick = onClick)
                Spacer(modifier = Modifier.width(4.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        if (badge != null) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                                Text(
                                    badge,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (selected) {
                Spacer(modifier = Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { expandedContent() }
            }
        }
    }
}

@Composable
private fun CredentialBox() {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var showPassword by remember { mutableStateOf(false) }

    fun copy(label: String, value: String) {
        clipboard.setText(AnnotatedString(value))
        Toast.makeText(context, "$label copied", Toast.LENGTH_SHORT).show()
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Sign-in details", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            CredentialRow(
                label = "Email",
                value = TestAccount.email,
                onCopy = { copy("Email", TestAccount.email) }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            if (TestAccount.hasPassword) {
                CredentialRow(
                    label = "Password",
                    value = if (showPassword) TestAccount.password else "•".repeat(TestAccount.password.length.coerceAtLeast(8)),
                    onCopy = { copy("Password", TestAccount.password) },
                    extra = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showPassword) "Hide password" else "Show password"
                            )
                        }
                    }
                )
            } else {
                Text(
                    "Password: ask the project owner (it is not bundled in this build).",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun CredentialRow(label: String, value: String, onCopy: () -> Unit, extra: @Composable () -> Unit = {}) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
        }
        extra()
        IconButton(onClick = onCopy) {
            Icon(Icons.Default.ContentCopy, contentDescription = "Copy $label")
        }
    }
}

@Composable
private fun Steps(steps: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Steps", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        steps.forEachIndexed { index, text ->
            Row(verticalAlignment = Alignment.Top) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Text(
                            "${index + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SignInRow(
    connected: Boolean,
    trouble: Boolean,
    status: String,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onRetry: () -> Unit
) {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = CircleShape,
            color = when {
                connected -> SuccessGreen
                trouble -> WarningOrange
                else -> MaterialTheme.colorScheme.outline
            },
            modifier = Modifier.size(10.dp)
        ) {}
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            if (connected) status else if (trouble) status else "Not signed in",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (connected) {
            OutlinedButton(onClick = onSignOut) { Text("Sign out") }
        } else {
            Button(onClick = onSignIn) { Text("Sign in with Google") }
            if (trouble) OutlinedButton(onClick = onRetry) { Text("Retry") }
        }
    }
}

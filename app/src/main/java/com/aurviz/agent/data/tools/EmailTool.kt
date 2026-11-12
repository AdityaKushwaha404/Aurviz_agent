package com.aurviz.agent.data.tools

import android.util.Base64
import com.aurviz.agent.data.remote.GmailAccessManager
import com.aurviz.agent.domain.model.SensitiveAction
import com.aurviz.agent.domain.model.ToolExecutionResponse
import com.aurviz.agent.domain.tool.AgentTool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

/**
 * Sends mail through Gmail after the app's explicit sensitive-action confirmation.
 * Gmail OAuth access tokens are kept by Google Identity Services and in process memory only.
 */
class EmailTool(
    private val gmailAccessManager: GmailAccessManager,
    private val getDefaultEmail: () -> String
) : AgentTool {
    override val name: String = "send_email"
    override val description: String =
        "Send an email through the connected Gmail account after the user explicitly confirms the recipient, subject, and body."
    override val isSensitive: Boolean = true

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override suspend fun execute(arguments: Map<String, Any?>): ToolExecutionResponse {
        val recipient = (arguments["recipient"] as? String ?: getDefaultEmail()).trim()
        val subject = (arguments["subject"] as? String ?: "").trim()
        val body = arguments["body"] as? String ?: ""

        if (!recipient.isValidEmail()) {
            return failure("A valid recipient email address is required.")
        }
        if (subject.length > MAX_SUBJECT_CHARS || body.length > MAX_BODY_CHARS) {
            return failure("The subject or message is too long. Shorten it and try again.")
        }
        if (gmailAccessManager.accessToken() == null) {
            return failure("Connect Gmail in Settings before preparing email delivery.")
        }

        val sensitiveAction = SensitiveAction.SendEmail(
            description = "Send an email to $recipient with subject '$subject'",
            recipient = recipient,
            subject = subject,
            body = body
        )
        return ToolExecutionResponse(
            toolName = name,
            isSuccess = false,
            output = "Review the recipient, subject, and message, then confirm to send.",
            sensitiveActionRequired = sensitiveAction
        )
    }

    override suspend fun executeConfirmed(
        actionId: String,
        payload: Map<String, Any?>
    ): ToolExecutionResponse {
        val recipient = (payload["recipient"] as? String ?: getDefaultEmail()).trim()
        val subject = payload["subject"] as? String ?: ""
        val body = payload["body"] as? String ?: ""
        if (!recipient.isValidEmail()) return failure("A valid recipient email address is required.")
        if (subject.length > MAX_SUBJECT_CHARS || body.length > MAX_BODY_CHARS) {
            return failure("The subject or message is too long. Shorten it and try again.")
        }

        val token = gmailAccessManager.accessToken()
            ?: return failure("Connect Gmail in Settings before confirming email delivery.")

        return withContext(Dispatchers.IO) {
            try {
                val rawMessage = buildMimeMessage(recipient, subject, body)
                val requestBody = JSONObject().put("raw", rawMessage)
                    .toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                val request = Request.Builder()
                    .url("https://gmail.googleapis.com/gmail/v1/users/me/messages/send")
                    .header("Authorization", "Bearer $token")
                    .post(requestBody)
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    val responseText = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        val detail = runCatching {
                            JSONObject(responseText).getJSONObject("error").getString("message")
                        }.getOrNull()
                        val message = when (response.code) {
                            401, 403 -> "Gmail authorization is missing or expired. Reconnect Gmail in Settings and try again."
                            else -> "Gmail could not send the message (HTTP ${response.code})" +
                                (detail?.let { ": $it" } ?: ".")
                        }
                        failure(message)
                    } else {
                        val messageId = runCatching {
                            JSONObject(responseText).optString("id").takeIf(String::isNotBlank)
                        }.getOrNull() ?: return@use failure(
                            "Gmail returned success without a message ID. Check the Sent folder before retrying."
                        )
                        ToolExecutionResponse(
                            toolName = name,
                            isSuccess = true,
                            output = "Email sent to $recipient (Gmail message ID: $messageId)."
                        )
                    }
                }
            } catch (_: Exception) {
                failure("Gmail delivery failed because of a network or request error. Check your connection and try again.")
            }
        }
    }

    private fun buildMimeMessage(recipient: String, subject: String, body: String): String {
        val encodedSubject = "=?UTF-8?B?" +
            Base64.encodeToString(subject.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP) +
            "?="
        val safeBody = body.replace("\r\n", "\n").replace("\r", "\n")
        val mime = "To: $recipient\r\n" +
            "Subject: $encodedSubject\r\n" +
            "MIME-Version: 1.0\r\n" +
            "Content-Type: text/plain; charset=UTF-8\r\n" +
            "Content-Transfer-Encoding: 8bit\r\n\r\n" +
            safeBody
        return Base64.encodeToString(
            mime.toByteArray(StandardCharsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
    }

    private fun String.isValidEmail(): Boolean =
        length <= 254 && matches(Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))

    private fun failure(message: String) = ToolExecutionResponse(
        toolName = name,
        isSuccess = false,
        output = message
    )

    private companion object {
        const val MAX_SUBJECT_CHARS = 500
        const val MAX_BODY_CHARS = 20_000
    }
}

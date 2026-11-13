package com.aurviz.agent.data.mail

import com.aurviz.agent.domain.mail.MailService
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.TimeUnit

/** One sample message. [daysAgo] keeps the dates fresh whenever the app runs. */
data class DemoEmail(
    val id: String,
    val from: String,
    val to: String,
    val subject: String,
    val daysAgo: Int,
    val body: String,
    val unread: Boolean = false,
    val important: Boolean = false,
    val folder: String = "inbox"
) {
    val snippet: String get() = body.replace(Regex("\\s+"), " ").take(140)
}

/**
 * Built-in sample mailbox for evaluation. Behaves like Gmail for the operations Aurviz
 * uses (search with Gmail-style operators, read, draft, send) but never touches a real
 * account. Sent mail and drafts are kept in memory and can be found with in:sent / in:drafts.
 */
class DemoMailService(
    private val clock: () -> Long = System::currentTimeMillis,
    seed: List<DemoEmail> = SAMPLE_INBOX
) : MailService {

    private val mailbox = seed.toMutableList()
    override val isDemo: Boolean = true

    override fun isConnected() = true

    override suspend fun search(query: String, maxResults: Int): JSONObject {
        val effective = query.ifBlank { "in:inbox" }
        val matches = filter(mailbox, effective).take(maxResults.coerceIn(1, 15))
        return JSONObject()
            .put("query", effective)
            .put("resultSizeEstimate", matches.size)
            .put("messages", JSONArray().apply { matches.forEach { put(summary(it)) } })
    }

    override suspend fun read(messageId: String): JSONObject {
        val email = mailbox.firstOrNull { it.id == messageId }
            ?: throw com.aurviz.agent.data.remote.BackendApiException(404, "That email was not found.")
        return summary(email).put("body", email.body)
    }

    override suspend fun createDraft(recipient: String, subject: String, body: String): JSONObject {
        val draft = outgoing("drafts", recipient, subject, body)
        mailbox += draft
        return JSONObject().put("id", draft.id).put("demo", true)
    }

    override suspend fun send(recipient: String, subject: String, body: String, idempotencyKey: String): JSONObject {
        // Idempotent like the real backend: the same key never records two messages.
        val id = "demo-sent-" + idempotencyKey.filter { it.isLetterOrDigit() }.take(16)
        if (mailbox.none { it.id == id }) mailbox += outgoing("sent", recipient, subject, body).copy(id = id)
        return JSONObject().put("id", id).put("demo", true)
    }

    private fun outgoing(folder: String, recipient: String, subject: String, body: String) = DemoEmail(
        id = "demo-" + UUID.randomUUID().toString().take(8),
        from = "Demo User <demo@aurviz.app>",
        to = recipient,
        subject = subject,
        daysAgo = 0,
        body = body,
        folder = folder
    )

    private fun summary(email: DemoEmail): JSONObject = JSONObject()
        .put("id", email.id)
        .put("from", email.from)
        .put("to", email.to)
        .put("subject", email.subject)
        .put("date", formatDate(email.daysAgo))
        .put("snippet", email.snippet)
        .put("unread", email.unread)

    private fun formatDate(daysAgo: Int): String {
        val format = SimpleDateFormat("EEE, d MMM yyyy", Locale.US).apply { timeZone = TimeZone.getDefault() }
        return format.format(clock() - TimeUnit.DAYS.toMillis(daysAgo.toLong()))
    }

    companion object {
        /** Applies Gmail-style operators: subject:, from:, to:, is:unread/important, in:, newer_than:Nd, plain words. */
        fun filter(all: List<DemoEmail>, query: String): List<DemoEmail> {
            val tokens = tokenize(query)
            var folder = "inbox"
            val predicates = mutableListOf<(DemoEmail) -> Boolean>()
            for (token in tokens) {
                val (key, value) = token.split(":", limit = 2).let {
                    if (it.size == 2 && it[0].lowercase() in OPERATORS) it[0].lowercase() to it[1] else null to token
                }
                val v = value.lowercase()
                when (key) {
                    "in" -> folder = when (v) { "sent" -> "sent"; "drafts", "draft" -> "drafts"; else -> "inbox" }
                    "subject" -> predicates += { it.subject.lowercase().contains(v) }
                    "from" -> predicates += { it.from.lowercase().contains(v) }
                    "to" -> predicates += { it.to.lowercase().contains(v) }
                    "is" -> when (v) {
                        "unread" -> predicates += { it.unread }
                        "important", "starred" -> predicates += { it.important }
                    }
                    "label" -> if (v == "important") predicates += { it.important }
                    "newer_than" -> {
                        val days = Regex("(\\d+)([dwm]?)").matchEntire(v)?.let {
                            val n = it.groupValues[1].toInt()
                            when (it.groupValues[2]) { "w" -> n * 7; "m" -> n * 30; else -> n }
                        }
                        if (days != null) predicates += { it.daysAgo <= days }
                    }
                    else -> if (v.isNotBlank()) predicates += {
                        (it.subject + " " + it.from + " " + it.body).lowercase().contains(v)
                    }
                }
            }
            return all.filter { it.folder == folder && predicates.all { p -> p(it) } }.sortedBy { it.daysAgo }
        }

        private val OPERATORS = setOf("subject", "from", "to", "is", "in", "label", "newer_than")

        /** Splits on spaces but keeps "quoted phrases" and operator:"quoted value" together. */
        fun tokenize(query: String): List<String> {
            val tokens = mutableListOf<String>()
            val current = StringBuilder()
            var quoted = false
            for (ch in query) {
                when {
                    ch == '"' -> quoted = !quoted
                    ch.isWhitespace() && !quoted -> { if (current.isNotEmpty()) tokens += current.toString(); current.clear() }
                    else -> current.append(ch)
                }
            }
            if (current.isNotEmpty()) tokens += current.toString()
            return tokens
        }

        val SAMPLE_INBOX: List<DemoEmail> = listOf(
            DemoEmail(
                "demo-001", "Neha Sharma <neha.sharma@northwindlabs.example>", "Demo User <demo@aurviz.app>",
                "Interview invitation: Software Engineer, Round 2", 1,
                "Hi,\n\nThank you for your time in the first round. We would like to invite you to the second " +
                    "technical interview for the Software Engineer role.\n\nDate: Monday, 10:30 AM IST\n" +
                    "Format: 60-minute video call with two engineers\nTopics: system design and a short coding exercise.\n\n" +
                    "Please confirm your availability by Friday evening.\n\nRegards,\nNeha Sharma\nTalent Acquisition, Northwind Labs",
                unread = true, important = true
            ),
            DemoEmail(
                "demo-002", "Campus Placement Cell <placements@university.example>", "Demo User <demo@aurviz.app>",
                "Placement drive: Aurora Tech on 18 November", 2,
                "Dear students,\n\nAurora Tech will visit campus on 18 November for software developer roles " +
                    "(package 8 to 12 LPA). Eligibility: CGPA 7.0+, no active backlogs.\n\nRegister on the placement " +
                    "portal before 16 November, 5 PM. Carry two copies of your resume.\n\nPlacement Cell",
                unread = true, important = true
            ),
            DemoEmail(
                "demo-003", "Prof. Meera Rao <meera.rao@university.example>", "Demo User <demo@aurviz.app>",
                "Re: Assignment 3 deadline extension", 2,
                "Hello,\n\nI have reviewed your request. You may submit Assignment 3 by 20 November, 11:59 PM. " +
                    "Late submissions after that will carry a penalty. Please include a short note on your design choices.\n\n" +
                    "Regards,\nProf. Meera Rao",
                important = true
            ),
            DemoEmail(
                "demo-004", "HR Team <hr@zephyrsystems.example>", "Demo User <demo@aurviz.app>",
                "Application received: Android Developer", 4,
                "Hello,\n\nWe have received your application for Android Developer. Our team is reviewing it and " +
                    "you will hear from us within 7 working days. No action is needed from you right now.\n\nZephyr Systems HR"
            ),
            DemoEmail(
                "demo-005", "GitHub <notifications@github.example>", "Demo User <demo@aurviz.app>",
                "[aurviz-agent] Pull request #12 merged", 1,
                "Pull request #12 \"Add task history\" was merged into main by a collaborator. 3 files changed, " +
                    "142 additions, 18 deletions. All checks passed."
            ),
            DemoEmail(
                "demo-006", "Nova Bank <alerts@novabank.example>", "Demo User <demo@aurviz.app>",
                "Security alert: new sign-in to your account", 3,
                "We noticed a new sign-in to your Nova Bank account from a new device in Pune. If this was you, " +
                    "no action is needed. If not, reset your password immediately. We will never ask for your PIN by email.",
                unread = true
            ),
            DemoEmail(
                "demo-007", "Priya <priya@mail.example>", "Demo User <demo@aurviz.app>",
                "Weekend plan?", 1,
                "Hey! Are you free on Saturday? Thinking of the 5 PM show followed by dinner. Let me know so I can book seats."
            ),
            DemoEmail(
                "demo-008", "LinkedIn Job Alerts <jobs@linkedin.example>", "Demo User <demo@aurviz.app>",
                "Jobs you may like: Software Developer in Bengaluru", 5,
                "Top picks: 1) Software Developer, Orbit Labs, Bengaluru. 2) Android Engineer, Pixel Forge, Remote. " +
                    "3) Backend Engineer, Lumen Cloud, Hyderabad."
            ),
            DemoEmail(
                "demo-009", "The Weekly Brief <news@weeklybrief.example>", "Demo User <demo@aurviz.app>",
                "This week in tech: on-device AI and new Android releases", 6,
                "Highlights: on-device language models are getting faster, a new Android beta lands, and open-source " +
                    "agent frameworks keep growing. Read more on our site."
            ),
            DemoEmail(
                "demo-010", "CloudHost Billing <billing@cloudhost.example>", "Demo User <demo@aurviz.app>",
                "Your October invoice is ready", 7,
                "Your invoice for October is ready: 412.50 INR. It will be charged to your saved card on 15 November. " +
                    "You can download the PDF from your dashboard."
            )
        )
    }
}

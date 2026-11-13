# Software Requirements Specification

**Product:** Aurviz, a personal AI agent for Android
**Version:** 1.1 (November 2026)
**Status:** Implemented and verified (see [Test Report](TEST_REPORT.md))

---

## 1. Introduction

### 1.1 Purpose
This document specifies what Aurviz must do (functional requirements) and how well it must do it (non-functional requirements). Every requirement has an ID, an acceptance criterion and a test, so that a reviewer can verify it directly.

### 1.2 Scope
Aurviz is an Android application, backed by a small Node.js service, that understands spoken and typed instructions, remembers the user's preferences, plans multi-step work, calls tools (Gmail, memory, scheduler, web browser) and executes tasks, including scheduled tasks that run when the app is closed. It is more than a chat bot: the model decides *what* to do, Kotlin code *does* it, and WorkManager decides *when*.

### 1.3 Definitions
| Term | Meaning |
|---|---|
| Agent | The orchestration loop that asks Gemini for the next step, runs the tool it picks, and feeds the result back |
| Tool | A Kotlin class implementing `AgentTool` that performs one operation (for example `search_emails`) |
| Sensitive tool | A tool with an external side effect (sending mail, clicking in a web page). It always needs explicit user approval |
| Demo mailbox | A built-in sample inbox that lets the app be evaluated without any Google account |
| HITL | Human in the loop: the approval dialog shown before a sensitive action |

### 1.4 Responsibilities (three separate concerns)
| Concern | Owner |
|---|---|
| Reasoning: decide what to do next | Gemini (function calling) |
| Execution: perform the operation | Kotlin tools and the backend |
| Scheduling: run later | Android WorkManager |

Gemini cannot wake the app. A scheduled task is persisted in Room and registered with WorkManager, which later invokes the same agent loop in background mode.

---

## 2. Overall description

### 2.1 Users
- **End user:** a person who wants an assistant for email, reminders and quick web tasks.
- **Evaluator:** a reviewer who must be able to test every feature without receiving anyone's credentials (see [Evaluator Guide](EVALUATOR_GUIDE.md)).

### 2.2 Operating environment
Android 8.0 (API 26) or newer, internet access, a Gemini API key (bundled at build time or entered in Settings). Mail features work with the shared test inbox, the demo mailbox or the user's own Google account.

### 2.3 Constraints
- WorkManager periodic work is durable but not exact to the minute (Doze and battery policy apply).
- Google restricts the `gmail.readonly` scope: accounts must be listed as OAuth test users until the app passes Google verification.
- The controlled browser only opens public HTTPS pages.

### 2.4 Assumptions
The backend is reachable over HTTPS. The Gemini free tier may rate-limit requests; the app handles this (retry, model fallback).

---

## 3. Functional requirements

Priority: **P0** must have, **P1** should have, **P2** nice to have.

### 3.1 Core requirements

| ID | Requirement | Pri | Acceptance criterion | Test |
|---|---|---|---|---|
| FR-01 | **Voice input.** Accept spoken commands and transcribe them with Android speech recognition | P0 | Tapping the microphone, speaking, and the transcribed text appears as a "Voice" message and is processed | Device (earlier build); `VoiceManager` |
| FR-02 | **Text input.** Accept typed commands | P0 | A typed prompt receives an answer | `AgentOrchestratorTest`; device |
| FR-03 | **Intent detection.** Recognise what the user wants and which tool is needed | P0 | "Check my emails" triggers `search_emails`; "draft, don't send" triggers `create_email_draft` and never `send_email` | `AgentReliabilityTest`; device |
| FR-04 | **Tool calling.** Invoke tools with structured arguments and return results to the model | P0 | The timeline shows each tool call with status and result | `AgentOrchestratorTest`, `GeminiAgentClientTest`; device |
| FR-05 | **Persistent memory.** Save facts and preferences that survive restarts | P0 | Save a memory, force-stop the app, reopen, the memory is still listed | Device; `MemoryToolTest` |
| FR-06 | **Memory retrieval.** Use saved memories automatically | P0 | After "I prefer formal emails", a later draft is formal | `AgentReliabilityTest.savedPreference…`; device |
| FR-07 | **Memory edit and delete.** View, edit and delete memories | P0 | Edit and delete work from the Memory screen (with confirmation) and by voice ("forget my…") | `MemoryToolTest`; device |
| FR-08 | **Email sending with confirmation.** Send only after the user approves recipient, subject and body | P0 | An editable approval dialog appears; cancelling sends nothing; approving sends once | `AgentOrchestratorTest.processUserPrompt_sensitiveTool…`; device |
| FR-09 | **Web automation.** Open pages, read, scroll, navigate and extract information | P1 | "Open news.ycombinator.com and tell me the top 3 stories" opens the site and lists stories | `ControlledWebScraperTest`, `BrowserUrlTest`; device |
| FR-10 | **Task scheduling.** Create one-time and daily recurring tasks from natural language | P0 | "Every morning at 9 AM…" creates a task showing "Daily at 09:00" and the next run time | `SchedulerToolTest`, `TimeUtilsTest`; device |
| FR-11 | **Background execution.** Run tasks without the app open | P0 | A task run through WorkManager records a result and posts a notification | `TaskExecutionPolicyTest`; device |
| FR-12 | **Multi-step execution.** Complete several operations in sequence | P0 | "Check emails, find interview invitations and summarize them" runs search, then read, then summary | `AgentReliabilityTest`; device |
| FR-13 | **Task history.** Show previous runs | P0 | The History tab lists each run with status, time, duration and attempt | Device |
| FR-14 | **Error reporting.** Show useful errors and recovery actions | P0 | Failures show a plain message and a "Try again" action for transient errors | `AgentReliabilityTest`, `ExecutionLogFormatterTest`; device |

### 3.2 Extended requirements (beyond the brief)

| ID | Requirement | Pri | Acceptance criterion | Test |
|---|---|---|---|---|
| FR-15 | **Email search and read.** Search a mailbox with Gmail operators and read a message | P0 | `subject:`, `from:`, `is:unread`, `newer_than:` filters work | `DemoMailServiceTest`, `gmail-inbox.test.js` |
| FR-16 | **Email drafts.** Save a draft without sending | P0 | The draft is stored (Gmail Drafts or demo Drafts) and nothing is sent | `GmailInboxToolsTest`; device |
| FR-17 | **Demo mailbox.** Test every email feature without an account | P0 | Turning on Demo mailbox lets search, read, draft and send work on sample data; nothing leaves the device | `DemoMailServiceTest`; device |
| FR-18 | **OAuth Gmail connection.** Connect, check and disconnect a Gmail account via OAuth | P0 | No password is ever entered in the app; disconnect revokes access | Device |
| FR-19 | **Task management.** Pause, resume, run now, edit and delete tasks | P1 | A paused task does not run; "Run now" runs immediately | `TaskExecutionPolicyTest`; device |
| FR-20 | **Readable run results.** Describe task outcomes in plain language with a next step | P1 | A failed run explains the cause and what to do; raw text sits behind "Technical details" | `ExecutionLogFormatterTest` |
| FR-21 | **Browser tools for people.** Address bar with search, find in page, reader view, desktop site, text size, bookmarks, history, share | P1 | Each control works in the in-app browser | `BrowserUrlTest`; device |
| FR-22 | **AI on web pages.** Summarize, extract key facts, explain or ask about the open page | P1 | Tapping Summarize returns to chat with a summary of that page | Device |
| FR-23 | **Theme.** Choose System, Light or Dark | P1 | The choice applies immediately and persists | Device |
| FR-24 | **Onboarding and shortcuts.** First-run walkthrough, launcher shortcuts, share text to Aurviz | P2 | Onboarding appears once; long-pressing the icon lists shortcuts; sharing text opens a summary | Device |
| FR-26 | **Test Gmail account for reviewers.** The app shows the test account's email, password and numbered sign-in steps | P0 | A reviewer follows the steps, signs in on Google's page and sees "Connected as…"; search, read and draft work; mail can only be sent to the account itself or example.com addresses; it cannot be disconnected | `shared-account.test.js`; device |
| FR-27 | **Demo mailbox viewer.** See the demo mailbox's Inbox, Sent and Drafts, read messages, and watch them update | P0 | A draft or sent message saved by the agent appears without leaving the screen; demo can be reset. Real accounts are viewed in Gmail | `MailGatewayTest`; device |
| FR-28 | **Mailbox selection.** Choose Test Gmail account, My own Gmail or Demo mailbox at any time | P0 | The selection persists, the header badge reflects it, and the agent uses the selected mailbox immediately | `MailGatewayTest`; device |
| FR-25 | **Settings and permissions.** Manage key, default recipient, spoken replies, Gmail and system permissions | P1 | All settings persist across restarts | Device |

---

## 4. Non-functional requirements

| ID | Category | Requirement | Measure and result |
|---|---|---|---|
| NFR-01 | Performance | Simple commands respond within about 3 s, excluding external service time | On-device overhead about 0.1 s median; end-to-end median 1.6 s over 30 samples ([Performance](PERFORMANCE.md)) |
| NFR-02 | Security | Protect keys, tokens and accounts | OAuth only (no passwords); refresh tokens AES-256-GCM encrypted on the server; session token and API key in Keystore-backed encrypted storage; excluded from backup; confirmation before send; no secrets in git |
| NFR-03 | Reliability | Failures never look like successes; state survives failures | Failed runs are recorded as failed; transient errors retry with exponential backoff (up to 3); ambiguous email delivery is stored as UNKNOWN and never auto-resent |
| NFR-04 | Accuracy | Choose the correct tool and clarify ambiguity | Draft-versus-send rule; asks for a missing recipient address; never invents email content |
| NFR-05 | Maintainability | Modular, easy to extend | Layered packages; new tool needs a class, a declaration and one registration line ([Architecture](ARCHITECTURE.md)) |
| NFR-06 | Scalability | Support more tools and users without redesign | `AgentTool` and `MailService` abstractions; backend is stateless with a PostgreSQL job queue |
| NFR-07 | Fault tolerance | Handle outages gracefully | Offline check before background runs; retry actions; model fallback chain; backend cold-start-safe timeouts and warm-up |
| NFR-08 | Usability | Intuitive interface | One-tap mic, live "what the agent is doing" status, tool timeline, approval dialogs, onboarding, dark mode |
| NFR-09 | Resource efficiency | Low battery and memory impact | WorkManager only (no persistent service), network constraints, bounded tables and context |
| NFR-10 | Compatibility | Wide device support | minSdk 26, adaptive and themed icon, runtime permissions, tested on Android 16 |
| NFR-12 | Safe shared access | A reviewer-facing account must not be abusable | Server-enforced: sends only to itself or example.com addresses, no disconnect; throwaway account |
| NFR-11 | Privacy | Minimise data exposure | Page and text attachments are sent only when the user triggers an action; demo mode never contacts Gmail |

---

## 5. External interfaces

| Interface | Direction | Purpose |
|---|---|---|
| Gemini REST `generateContent` | App to Google | Reasoning and function calling, with model fallback |
| Aurviz backend REST (HTTPS) | App to server | OAuth session, Gmail search/read/draft/send, scheduled email |
| Gmail API | Server to Google | Mailbox operations with the user's consent |
| Android SpeechRecognizer / TextToSpeech | App to OS | Voice in and out |
| Android WorkManager / Notifications | App to OS | Background execution and result alerts |

Backend endpoints are listed in [API](API.md).

## 6. Data requirements

**On device (Room):** `memories`, `scheduled_tasks`, `task_executions`, `performance_samples`. Chat history is a bounded JSON file (100 messages). Bookmarks and history for the browser are stored locally.
**On the server (PostgreSQL):** `GoogleAccount` (encrypted refresh token), `AppAuthCode`, `DeviceSession` (hashed tokens), `ScheduledEmail`, `EmailExecution`, plus the pg-boss job tables.

## 7. Traceability

See the matrix in [Test Report](TEST_REPORT.md#4-requirement-to-evidence-matrix).

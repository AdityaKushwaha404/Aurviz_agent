# Architecture

## System view

```mermaid
flowchart TD
    Person[Voice or text] --> Compose[Compose UI]
    Compose --> VM[ViewModels]
    VM --> Orchestrator[AgentOrchestrator]
    Orchestrator --> Gemini[Gemini REST function calling]
    Orchestrator --> Registry[ToolRegistry]
    Registry --> MemoryTool[Memory tool]
    Registry --> ScheduleTool[Local scheduler tool]
    Registry --> WebTool[Public page fetcher]
    Registry --> BrowserTools[Agent browser tools]
    Registry --> EmailTool[Foreground Gmail tool]
    MemoryTool --> Room[(Room memories)]
    ScheduleTool --> TaskRepo[Task repository]
    TaskRepo --> WorkManager[Android WorkManager]
    BrowserTools --> BrowserController[BrowserAgentController commands]
    BrowserController --> WebView[Controlled Android WebView]
    WebView --> BrowserController
    TaskRepo --> Tasks[(Room scheduled tasks)]
    Orchestrator --> ChatStore[Bounded JSON conversation store]
    subgraph Separate backend — not yet called by Android
      OAuth[Express OAuth and API]
      OAuth --> Postgres[(PostgreSQL + Prisma)]
      OAuth --> Gmail[Gmail API]
      Postgres --> Queue[pg-boss persistent jobs]
      Queue --> Worker[Backend email worker]
      Worker --> Gmail
    end
```

The application is a single Android module with UI, orchestration, repositories, platform services, and tools separated by package. The backend is a separately deployable Node.js service.

## Interactive agent flow

1. Speech recognition or typed input produces a user prompt.
2. The orchestrator fetches lexical memory matches, falling back to recent memories; it sends up to 12 prior user/assistant text messages as context.
3. Gemini receives system instructions and declared tool functions.
4. Tool calls run through the registry, with a four-step limit. Results and statuses return to the model and appear in chat.
5. Direct Gmail send pauses for explicit approval of recipient, subject, and body. The confirmation gate runs before Gmail API delivery.
6. Agent browser commands route through a shared BrowserAgentController to the visible WebView. Click and text-entry calls require explicit approval. Read and navigation results are returned as tool results.
7. Conversation messages and tool states are persisted locally, bounded to 100 items.

The Gemini REST client preserves raw model function-call parts/signatures and call IDs in follow-up turns so function responses match the request.

## Memory and conversation

Room holds explicit memories and scheduled task data. Memory retrieval is lexical, not vector-based. ConversationStore writes one bounded JSON history file; it survives restarts and can be cleared. It does not yet support multiple threads, message editing, or cross-device sync.

## Voice and local scheduling

SpeechRecognizer converts voice to text; typed and voice requests use the same orchestrator. TextToSpeech speaks responses when enabled. Local tasks use WorkManager for persistent, battery-aware execution. Android may defer these jobs, and periodic work is not an exact local wall-clock cron scheduler.

Scheduled agent runs reject sensitive tools. Background email delivery must go through the separate backend after Android integration provides a confirmation flow.

## Browser control

The BrowserAgentController provides a single active browser session and a command/result channel between tools and the browser screen. Tools include `open_url`, `search_webpage`, `read_page`, `click_element`, `enter_text`, `scroll_page`, `navigate_back`, `navigate_forward`, and `get_browser_state`. The WebView executes narrowly scoped DOM operations using safely encoded arguments, but exposes no JavaScript interface to page scripts. Top-level navigation is restricted to HTTPS and obvious local/private literal destinations are rejected; agent URL opens also resolve DNS and reject private destinations. Loads have a timeout, and the user can see page activity.

Clicks and entering text pause for user review. Entering text does not submit a form; page clicks may cause page effects, so the confirmation screen shows the requested action/selector. This is a controlled WebView, not a full browser security sandbox. Third-party page scripts still run inside WebView; avoid authenticated or financial workflows until stronger domain allowlists and device testing are in place. Browser control compiled but was not exercised on a connected device in the latest test run.

## Gmail and scheduled email backend

Android's existing direct Gmail tool uses Google Identity Services to obtain a short-lived Gmail send token held in process memory. It requires reconnecting after process loss and is not used by WorkManager.

The separate backend uses a Google Web OAuth flow with `openid`, `email`, and `gmail.send`. It validates OAuth state, exchanges the code server-side, encrypts refresh tokens with AES-256-GCM, and returns a short-lived one-time code to Android. The app exchanges that for an opaque server session token. Only hashes of app session/code credentials are stored.

PostgreSQL holds account, session, scheduled email, and execution-history rows. pg-boss persists jobs in PostgreSQL. Each job contains the email ID and version; editing increments the version so a stale queued job cannot send the updated email early. Startup reconciles scheduled rows without an associated job. Retries are bounded for explicit Gmail HTTP 429 quota rejections. Definite other 4xx responses become `FAILED`; 5xx and network results become `UNKNOWN` without automatic resend because Gmail acceptance may be ambiguous. Gmail API plus a separate database cannot guarantee exactly-once side effects.

The backend API/OAuth and worker are implemented but Android does not yet call them. The backend has not been exercised against a live database, Google OAuth account, Gmail send, or restarted worker. Email-sending integration and backend scheduled-email management remain incomplete end-to-end.

## Security and error handling

- Gemini keys are entered in Settings and saved with encrypted preferences. No plaintext fallback is used.
- No Web OAuth secret or refresh token is present in Android source or backend sample configuration. The Web client secret shared in chat must be rotated before use.
- Backend values are read from environment configuration. Its dependency audit reports zero known advisories after dependency updates.
- The backend uses HTTPS in deployment, OAuth CSRF state, one-time codes, hashed device tokens, rate limiting, Helmet, schema validation, bounded request bodies, encrypted refresh tokens, and redacted logs.
- Android WebView has file/content access disabled, mixed content blocked, Safe Browsing enabled, and no native JavaScript bridge.
- Distributed Gemini API keys remain extractable from an APK; production should use an authenticated proxy and quotas.

## Verification and trade-offs

- Android `./gradlew test` and `./gradlew assembleDebug` passed after browser integration.
- Backend `npm test` (5 tests), `npm run prisma:generate`, and `npm audit` passed. The commands ran on Node 20.15, below the declared Node 22.12 minimum; use Node 22.12+ to run the backend.
- Docker and an attached ADB device were unavailable for this run, so database migrations, server startup, browser device interaction, live OAuth, and real scheduled delivery were not tested.
- WorkManager gives durable local execution with flexible timing. The backend supports one-shot emails and durable jobs but not recurring schedule rules yet.
- The separate HTTP fetcher and the controlled browser cover simple extraction and stateful agent interaction; their trust boundaries are different.

# Assignment Submission Readiness

This is a status report, not a claim of guaranteed marks or selection. External OAuth/deployment configuration and integration checks remain.

## Feature status

| Feature | Status | Evidence / limitation |
|---|---|---|
| Android voice and text agent with Gemini function calling | PASS | Existing orchestrator and tool registry; Android JVM tests pass. |
| Persistent personal memory | PASS | Room-backed CRUD UI and lexical context retrieval. |
| Local scheduled tasks | PARTIAL | Room + WorkManager, edit/delete/trigger-now; Android may defer timing and this is not exact cron. |
| Direct Gmail OAuth and send | PASS | Prior user-approved self-test email received successful Gmail API response/message ID. Direct flow is foreground/session-only. |
| Explicit confirmation for direct send | PASS | App displays editable recipient/subject/body confirmation before calling Gmail. |
| Agent-directed WebView control | PARTIAL | Agent tools and shared WebView command/result controller compile; user approval required for click and text entry. Not tested on device in this run; no comprehensive domain allowlist or browser sandbox. |
| Backend Google OAuth and Gmail API implementation | PARTIAL | Express routes, state validation, offline scope, encrypted refresh-token storage, session exchange and disconnect implemented. No live Google OAuth test. |
| Backend scheduled email API/worker | PARTIAL | PostgreSQL/Prisma schema and migration, pg-boss queue, send/list/update/cancel/history APIs, stale-job version check. No PostgreSQL runtime, restart, cancellation race or Gmail delivery integration test. |
| Android-to-backend integration | FAIL / NOT IMPLEMENTED | Android still uses its foreground Google Identity flow; it does not call backend OAuth, email, schedule, or status endpoints. |
| Backend dependency/security checks | PASS | `npm audit` reports zero known vulnerabilities after updates; five unit tests pass. |
| Android unit tests and debug build | PASS | `./gradlew test` and `./gradlew assembleDebug` completed successfully. |
| Backend schema client generation | PASS | `npm run prisma:generate` succeeds. |
| Backend database migration/runtime | NOT TESTED | Docker is unavailable in this environment. |
| On-device browser interaction | NOT TESTED | `adb devices -l` showed no attached device for the current run. |
| Performance study | PARTIAL | Earlier single-device diagnostic measurements are documented in PERFORMANCE.md; they are not a repeated benchmark study. |

## Implemented

- Native Kotlin/Compose voice/text assistant, agent orchestration, memory, direct Gmail confirmation/send, local WorkManager tasks, conversation persistence, and agent-directed browser tool declarations.
- Separate Node.js/Express backend with PostgreSQL/Prisma, OAuth Web client flow, encrypted refresh tokens, device session auth, Gmail send endpoints, durable pg-boss scheduling, execution history, validation, and rate limiting.
- Backend test coverage currently includes token encryption/tamper checks, validation, timezone parsing, and retry classification.
- Architecture, setup, API routes, scheduler behavior, and known delivery semantics are documented in ARCHITECTURE.md and backend/README.md.

## External configuration still required

1. Rotate the Google Web OAuth client secret previously pasted in chat; set its replacement only in backend `.env` or a deployment secret manager.
2. Keep the Google Web OAuth client ID/secret, database URL, session secret, and token-encryption key private to the backend.
3. Enable Gmail API and request `openid`, `email`, and `https://www.googleapis.com/auth/gmail.send`; add the exact HTTPS deployed callback URL to the Web OAuth client.
4. Deploy PostgreSQL and backend over HTTPS; configure DNS, TLS, backups, a secret manager, and at least one worker.
5. Configure an Android-reachable backend base URL and implement/test Android OAuth callback exchange, session storage/refresh, and backend email tools/UI. The phone cannot use the developer computer's localhost.
6. Use Node.js 22.12+; the available test environment was Node 20.15, below the declared runtime requirement.

## Not yet proven

No claim is made for backend live Gmail delivery, refresh-token rotation, PostgreSQL migration execution, job recovery after restart, or exact-once sending. Gmail acceptance and app database commit cannot be atomic; ambiguous delivery is stored as UNKNOWN and requires manual resolution. Recurring backend email scheduling and Android integration remain unfinished.

## Submission communication template

Subject: Aurviz SDE Assignment Submission – [Candidate Name]

Repository: [verified repository link]
Build: `./gradlew app:assembleDebug`
Architecture: ARCHITECTURE.md
Setup/API: backend/README.md
Performance: PERFORMANCE.md

Describe the PASS/PARTIAL items above and do not present unverified backend or device workflows as complete.

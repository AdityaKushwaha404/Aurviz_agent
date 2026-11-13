# Aurviz — Personal AI Agent for Android

Aurviz is a Kotlin/Compose personal agent for voice and text commands, persistent memory, scheduled workflows, Gmail send, and controlled web browsing.

## What it does

- **Voice and text commands** feed one bounded agent loop. Gemini plans and picks tools; Kotlin tools execute; WorkManager schedules.
- **Gmail** (OAuth via the Aurviz backend, no passwords): search and read the inbox, summarize, save drafts, and send only after an editable in-app confirmation.
- **Personal memory** in Room: save, recall, edit and delete from the Memory screen or by voice. Saved preferences (e.g. "formal emails") are applied automatically.
- **Scheduled tasks:** one-time or daily background workflows (e.g. "every morning at 9 AM summarize my important emails"), with pause/resume, run-now, retries with backoff, notifications and a full execution history.
- **Web automation** in a controlled WebView: open, search, read, scroll and navigate; clicks and typing need approval.
- **Reliability and measurement:** real error messages with Retry, offline-aware background runs, and on-device latency stats (median/p95) in Settings.

See [SUBMISSION.md](SUBMISSION.md) for the FR/NFR traceability matrix and a manual demo script.

## Backend

The Node.js/Express + PostgreSQL/Prisma backend ([backend/README.md](backend/README.md)) owns Google OAuth. It encrypts refresh tokens, exchanges Android session tokens, and serves Gmail send/search/read/draft, scheduled one-shot emails and persistent pg-boss jobs. Android calls it at `API_BASE_URL` (see `app/build.gradle.kts`). Never put the Web client secret or refresh tokens in Android or source control.

## Architecture

See [ARCHITECTURE.md](ARCHITECTURE.md) for component boundaries, data flow, browser safety, and delivery semantics. See [SUBMISSION.md](SUBMISSION.md) for an honest status checklist and remaining validation.

## Android setup

Requirements: Android Studio, JDK 17, Android SDK 34, and Android device/emulator API 26+. Add a Gemini API key in Settings. For Gmail, enable the Gmail API, add the `gmail.send`, `gmail.readonly` and `gmail.compose` scopes and your test users on the OAuth consent screen, and configure the backend Web OAuth client (see backend README). Then tap Settings → Connect Gmail. The app stores the Gemini key in AndroidX encrypted preferences; distributed APK keys are still extractable and should be proxied for production.

Build and test:

```sh
./gradlew app:assembleDebug
./gradlew app:testDebugUnitTest
```

## Backend setup

See [backend/README.md](backend/README.md). In short: copy `backend/.env.example` to a private `backend/.env`, set the replacement Google Web OAuth credentials and generated secrets, start PostgreSQL, run npm install, Prisma generate/migrate, then start the API/worker. A physical phone needs a reachable HTTPS backend; its localhost is not your development computer.

## Known limitations

See "Known limits" in [SUBMISSION.md](SUBMISSION.md). In short: WorkManager timing is inexact, `gmail.readonly` requires OAuth test users or Google verification, memory search is lexical, and the WebView is not a hardened sandbox.

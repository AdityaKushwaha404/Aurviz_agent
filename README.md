# Aurviz — Personal AI Agent for Android

Aurviz is a Kotlin/Compose personal agent for voice and text commands, persistent memory, scheduled workflows, Gmail send, and controlled web browsing.

## Android workflows

- Voice and text prompts enter the same bounded agent orchestration flow. Gemini function calls invoke memory, local task scheduling, public web extraction, direct Gmail send, and the controlled browser.
- Memories and scheduled task definitions are stored in Room. Chat history is saved locally with a 100-message bound.
- Android WorkManager supports persistent one-time and periodic workflows. Timing is inexact under Android background restrictions. Tasks can be edited, deleted, or triggered manually.
- The direct Android Gmail path uses Google Identity Services and the send-only `gmail.send` scope. A human confirmation preview is required before sending. An approved self-test email was accepted by Gmail and returned a Gmail message ID in a prior device run.
- Agent browser tools open public HTTPS URLs, search, read visible page text, scroll, navigate history, and request confirmation before clicks or entering text. Browser interactions are executed in the app's WebView without a JavaScript-to-native bridge. The earlier standalone fetch tool remains available for simple public pages.
- The app's existing direct Gmail authorization is foreground-only. It is not used for backend scheduled delivery.

## Separate backend

A Node.js/Express + PostgreSQL/Prisma backend is in [backend/README.md](backend/README.md). It implements web OAuth code flow, encrypted Google refresh-token storage, Android session-token exchange, Gmail send/schedule/list/update/cancel endpoints, and persistent pg-boss jobs. The Android app is not yet connected to these endpoints, and live backend OAuth/scheduling has not been integration-tested. See the backend README for required deployment and Google Cloud values.

The backend requires Node.js 22.12+, PostgreSQL, and a rotated Google Web OAuth client secret provided through a private environment file or secret manager. Never put the Web client secret or refresh token in Android or source control.

## Architecture

See [ARCHITECTURE.md](ARCHITECTURE.md) for component boundaries, data flow, browser safety, and delivery semantics. See [SUBMISSION.md](SUBMISSION.md) for an honest status checklist and remaining validation.

## Android setup

Requirements: Android Studio, JDK 17, Android SDK 34, and Android device/emulator API 26+. Add a Gemini API key in Settings. For the direct Gmail flow, enable Gmail API, configure the OAuth consent screen/test users and Android OAuth client matching package/signing SHA-1. Debug package: `com.aurviz.agent.debug`; debug SHA-1: `28:B0:E2:1E:76:4E:98:04:2C:D4:D7:8F:05:B2:7C:86:CA:5D:40:35`. Configure the release signing certificate separately. The app stores the Gemini key in AndroidX encrypted preferences; distributed APK keys are still extractable and should be proxied for production.

Build and test:

```sh
./gradlew app:assembleDebug
./gradlew app:testDebugUnitTest
```

## Backend setup

See [backend/README.md](backend/README.md). In short: copy `backend/.env.example` to a private `backend/.env`, set the replacement Google Web OAuth credentials and generated secrets, start PostgreSQL, run npm install, Prisma generate/migrate, then start the API/worker. A physical phone needs a reachable HTTPS backend; its localhost is not your development computer.

## Remaining limitations

- The Android application does not yet call the new backend APIs, expose backend OAuth connection in Settings, or offer backend scheduled-email management through the agent. Direct foreground Gmail sending remains separate.
- Backend OAuth, Gmail send, token refresh, queue recovery, and schedule mutation have only unit/schema validation; they require a live PostgreSQL and Google configuration to verify.
- WebView agent operations compile and are wired through the orchestrator, but were not exercised on a connected phone in the latest run. Review selectors and page effects carefully before approval.
- WorkManager is durable but not an exact cron service. The backend currently schedules one-shot emails, not recurring email workflows.
- No performance study is claimed; see [PERFORMANCE.md](PERFORMANCE.md).
